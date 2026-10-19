package de.flashcards.open.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.io.TestDataSeeder
import de.flashcards.open.data.repo.FlashcardRepository
import de.flashcards.open.data.repo.SettingsRepository
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.csv.CsvCard
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.domain.scheduler.Sm2Scheduler
import de.flashcards.open.domain.study.MixMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repo: FlashcardRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repo = FlashcardRepository(db, SettingsRepository(ctx), Sm2Scheduler, scope)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private suspend fun loadedOverview() = withTimeout(10_000) { repo.overview.first { it.loaded && it.decks.isNotEmpty() } }

    @Test fun seed10x20x100LoadsFast() = runBlocking {
        TestDataSeeder.seed(db)
        assertEquals(10, db.topicDao().all().size)
        assertEquals(200, db.deckDao().all().size)

        // warm up first, then measure: the home list (all counters) and one topic
        db.cardDao().observeDeckCounts(Long.MAX_VALUE).first()
        val tStart = measureTimeMillis {
            db.topicDao().all()
            db.deckDao().all()
            val counts = db.cardDao().observeDeckCounts(repo.today().end).first()
            assertEquals(200, counts.size)
            assertEquals(20_000, counts.sumOf { it.total })
        }
        val topicId = db.topicDao().all().first().id
        val tTopic = measureTimeMillis { assertEquals(20, db.deckDao().byTopic(topicId).size) }
        println("home list: $tStart ms, topic: $tTopic ms")
        assertTrue("home list too slow: $tStart ms", tStart < 300)
        assertTrue("topic too slow: $tTopic ms", tTopic < 300)

        val ov = loadedOverview()
        assertEquals(10, ov.topics.size)
        // new is capped at the daily limit (20)
        assertEquals(20, ov.deckCounts.values.first().new)
        assertEquals(20 * 200, ov.countsOf(ov.decks.map { it.id }).new)
    }

    @Test fun comboQueue20DecksFrom10Topics() = runBlocking {
        TestDataSeeder.seed(db)
        val topics = db.topicDao().all()
        val chosen = topics.flatMap { t -> db.deckDao().byTopic(t.id).take(2) }.map { it.id }
        assertEquals(20, chosen.size)

        // make a few cards due
        val firstDeckCards = db.cardDao().byDeck(chosen[0]).take(5)
        firstDeckCards.forEach { db.cardDao().update(it.copy(state = CardState.REVIEW, dueAt = 1, intervalDays = 3)) }

        lateinit var queue: List<de.flashcards.open.domain.study.QueueCard>
        val t = measureTimeMillis { queue = repo.buildQueue(chosen, MixMode.MIXED, null, Random(1)) }
        println("queue across 20 decks: $t ms, ${queue.size} cards")
        assertTrue("start too slow: $t ms", t < 300)
        assertEquals(5 + 20 * 20, queue.size) // 5 due + 20 new per deck
        assertEquals(20, queue.map { it.deckId }.toSet().size)
        assertEquals(10, chosen.map { id -> db.deckDao().get(id)!!.topicId }.toSet().size)

        // session cap
        val limited = repo.buildQueue(chosen, MixMode.BY_DECK, 30)
        assertEquals(5 + 30, limited.size)
    }

    @Test fun newLimitCountsAnsweredCardsPerDay() = runBlocking {
        val topic = repo.createTopic("T", 0, null)
        val r = repo.importCards(topic, "D", null, (1..30).map { CsvCard("Q$it", "A$it", "") }, null)
        assertEquals(20, repo.buildQueue(listOf(r.deckId), MixMode.MIXED, null).size)
        val card = repo.cardsOfDeck(r.deckId).first()
        repo.answer(card, Rating.EASY)
        // 19 new left, the answered card is now a review (due in 4 days)
        assertEquals(19, repo.buildQueue(listOf(r.deckId), MixMode.MIXED, null).size)
        repo.setDeckNewPerDay(r.deckId, 3)
        assertEquals(2, repo.buildQueue(listOf(r.deckId), MixMode.MIXED, null).size)
    }

    @Test fun reimportKeepsProgress() = runBlocking {
        val topic = repo.createTopic("Networking", 0, null)
        val first = repo.importCards(topic, "OSI", "OSI.csv", listOf(CsvCard("A", "a", ""), CsvCard("B", "b", ""), CsvCard("C", "c", "")), null)
        assertEquals(3, first.added)
        val b = repo.cardsOfDeck(first.deckId).first { it.frontMd == "B" }
        val answered = repo.answer(repo.answer(b, Rating.GOOD), Rating.GOOD)
        assertEquals(CardState.REVIEW, answered.state)

        val diff = repo.previewDiff(first.deckId, listOf(CsvCard("B", "b new", "x"), CsvCard("D", "d", "")))
        assertEquals(1, diff.added); assertEquals(1, diff.changed); assertEquals(2, diff.removed)

        val second = repo.importCards(topic, "OSI", "OSI.csv", listOf(CsvCard("B", "b new", "x"), CsvCard("D", "d", "")), first.deckId)
        assertEquals(first.deckId, second.deckId)
        val cards = repo.cardsOfDeck(first.deckId)
        assertEquals(listOf("B", "D"), cards.map { it.frontMd })
        val b2 = cards.first { it.frontMd == "B" }
        assertEquals(b.id, b2.id)
        assertEquals("b new", b2.backMd)
        assertEquals(CardState.REVIEW, b2.state) // progress kept
        assertEquals(answered.intervalDays, b2.intervalDays)
    }

    @Test fun importStoresCardTypesAndReimportUpdatesThemKeepingProgress() = runBlocking {
        val topic = repo.createTopic("Types", 0, null)
        val first = repo.importCards(
            topic, "All", "all.csv",
            listOf(
                CsvCard("Gap {{ohm}}", "unit", "", CardType.CLOZE),
                CsvCard("Capital?", "Paris", "", CardType.INPUT, "Paris, France"),
                CsvCard("Layer 3?", "Router", "", CardType.CHOICE, "Switch|Hub"),
            ),
            null,
        )
        val stored = repo.cardsOfDeck(first.deckId)
        assertEquals(listOf(CardType.CLOZE, CardType.INPUT, CardType.CHOICE), stored.map { it.type })
        assertEquals("Switch|Hub", stored.first { it.type == CardType.CHOICE }.options)

        // build progress on the choice card, then change its type through a re-import
        val choice = stored.first { it.type == CardType.CHOICE }
        val answered = repo.answer(repo.answer(choice, Rating.GOOD), Rating.GOOD)
        assertEquals(CardState.REVIEW, answered.state)

        val changed = listOf(
            CsvCard("Gap {{ohm}}", "unit", "", CardType.CLOZE),
            CsvCard("Capital?", "Paris", "", CardType.INPUT, "Paris, France"),
            CsvCard("Layer 3?", "Router", "", CardType.INPUT, ""),
        )
        assertEquals(1, repo.previewDiff(first.deckId, changed).changed)
        repo.importCards(topic, "All", "all.csv", changed, first.deckId)

        val after = repo.cardsOfDeck(first.deckId).first { it.id == choice.id }
        assertEquals(CardType.INPUT, after.type)
        assertEquals("", after.options)
        assertEquals(CardState.REVIEW, after.state) // progress kept
        assertEquals(answered.intervalDays, after.intervalDays)
    }

    @Test fun deletingDeckCascadesAndLeavesPreset() = runBlocking {
        val topic = repo.createTopic("T", 0, null)
        val d1 = repo.importCards(topic, "D1", null, listOf(CsvCard("Q", "A", "")), null).deckId
        val d2 = repo.importCards(topic, "D2", null, listOf(CsvCard("Q", "A", "")), null).deckId
        val card = repo.cardsOfDeck(d1).single()
        repo.answer(card, Rating.GOOD)
        val preset = repo.savePreset("Klausur", listOf(d1, d2), MixMode.BY_DECK, 10)

        repo.deleteDecks(listOf(d1))
        assertNull(repo.card(card.id))
        assertEquals(0, db.reviewLogDao().count())
        assertEquals(listOf(d2), repo.presetDeckIds(preset))

        repo.deleteTopic(db.topicDao().get(topic)!!)
        assertEquals(0, db.deckDao().all().size)
        assertEquals(0, db.cardDao().all().size)
        assertEquals(emptyList<Long>(), repo.presetDeckIds(preset))
    }

    @Test fun moveDecksBetweenTopicsAndSort() = runBlocking {
        val a = repo.createTopic("A", 0, null)
        val b = repo.createTopic("B", 0, "🧪")
        val ids = (1..3).map { repo.createDeck(a, "D$it") }
        repo.moveDeck(ids[2], -2)
        assertEquals(listOf("D3", "D1", "D2"), repo.decksOfTopic(a).map { it.name })
        repo.moveDecksToTopic(listOf(ids[0], ids[1]), b)
        assertEquals(listOf("D3"), repo.decksOfTopic(a).map { it.name })
        assertEquals(setOf("D1", "D2"), repo.decksOfTopic(b).map { it.name }.toSet())
        repo.moveTopic(b, -1)
        assertEquals(listOf("B", "A"), repo.allTopics().map { it.name })
    }

    @Test fun cramByTagAndReset() = runBlocking {
        val topic = repo.createTopic("T", 0, null)
        val d = repo.importCards(topic, "D", null, listOf(CsvCard("1", "a", "netz linux"), CsvCard("2", "b", "netz,web"), CsvCard("3", "c", "web")), null).deckId
        assertEquals(2, repo.cramQueue(listOf(d), "netz").size)
        assertEquals(1, repo.cramQueue(listOf(d), "linux").size)
        assertEquals(3, repo.cramQueue(listOf(d), null).size)
        assertEquals(listOf("linux", "netz", "web"), repo.tags(listOf(d)))

        repo.cardsOfDeck(d).forEach { repo.answer(it, Rating.EASY) }
        assertTrue(repo.cardsOfDeck(d).all { it.state == CardState.REVIEW })
        repo.resetProgress(listOf(d))
        assertTrue(repo.cardsOfDeck(d).all { it.state == CardState.NEW && it.reps == 0 })
    }
}

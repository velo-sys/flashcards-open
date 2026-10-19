package de.flashcards.open.domain

import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.domain.study.MixMode
import de.flashcards.open.domain.study.QueueCard
import de.flashcards.open.domain.study.StudyQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StudyQueueTest {
    private var nextId = 1L
    private fun cards(deck: Long, state: CardState, n: Int, due: Long = 0) =
        (0 until n).map { QueueCard(nextId++, deck, state, due + it, it) }

    @Test fun learnFirstThenReviewsAndNewInterleaved() {
        val learn = cards(1, CardState.LEARN, 2, due = 100).reversed()
        val review = cards(1, CardState.REVIEW, 4)
        val new = cards(1, CardState.NEW, 2)
        val q = StudyQueue.build(learn, review, new, listOf(1), mapOf(1L to 20), null, MixMode.MIXED, Random(1))
        assertEquals(8, q.size)
        assertEquals(listOf(100L, 101L), q.take(2).map { it.dueAt }) // sorted by due date
        assertEquals(
            listOf(CardState.REVIEW, CardState.REVIEW, CardState.NEW, CardState.REVIEW, CardState.REVIEW, CardState.NEW),
            q.drop(2).map { it.state },
        )
    }

    @Test fun newLimitPerDeckAndOrder() {
        val new = cards(1, CardState.NEW, 10).shuffled(Random(3)) + cards(2, CardState.NEW, 10)
        val q = StudyQueue.build(emptyList(), emptyList(), new, listOf(1, 2), mapOf(1L to 3, 2L to 0), null, MixMode.MIXED)
        assertEquals(3, q.size)
        assertTrue(q.all { it.deckId == 1L })
        assertEquals(listOf(0, 1, 2), q.map { it.orderIndex })
    }

    @Test fun sessionLimitTakesRoundRobinAcrossDecks() {
        val new = (1L..5L).flatMap { cards(it, CardState.NEW, 10) }
        val q = StudyQueue.build(emptyList(), emptyList(), new, (1L..5L).toList(), (1L..5L).associateWith { 20 }, 7, MixMode.MIXED)
        assertEquals(7, q.size)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), q.map { it.deckId }.toSet())
    }

    @Test fun byDeckModeIsBlockwise() {
        val review = cards(1, CardState.REVIEW, 3) + cards(2, CardState.REVIEW, 3) + cards(3, CardState.REVIEW, 3)
        val new = cards(2, CardState.NEW, 2) + cards(3, CardState.NEW, 1)
        val q = StudyQueue.build(emptyList(), review.shuffled(Random(7)), new, listOf(3, 1, 2), mapOf(1L to 20, 2L to 20, 3L to 20), null, MixMode.BY_DECK)
        assertEquals(listOf(3L, 3L, 3L, 3L, 1L, 1L, 1L, 2L, 2L, 2L, 2L, 2L), q.map { it.deckId })
    }

    @Test fun mixedModeMixesDecks() {
        val review = (1L..4L).flatMap { cards(it, CardState.REVIEW, 25) }
        val q = StudyQueue.build(emptyList(), review, emptyList(), (1L..4L).toList(), emptyMap(), null, MixMode.MIXED, Random(11))
        val firstTen = q.take(10).map { it.deckId }.toSet()
        assertTrue("expected several decks among the first 10 cards: $firstTen", firstTen.size > 1)
    }

    @Test fun mixedNewCardsAlternateDecks() {
        val new = cards(1, CardState.NEW, 3) + cards(2, CardState.NEW, 3)
        val q = StudyQueue.build(emptyList(), emptyList(), new, listOf(1, 2), mapOf(1L to 9, 2L to 9), null, MixMode.MIXED)
        assertEquals(listOf(1L, 2L, 1L, 2L, 1L, 2L), q.map { it.deckId })
    }

    @Test fun requeuePositions() {
        assertEquals(4, StudyQueue.requeuePosition(0, 50, Rating.AGAIN))
        assertEquals(7, StudyQueue.requeuePosition(0, 50, Rating.GOOD))
        assertEquals(3, StudyQueue.requeuePosition(2, 3, Rating.AGAIN)) // to the end when shorter
        assertEquals(4, StudyQueue.cramRequeuePosition(0, 10))
    }
}

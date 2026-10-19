package de.flashcards.open.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.db.Card
import de.flashcards.open.data.db.Counts
import de.flashcards.open.data.db.Deck
import de.flashcards.open.data.db.ReviewLog
import de.flashcards.open.data.db.StudyPreset
import de.flashcards.open.data.db.Topic
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.csv.ContentKey
import de.flashcards.open.domain.csv.CsvCard
import de.flashcards.open.domain.csv.ExistingCardRef
import de.flashcards.open.domain.csv.ImportDiff
import de.flashcards.open.domain.csv.ImportDiffer
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.DayBounds
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.domain.scheduler.Scheduler
import de.flashcards.open.domain.study.MixMode
import de.flashcards.open.domain.study.QueueCard
import de.flashcards.open.domain.study.StudyQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.random.Random

/** Day boundaries; they move at midnight. */
data class Day(val start: Long, val end: Long)

data class PresetInfo(val preset: StudyPreset, val deckIds: List<Long>, val counts: Counts)

/** Overview for the list screens: everything aggregated in SQL, nothing filtered in memory. */
data class Overview(
    val topics: List<Topic> = emptyList(),
    val decks: List<Deck> = emptyList(),
    /** Counters per deck; “new” is already capped at today's limit */
    val deckCounts: Map<Long, Counts> = emptyMap(),
    /** cards never studied, per deck (uncapped) */
    val unseen: Map<Long, Int> = emptyMap(),
    val presets: List<PresetInfo> = emptyList(),
    val loaded: Boolean = false,
) {
    val decksByTopic: Map<Long, List<Deck>> by lazy { decks.groupBy { it.topicId } }
    val topicById: Map<Long, Topic> by lazy { topics.associateBy { it.id } }
    val deckById: Map<Long, Deck> by lazy { decks.associateBy { it.id } }

    fun countsOf(deckIds: Collection<Long>): Counts =
        deckIds.fold(Counts()) { acc, id -> acc + (deckCounts[id] ?: Counts()) }

    fun topicCounts(topicId: Long): Counts = countsOf(decksByTopic[topicId].orEmpty().map { it.id })
}

data class ImportResult(val deckId: Long, val added: Int, val changed: Int, val removed: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class FlashcardRepository(
    private val db: AppDatabase,
    private val settingsRepo: SettingsRepository,
    private val scheduler: Scheduler,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val topics = db.topicDao()
    private val decks = db.deckDao()
    private val cards = db.cardDao()
    private val logs = db.reviewLogDao()
    private val presets = db.presetDao()

    fun today(): Day {
        val now = clock()
        return Day(DayBounds.startOfDay(now), DayBounds.startOfTomorrow(now))
    }

    private val dayFlow: Flow<Day> = flow {
        while (true) {
            emit(today()); delay(60_000)
        }
    }.distinctUntilChanged()

    val overview: StateFlow<Overview> = dayFlow.flatMapLatest { day ->
        val base = combine(
            topics.observeAll(),
            decks.observeAll(),
            cards.observeDeckCounts(day.end),
            logs.observeNewToday(day.start),
            settingsRepo.settings,
        ) { t, d, counts, newToday, settings ->
            val countMap = counts.associateBy { it.deckId }
            val newMap = newToday.associate { it.deckId to it.n }
            val adjusted = HashMap<Long, Counts>(d.size)
            val unseen = HashMap<Long, Int>(d.size)
            for (deck in d) {
                val c = countMap[deck.id]
                val limit = deck.newPerDay ?: settings.newPerDay
                val left = (limit - (newMap[deck.id] ?: 0)).coerceAtLeast(0)
                adjusted[deck.id] = Counts(
                    new = minOf(c?.newCount ?: 0, left),
                    learn = c?.learnCount ?: 0,
                    due = c?.dueCount ?: 0,
                    total = c?.total ?: 0,
                )
                unseen[deck.id] = c?.newCount ?: 0
            }
            Overview(t, d, adjusted, unseen, loaded = true)
        }
        combine(base, presets.observeAll(), presets.observeLinks()) { ov, ps, links ->
            val byPreset = links.groupBy({ it.presetId }, { it.deckId })
            ov.copy(
                presets = ps.map { p ->
                    val ids = byPreset[p.id].orEmpty()
                    var c = ov.countsOf(ids)
                    p.newLimit?.let { c = c.copy(new = minOf(c.new, it)) }
                    PresetInfo(p, ids, c)
                },
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), Overview())

    // ---------------- Topics ----------------
    suspend fun createTopic(name: String, colorArgb: Int, emoji: String?): Long =
        topics.insert(Topic(name = name.trim(), colorArgb = colorArgb, emoji = emoji?.ifBlank { null }, sortIndex = topics.nextSortIndex()))

    suspend fun updateTopic(t: Topic) = topics.update(t.copy(name = t.name.trim(), emoji = t.emoji?.ifBlank { null }))

    suspend fun deleteTopic(t: Topic) = topics.delete(t)

    /** Moves a topic by [delta] positions within the manual sort order. */
    suspend fun moveTopic(id: Long, delta: Int) = db.withTransaction {
        val list = topics.all().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return@withTransaction
        list.add(j, list.removeAt(i))
        topics.updateAll(list.mapIndexed { idx, t -> t.copy(sortIndex = idx) })
    }

    suspend fun getOrCreateTopic(name: String, colorArgb: Int): Topic =
        topics.byName(name.trim()) ?: run {
            val id = createTopic(name, colorArgb, null)
            topics.get(id)!!
        }

    // ---------------- Decks ----------------
    suspend fun createDeck(topicId: Long, name: String, sourceFileName: String? = null): Long =
        decks.insert(Deck(topicId = topicId, name = name.trim(), sourceFileName = sourceFileName, sortIndex = decks.nextSortIndex(topicId)))

    suspend fun renameDeck(id: Long, name: String) {
        val d = decks.get(id) ?: return
        decks.update(d.copy(name = name.trim(), updatedAt = clock()))
    }

    suspend fun setDeckNewPerDay(id: Long, v: Int?) {
        val d = decks.get(id) ?: return
        decks.update(d.copy(newPerDay = v?.coerceIn(0, 9999)))
    }

    suspend fun moveDecksToTopic(ids: List<Long>, topicId: Long) = db.withTransaction {
        var idx = decks.nextSortIndex(topicId)
        decks.updateAll(decks.byIds(ids).map { it.copy(topicId = topicId, sortIndex = idx++, updatedAt = clock()) })
    }

    suspend fun deleteDecks(ids: List<Long>) = decks.deleteByIds(ids)

    suspend fun moveDeck(id: Long, delta: Int) = db.withTransaction {
        val d = decks.get(id) ?: return@withTransaction
        val list = decks.byTopic(d.topicId).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i == j) return@withTransaction
        list.add(j, list.removeAt(i))
        decks.updateAll(list.mapIndexed { idx, x -> x.copy(sortIndex = idx) })
    }

    fun observeDeck(id: Long) = decks.observe(id)
    fun observeTopic(id: Long) = topics.observe(id)
    suspend fun allTopics() = topics.all()
    suspend fun deck(id: Long) = decks.get(id)
    suspend fun topic(id: Long) = topics.get(id)
    suspend fun decksOfTopic(topicId: Long) = decks.byTopic(topicId)
    suspend fun deckByName(topicId: Long, name: String) = decks.byName(topicId, name)

    // ---------------- Cards ----------------
    fun pagedCards(deckId: Long, query: String): Flow<PagingData<Card>> = Pager(
        PagingConfig(pageSize = 50, enablePlaceholders = false),
    ) { if (query.isBlank()) cards.pagingByDeck(deckId) else cards.pagingSearch(deckId, query.trim()) }.flow

    suspend fun card(id: Long) = cards.get(id)
    suspend fun cardsOfDeck(deckId: Long) = cards.byDeck(deckId)

    suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
        tags: String,
        type: CardType = CardType.BASIC,
        options: String = "",
    ): Long = db.withTransaction {
        val existing = cards.refsByDeck(deckId).map { it.contentKey }.toSet()
        var key = ContentKey.of(front)
        var n = 2
        while (key in existing) key = ContentKey.of(front) + "#" + n++
        cards.insert(
            Card(
                deckId = deckId, frontMd = front.trim(), backMd = back.trim(), tags = tags.trim(),
                orderIndex = cards.nextOrderIndex(deckId), contentKey = key, type = type, options = options.trim(),
            ),
        )
    }

    suspend fun updateCardContent(
        c: Card,
        front: String,
        back: String,
        tags: String,
        type: CardType = c.type,
        options: String = c.options,
    ) = cards.update(
        c.copy(
            frontMd = front.trim(), backMd = back.trim(), tags = tags.trim(),
            contentKey = ContentKey.of(front), type = type, options = options.trim(),
        ),
    )

    suspend fun deleteCard(id: Long) = cards.delete(id)

    suspend fun resetProgress(deckIds: List<Long>) = cards.resetProgress(deckIds)

    suspend fun tags(deckIds: List<Long>): List<String> =
        cards.tagStrings(deckIds).flatMap { s -> s.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() } }
            .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    // ---------------- Import ----------------
    suspend fun previewDiff(deckId: Long?, incoming: List<CsvCard>): ImportDiff {
        val existing = deckId?.let { id ->
            cards.refsByDeck(id).map { ExistingCardRef(it.id, it.contentKey, it.back, it.tags, it.type, it.options) }
        }.orEmpty()
        return ImportDiffer.diff(existing, incoming)
    }

    /**
     * Creates a deck or updates it (cards matched through contentKey).
     * Existing cards keep their study progress.
     */
    suspend fun importCards(topicId: Long, deckName: String, sourceFileName: String?, incoming: List<CsvCard>, existingDeckId: Long?): ImportResult =
        db.withTransaction {
            val deckId = existingDeckId ?: createDeck(topicId, deckName, sourceFileName)
            val diff = previewDiff(existingDeckId, incoming)
            if (diff.toDelete.isNotEmpty()) diff.toDelete.chunked(500).forEach { cards.deleteByIds(it) }
            for ((id, inc) in diff.toUpdate) {
                cards.updateContent(id, inc.card.front, inc.card.back, inc.card.tags, inc.orderIndex, inc.card.type, inc.card.options)
            }
            cards.insertAll(
                diff.toInsert.map {
                    Card(
                        deckId = deckId, frontMd = it.card.front, backMd = it.card.back, tags = it.card.tags,
                        orderIndex = it.orderIndex, contentKey = it.contentKey, type = it.card.type, options = it.card.options,
                    )
                },
            )
            decks.get(deckId)?.let { decks.update(it.copy(updatedAt = clock(), sourceFileName = sourceFileName ?: it.sourceFileName)) }
            ImportResult(deckId, diff.added, diff.changed, diff.removed)
        }

    // ---------------- Studying ----------------
    /** Builds the queue in SQL (deckId IN …, dueAt index). */
    suspend fun buildQueue(deckIds: List<Long>, mode: MixMode, sessionNewLimit: Int?, random: Random = Random.Default): List<QueueCard> {
        if (deckIds.isEmpty()) return emptyList()
        val day = today()
        val settings = settingsRepo.current()
        val deckList = decks.byIds(deckIds)
        val topicOrder = topics.all().withIndex().associate { it.value.id to it.index }
        val ordered = deckList.sortedWith(compareBy({ topicOrder[it.topicId] ?: 0 }, { it.sortIndex }, { it.name }))
        val newToday = logs.newToday(day.start).associate { it.deckId to it.n }
        val remaining = ordered.associate { d -> d.id to ((d.newPerDay ?: settings.newPerDay) - (newToday[d.id] ?: 0)).coerceAtLeast(0) }
        val chunks = deckIds.chunked(900)
        val learn = chunks.flatMap { cards.queueLearn(it, day.end) }
        val review = chunks.flatMap { cards.queueReview(it, day.end) }
        val new = ordered.flatMap { d -> remaining[d.id]!!.takeIf { it > 0 }?.let { cards.queueNew(d.id, it) }.orEmpty() }
        return StudyQueue.build(learn, review, new, ordered.map { it.id }, remaining, sessionNewLimit, mode, random)
    }

    /** Cram mode: every card (optionally filtered by tag), shuffled. */
    suspend fun cramQueue(deckIds: List<Long>, tag: String?): List<QueueCard> {
        val chunks = deckIds.chunked(900)
        val all = if (tag.isNullOrBlank()) chunks.flatMap { cards.queueAll(it) } else chunks.flatMap { cards.queueByTag(it, tag) }
        return all.shuffled()
    }

    suspend fun answer(card: Card, rating: Rating): Card = db.withTransaction {
        val now = clock()
        val updated = card.withSrs(scheduler.schedule(card.srs, rating, now))
        cards.update(updated)
        logs.insert(ReviewLog(cardId = card.id, reviewedAt = now, rating = rating.value, prevState = card.state, newIntervalDays = updated.intervalDays))
        updated
    }

    fun isLearning(c: Card) = c.state == CardState.LEARN || c.state == CardState.RELEARN

    // ---------------- Presets ----------------
    suspend fun savePreset(name: String, deckIds: List<Long>, mode: MixMode, newLimit: Int?): Long = db.withTransaction {
        val id = presets.insert(StudyPreset(name = name.trim(), shuffleMode = mode, newLimit = newLimit))
        presets.replaceLinks(id, deckIds)
        id
    }

    suspend fun updatePreset(p: StudyPreset, deckIds: List<Long>) = db.withTransaction {
        presets.update(p.copy(name = p.name.trim()))
        presets.replaceLinks(p.id, deckIds)
    }

    suspend fun renamePreset(id: Long, name: String) {
        presets.get(id)?.let { presets.update(it.copy(name = name.trim())) }
    }

    suspend fun deletePreset(id: Long) = presets.delete(id)
    suspend fun preset(id: Long) = presets.get(id)
    suspend fun presetDeckIds(id: Long) = presets.deckIds(id)

    // ---------------- Statistics ----------------
    suspend fun reviewTimesSince(since: Long) = logs.reviewTimesSince(since)
    suspend fun dueTimesUntil(until: Long) = cards.dueTimesUntil(until)
    suspend fun stateCounts() = cards.stateCounts()
}

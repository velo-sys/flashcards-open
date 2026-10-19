package de.flashcards.open.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.study.QueueCard
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Query("SELECT * FROM topic ORDER BY sortIndex, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Topic>>

    @Query("SELECT * FROM topic ORDER BY sortIndex, name COLLATE NOCASE")
    suspend fun all(): List<Topic>

    @Query("SELECT * FROM topic WHERE id = :id")
    fun observe(id: Long): Flow<Topic?>

    @Query("SELECT * FROM topic WHERE id = :id")
    suspend fun get(id: Long): Topic?

    @Query("SELECT * FROM topic WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Topic?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM topic")
    suspend fun nextSortIndex(): Int

    @Insert
    suspend fun insert(t: Topic): Long

    @Insert
    suspend fun insertAll(t: List<Topic>)

    @Update
    suspend fun update(t: Topic)

    @Update
    suspend fun updateAll(t: List<Topic>)

    @Delete
    suspend fun delete(t: Topic)

    @Query("DELETE FROM topic")
    suspend fun deleteAll()
}

@Dao
interface DeckDao {
    @Query("SELECT * FROM deck ORDER BY sortIndex, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Deck>>

    @Query("SELECT * FROM deck ORDER BY sortIndex, name COLLATE NOCASE")
    suspend fun all(): List<Deck>

    @Query("SELECT * FROM deck WHERE topicId = :topicId ORDER BY sortIndex, name COLLATE NOCASE")
    fun observeByTopic(topicId: Long): Flow<List<Deck>>

    @Query("SELECT * FROM deck WHERE topicId = :topicId ORDER BY sortIndex, name COLLATE NOCASE")
    suspend fun byTopic(topicId: Long): List<Deck>

    @Query("SELECT * FROM deck WHERE id = :id")
    fun observe(id: Long): Flow<Deck?>

    @Query("SELECT * FROM deck WHERE id = :id")
    suspend fun get(id: Long): Deck?

    @Query("SELECT * FROM deck WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Deck>

    @Query("SELECT * FROM deck WHERE topicId = :topicId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(topicId: Long, name: String): Deck?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM deck WHERE topicId = :topicId")
    suspend fun nextSortIndex(topicId: Long): Int

    @Insert
    suspend fun insert(d: Deck): Long

    @Insert
    suspend fun insertAll(d: List<Deck>)

    @Update
    suspend fun update(d: Deck)

    @Update
    suspend fun updateAll(d: List<Deck>)

    @Query("DELETE FROM deck WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM deck")
    suspend fun deleteAll()
}

@Dao
interface CardDao {
    /** Counters per deck; `eod` = start of the next day. */
    @Query(
        """
        SELECT deckId,
          SUM(CASE WHEN state = 'NEW' THEN 1 ELSE 0 END) AS newCount,
          SUM(CASE WHEN state IN ('LEARN','RELEARN') AND dueAt < :eod THEN 1 ELSE 0 END) AS learnCount,
          SUM(CASE WHEN state = 'REVIEW' AND dueAt <= :eod THEN 1 ELSE 0 END) AS dueCount,
          COUNT(*) AS total
        FROM card GROUP BY deckId
        """,
    )
    fun observeDeckCounts(eod: Long): Flow<List<DeckCounts>>

    @Query("SELECT * FROM card WHERE deckId = :deckId ORDER BY orderIndex")
    fun pagingByDeck(deckId: Long): PagingSource<Int, Card>

    @Query(
        """
        SELECT * FROM card WHERE deckId = :deckId
          AND (frontMd LIKE '%' || :q || '%' OR backMd LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%')
        ORDER BY orderIndex
        """,
    )
    fun pagingSearch(deckId: Long, q: String): PagingSource<Int, Card>

    @Query("SELECT * FROM card WHERE id = :id")
    suspend fun get(id: Long): Card?

    @Query("SELECT * FROM card WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Card>

    @Query("SELECT * FROM card WHERE deckId = :deckId ORDER BY orderIndex")
    suspend fun byDeck(deckId: Long): List<Card>

    @Query("SELECT id, contentKey, backMd AS back, tags, type, options FROM card WHERE deckId = :deckId")
    suspend fun refsByDeck(deckId: Long): List<CardRefRow>

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM card WHERE deckId = :deckId")
    suspend fun nextOrderIndex(deckId: Long): Int

    @Query("SELECT DISTINCT tags FROM card WHERE deckId IN (:deckIds) AND tags != ''")
    suspend fun tagStrings(deckIds: List<Long>): List<String>

    // --- Queue built in SQL (index deckId, state, dueAt) ---
    @Query(
        """
        SELECT id, deckId, state, dueAt, orderIndex FROM card
        WHERE deckId IN (:deckIds) AND state IN ('LEARN','RELEARN') AND dueAt < :eod
        """,
    )
    suspend fun queueLearn(deckIds: List<Long>, eod: Long): List<QueueCard>

    @Query(
        """
        SELECT id, deckId, state, dueAt, orderIndex FROM card
        WHERE deckId IN (:deckIds) AND state = 'REVIEW' AND dueAt <= :eod
        """,
    )
    suspend fun queueReview(deckIds: List<Long>, eod: Long): List<QueueCard>

    @Query(
        """
        SELECT id, deckId, state, dueAt, orderIndex FROM card
        WHERE deckId = :deckId AND state = 'NEW' ORDER BY orderIndex LIMIT :limit
        """,
    )
    suspend fun queueNew(deckId: Long, limit: Int): List<QueueCard>

    @Query("SELECT id, deckId, state, dueAt, orderIndex FROM card WHERE deckId IN (:deckIds)")
    suspend fun queueAll(deckIds: List<Long>): List<QueueCard>

    @Query(
        """
        SELECT id, deckId, state, dueAt, orderIndex FROM card
        WHERE deckId IN (:deckIds) AND (' ' || REPLACE(tags, ',', ' ') || ' ') LIKE '% ' || :tag || ' %'
        """,
    )
    suspend fun queueByTag(deckIds: List<Long>, tag: String): List<QueueCard>

    @Insert
    suspend fun insert(c: Card): Long

    @Insert
    suspend fun insertAll(c: List<Card>)

    @Update
    suspend fun update(c: Card)

    @Query(
        """
        UPDATE card SET frontMd = :front, backMd = :back, tags = :tags, orderIndex = :orderIndex,
          type = :type, options = :options
        WHERE id = :id
        """,
    )
    suspend fun updateContent(
        id: Long,
        front: String,
        back: String,
        tags: String,
        orderIndex: Int,
        type: CardType,
        options: String,
    )

    @Query("DELETE FROM card WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM card WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        UPDATE card SET state = 'NEW', dueAt = 0, intervalDays = 0, ease = 2.5, reps = 0, lapses = 0, step = 0
        WHERE deckId IN (:deckIds)
        """,
    )
    suspend fun resetProgress(deckIds: List<Long>)

    @Query("SELECT * FROM card")
    suspend fun all(): List<Card>

    @Query("DELETE FROM card")
    suspend fun deleteAll()

    /** Reviews falling due over the next days (statistics). */
    @Query("SELECT dueAt FROM card WHERE state != 'NEW' AND dueAt < :until")
    suspend fun dueTimesUntil(until: Long): List<Long>

    @Query("SELECT state, COUNT(*) AS n FROM card GROUP BY state")
    suspend fun stateCounts(): List<StateCount>
}

data class CardRefRow(
    val id: Long,
    val contentKey: String,
    val back: String,
    val tags: String,
    val type: CardType,
    val options: String,
)
data class StateCount(val state: CardState, val n: Int)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(r: ReviewLog)

    @Insert
    suspend fun insertAll(r: List<ReviewLog>)

    @Query(
        """
        SELECT c.deckId AS deckId, COUNT(*) AS n FROM review_log r JOIN card c ON c.id = r.cardId
        WHERE r.prevState = 'NEW' AND r.reviewedAt >= :dayStart GROUP BY c.deckId
        """,
    )
    fun observeNewToday(dayStart: Long): Flow<List<DeckNewToday>>

    @Query(
        """
        SELECT c.deckId AS deckId, COUNT(*) AS n FROM review_log r JOIN card c ON c.id = r.cardId
        WHERE r.prevState = 'NEW' AND r.reviewedAt >= :dayStart GROUP BY c.deckId
        """,
    )
    suspend fun newToday(dayStart: Long): List<DeckNewToday>

    @Query("SELECT reviewedAt FROM review_log WHERE reviewedAt >= :since")
    suspend fun reviewTimesSince(since: Long): List<Long>

    @Query("SELECT COUNT(*) FROM review_log")
    suspend fun count(): Int

    @Query("SELECT * FROM review_log")
    suspend fun all(): List<ReviewLog>

    @Query("DELETE FROM review_log")
    suspend fun deleteAll()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media")
    fun observeAll(): Flow<List<Media>>

    @Query("SELECT * FROM media")
    suspend fun all(): List<Media>

    @Upsert
    suspend fun upsert(m: Media)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(m: List<Media>)

    @Query("DELETE FROM media")
    suspend fun deleteAll()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM study_preset ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StudyPreset>>

    @Query("SELECT * FROM study_preset")
    suspend fun all(): List<StudyPreset>

    @Query("SELECT * FROM study_preset WHERE id = :id")
    suspend fun get(id: Long): StudyPreset?

    @Query("SELECT presetId, deckId FROM study_preset_deck")
    fun observeLinks(): Flow<List<PresetDeckRow>>

    @Query("SELECT * FROM study_preset_deck")
    suspend fun allLinks(): List<StudyPresetDeck>

    @Query("SELECT deckId FROM study_preset_deck WHERE presetId = :presetId")
    suspend fun deckIds(presetId: Long): List<Long>

    @Insert
    suspend fun insert(p: StudyPreset): Long

    @Insert
    suspend fun insertAll(p: List<StudyPreset>)

    @Update
    suspend fun update(p: StudyPreset)

    @Query("DELETE FROM study_preset WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<StudyPresetDeck>)

    @Query("DELETE FROM study_preset_deck WHERE presetId = :presetId")
    suspend fun clearLinks(presetId: Long)

    @Transaction
    suspend fun replaceLinks(presetId: Long, deckIds: List<Long>) {
        clearLinks(presetId)
        insertLinks(deckIds.map { StudyPresetDeck(presetId, it) })
    }

    @Query("DELETE FROM study_preset")
    suspend fun deleteAll()
}

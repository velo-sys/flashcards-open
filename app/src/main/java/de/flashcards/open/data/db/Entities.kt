package de.flashcards.open.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.SrsState
import de.flashcards.open.domain.study.MixMode
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "topic")
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val emoji: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "deck",
    foreignKeys = [ForeignKey(Topic::class, ["id"], ["topicId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("topicId")],
)
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val name: String,
    val sourceFileName: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** null = use the global setting */
    val newPerDay: Int? = null,
)

@Serializable
@Entity(
    tableName = "card",
    foreignKeys = [ForeignKey(Deck::class, ["id"], ["deckId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("deckId", "state", "dueAt"),
        Index("deckId", "state", "orderIndex"),
        Index("deckId", "contentKey"),
    ],
)
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val frontMd: String,
    val backMd: String,
    val tags: String = "",
    val orderIndex: Int = 0,
    /** Hash of the front, used to match cards on re-import */
    val contentKey: String,
    /** How the card is asked; drives rendering and grading in the study view */
    @ColumnInfo(defaultValue = "BASIC") val type: CardType = CardType.BASIC,
    /**
     * Answer options, separated by `|`: for [CardType.CHOICE] the wrong answers ([backMd] being
     * the correct one), for [CardType.INPUT] further accepted answers.
     */
    @ColumnInfo(defaultValue = "") val options: String = "",
    val state: CardState = CardState.NEW,
    val dueAt: Long = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
) {
    val srs get() = SrsState(state, dueAt, intervalDays, ease, reps, lapses, step)

    fun withSrs(s: SrsState) = copy(
        state = s.state, dueAt = s.dueAt, intervalDays = s.intervalDays,
        ease = s.ease, reps = s.reps, lapses = s.lapses, step = s.step,
    )

    fun tagList(): List<String> = tags.split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }
}

@Serializable
@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(Card::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId"), Index("reviewedAt")],
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val reviewedAt: Long,
    val rating: Int,
    val prevState: CardState,
    val newIntervalDays: Int,
)

@Serializable
@Entity(tableName = "media")
data class Media(
    /** normalised: NFC, lower case, without the path */
    @PrimaryKey val fileName: String,
    /** relative to filesDir, e.g. `media/3fa9….png` */
    val relativePath: String,
    val sizeBytes: Long,
)

@Serializable
@Entity(tableName = "study_preset")
data class StudyPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val shuffleMode: MixMode = MixMode.MIXED,
    val newLimit: Int? = null,
)

@Serializable
@Entity(
    tableName = "study_preset_deck",
    primaryKeys = ["presetId", "deckId"],
    foreignKeys = [
        ForeignKey(StudyPreset::class, ["id"], ["presetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Deck::class, ["id"], ["deckId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("deckId")],
)
data class StudyPresetDeck(val presetId: Long, val deckId: Long)

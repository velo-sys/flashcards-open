package de.flashcards.open.ui.nav

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class TopicRoute(val topicId: Long)

@Serializable
data class DeckRoute(val deckId: Long)

/**
 * A study session across any number of decks.
 * @param deckIds comma separated IDs
 * @param newLimit overall cap on new cards for the session, -1 = none
 * @param mode name of a [de.flashcards.open.domain.study.MixMode], empty = use the setting
 */
@Serializable
data class StudyRoute(
    val deckIds: String,
    val cram: Boolean = false,
    val tag: String = "",
    val mode: String = "",
    val newLimit: Int = -1,
    val title: String = "",
) {
    fun ids(): List<Long> = deckIds.split(',').mapNotNull { it.trim().toLongOrNull() }

    companion object {
        fun of(ids: Collection<Long>, cram: Boolean = false, tag: String = "", mode: String = "", newLimit: Int? = null, title: String = "") =
            StudyRoute(ids.joinToString(","), cram, tag, mode, newLimit ?: -1, title)
    }
}

/** Combined study; presetId = -1 for a fresh selection, otherwise edit that preset. */
@Serializable
data class ComboRoute(val presetId: Long = -1, val preselectDeckIds: String = "")

@Serializable
data class ImportCsvRoute(val uri: String, val topicId: Long = -1, val deckId: Long = -1)

@Serializable
data class BulkImportRoute(val uri: String, val zip: Boolean)

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

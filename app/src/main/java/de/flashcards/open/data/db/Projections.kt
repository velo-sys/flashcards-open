package de.flashcards.open.data.db

/** A deck's counters, aggregated in SQL. */
data class DeckCounts(
    val deckId: Long,
    val newCount: Int,
    val learnCount: Int,
    val dueCount: Int,
    val total: Int,
)

data class DeckNewToday(val deckId: Long, val n: Int)

data class DayCount(val day: String, val n: Int)

data class PresetDeckRow(val presetId: Long, val deckId: Long)

data class Counts(val new: Int = 0, val learn: Int = 0, val due: Int = 0, val total: Int = 0) {
    operator fun plus(o: Counts) = Counts(new + o.new, learn + o.learn, due + o.due, total + o.total)
    val isEmpty get() = new + learn + due == 0
}

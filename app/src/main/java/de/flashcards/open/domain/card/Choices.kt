package de.flashcards.open.domain.card

import kotlin.random.Random

/**
 * The answer options of a choice card. The correct answer is the card's back, the wrong ones sit
 * in the `options` column, separated by `|`.
 */
object Choices {

    const val SEPARATOR = '|'

    /** One answer option on a button. */
    data class Choice(val text: String, val correct: Boolean)

    /** Splits an `options` column; empty entries and duplicates are dropped. */
    fun split(options: String): List<String> =
        options.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Builds the buttons of a choice card. Shuffling uses [seed] (the card ID) so the order stays
     * stable for a given card and does not change on recomposition.
     */
    fun build(correct: String, options: String, seed: Long): List<Choice> {
        val right = correct.trim()
        val wrong = split(options).filter { !it.equals(right, ignoreCase = true) }
        val all = buildList {
            if (right.isNotEmpty()) add(Choice(right, true))
            wrong.forEach { add(Choice(it, false)) }
        }
        return all.shuffled(Random(seed))
    }
}

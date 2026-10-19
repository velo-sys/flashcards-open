package de.flashcards.open.domain.card

import de.flashcards.open.domain.text.TextDecoding
import kotlin.math.max
import kotlin.math.min

/**
 * Compares a typed answer against the expected one. The result is only a suggestion: the final
 * grade is given by the user, who can overrule it.
 */
object AnswerCheck {

    private val PUNCT = Regex("""[\p{Punct}„“”»«›‹…–—]+""")
    private val SPACE = Regex("""\s+""")

    /** Case, punctuation and repeated whitespace do not matter for the comparison. */
    fun normalize(s: String): String =
        TextDecoding.nfc(s).lowercase().replace(PUNCT, " ").replace(SPACE, " ").trim()

    /**
     * @param expected the expected answer (the card's back)
     * @param alternatives further accepted answers (the card's `options` column)
     */
    fun grade(input: String, expected: String, alternatives: List<String> = emptyList()): SelfGrade {
        val given = normalize(input)
        if (given.isEmpty()) return SelfGrade.WRONG
        val targets = (listOf(expected) + alternatives).map(::normalize).filter { it.isNotEmpty() }
        // Without an expected answer there is nothing to check – the user decides
        if (targets.isEmpty()) return SelfGrade.PARTIAL
        if (targets.any { it == given }) return SelfGrade.CORRECT
        val partial = targets.any { target ->
            distance(given, target) <= tolerance(target.length) ||
                given.length >= 3 && (target.contains(given) || given.contains(target))
        }
        return if (partial) SelfGrade.PARTIAL else SelfGrade.WRONG
    }

    /** Allowed typos: one per eight characters of the expected answer, at least one. */
    private fun tolerance(length: Int): Int = max(1, length / 8)

    /**
     * Distance between two strings (Damerau-Levenshtein, optimal string alignment). Swapped
     * neighbours count as a single error – “Mississipip” is a typo, “Missouri” a different answer.
     */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var d = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d = min(d, prev2[j - 2] + 1)
                }
                cur[j] = d
            }
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

package de.flashcards.open.domain.card

import kotlin.random.Random

/**
 * Matching card: the terms on the left, their targets on the right.
 *
 * The pairs live in the card's **back**, separated by `|` (or one per line), term and target
 * separated by `=>`:
 *
 * ```
 * HTTP => 80 | HTTPS => 443 | SSH => 22
 * ```
 *
 * `->`, `→` and `=` work instead of `=>`. Type inference only looks at the unambiguous arrows –
 * a plain `=` shows up far too often in ordinary answers (`E = mc²`).
 *
 * `options` may hold additional **wrong** targets (distractors), separated by `|`.
 */
object Matching {

    /** Separator between two pairs; every line break counts as one as well. */
    const val SEPARATOR = '|'

    /** Canonical arrow between term and target – used for export and display. */
    const val ARROW = "=>"

    private val SPLIT = Regex("""[|\n]""")
    private val ARROW_ANY = Regex("""=>|->|→|=""")
    private val ARROW_CLEAR = Regex("""=>|->|→""")

    /** One pair of the solution. */
    data class MatchPair(val item: String, val target: String)

    /**
     * A matching card ready for display: [items] in the order of the card, [targets] shuffled and
     * extended by the distractors.
     *
     * The user's assignment is a mapping from term index to target index in [targets].
     */
    data class Board(
        val items: List<String>,
        val targets: List<String>,
        /** Expected target per term, in the same order as [items]. */
        val solution: List<String>,
    ) {
        val size get() = items.size

        fun expected(item: Int): String = solution.getOrElse(item) { "" }

        /**
         * The target's **text** is compared, not its position: two terms may share a target, and
         * case and punctuation do not matter, just as in [AnswerCheck].
         */
        fun isCorrect(item: Int, target: String): Boolean {
            val want = AnswerCheck.normalize(expected(item))
            return want.isNotEmpty() && AnswerCheck.normalize(target) == want
        }

        /** Number of correctly matched terms; unassigned ones count as wrong. */
        fun correctCount(assignment: Map<Int, Int>): Int = items.indices.count { i ->
            val target = assignment[i]?.let { targets.getOrNull(it) }
            target != null && isCorrect(i, target)
        }
    }

    /** Splits the back into pairs; segments without an arrow or with an empty side are dropped. */
    fun pairs(text: String): List<MatchPair> = text.split(SPLIT).mapNotNull { segment ->
        val arrow = ARROW_ANY.find(segment) ?: return@mapNotNull null
        val item = segment.substring(0, arrow.range.first).trim()
        val target = segment.substring(arrow.range.last + 1).trim()
        if (item.isEmpty() || target.isEmpty()) null else MatchPair(item, target)
    }

    /** Does the back contain pairs at all? Decides whether a card can be asked. */
    fun hasPairs(text: String): Boolean = pairs(text).isNotEmpty()

    /**
     * Inference from the content (no type column): at least two pairs with an unambiguous arrow.
     * A single pair would not be a matching card, and `=` on its own is too unreliable.
     */
    fun looksLikePairs(text: String): Boolean =
        text.split(SPLIT).count { ARROW_CLEAR.containsMatchIn(it) } >= 2 && pairs(text).size >= 2

    /**
     * Builds the card for display. Only the targets are shuffled, using [seed] (the card ID), so
     * the order survives recomposition.
     */
    fun build(back: String, options: String, seed: Long): Board {
        val pairs = pairs(back)
        val solution = pairs.map { it.target }
        val wanted = solution.mapTo(HashSet()) { AnswerCheck.normalize(it) }
        val distractors = Choices.split(options).filter { AnswerCheck.normalize(it) !in wanted }
        val targets = (solution + distractors).distinctBy { AnswerCheck.normalize(it) }.shuffled(Random(seed))
        return Board(pairs.map { it.item }, targets, solution)
    }

    /**
     * The next term without a target, searched from [from] in a circle. If everything is taken it
     * stays at [from] – picking another target then only changes that one term.
     */
    fun nextOpen(size: Int, from: Int, assignment: Map<Int, Int>): Int {
        if (size <= 0) return 0
        val start = from.coerceIn(0, size - 1)
        return (1..size).map { (start + it) % size }.firstOrNull { it !in assignment } ?: start
    }

    /** All right / some right / none right – only a suggestion, just as in [AnswerCheck]. */
    fun grade(board: Board, assignment: Map<Int, Int>): SelfGrade {
        if (board.size == 0) return SelfGrade.PARTIAL
        return when (board.correctCount(assignment)) {
            board.size -> SelfGrade.CORRECT
            0 -> SelfGrade.WRONG
            else -> SelfGrade.PARTIAL
        }
    }

    /** The solution as a Markdown list – for previews and the card list. */
    fun render(text: String): String =
        pairs(text).joinToString("\n") { "- **${it.item}** $ARROW ${it.target}" }

    /** Pairs in canonical notation, e.g. for the CSV export. */
    fun join(pairs: List<MatchPair>): String =
        pairs.joinToString(" $SEPARATOR ") { "${it.item} $ARROW ${it.target}" }
}

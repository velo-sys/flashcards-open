package de.flashcards.open.domain.card

/**
 * Cloze text. The gaps are written in double curly braces, Anki notation included:
 * `{{Berlin}}`, `{{c1::Berlin}}`, `{{c1::Berlin::capital}}` or `{{Berlin::capital}}` – the part
 * after the second `::` is a hint that is shown inside the gap while it is still closed.
 */
object Cloze {

    /** A single gap: [index] is its number (`c1` → 1, otherwise the order within the text). */
    data class Gap(val index: Int, val answer: String, val hint: String? = null)

    private val GAP = Regex("""\{\{([^{}]*)}}""")
    private val INDEX = Regex("""c(\d+)""", RegexOption.IGNORE_CASE)

    fun hasGaps(text: String): Boolean = GAP.containsMatchIn(text)

    fun gaps(text: String): List<Gap> =
        GAP.findAll(text).mapIndexed { i, m -> parse(m.groupValues[1], i + 1) }.toList()

    private fun parse(inner: String, position: Int): Gap {
        val parts = inner.split("::")
        val m = INDEX.matchEntire(parts[0].trim())
        return if (m != null && parts.size > 1) {
            Gap(
                index = m.groupValues[1].toIntOrNull() ?: position,
                answer = parts[1].trim(),
                hint = parts.drop(2).joinToString("::").trim().ifEmpty { null },
            )
        } else {
            Gap(
                index = position,
                answer = parts[0].trim(),
                hint = parts.drop(1).joinToString("::").trim().ifEmpty { null },
            )
        }
    }

    /**
     * Replaces the gaps for display: closed as `[ … ]` or `[ hint ]`, revealed as the answer in
     * bold. Text without gaps is returned unchanged.
     */
    fun render(text: String, reveal: Boolean): String {
        var position = 0
        return GAP.replace(text) { m ->
            val gap = parse(m.groupValues[1], ++position)
            if (reveal) {
                if (gap.answer.isEmpty()) "**[ … ]**" else "**${gap.answer}**"
            } else {
                "**[ ${gap.hint ?: "…"} ]**"
            }
        }
    }

    /** Every gap answer, used to check typed input and to build previews. */
    fun answers(text: String): List<String> = gaps(text).map { it.answer }.filter { it.isNotEmpty() }

    /**
     * Turns the selection [start]–[end] into a gap (the editor's “Insert gap” button). Without a
     * selection an empty pair of braces is inserted. Returns the new text and the resulting cursor
     * position: without a selection the cursor sits **inside** the gap, with one it sits behind it.
     */
    fun insertGap(text: String, start: Int, end: Int): Pair<String, Int> {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        val inner = text.substring(from, to)
        val new = text.substring(0, from) + "{{" + inner + "}}" + text.substring(to)
        return new to if (inner.isEmpty()) from + 2 else to + 4
    }
}

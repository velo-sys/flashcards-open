package de.flashcards.open.domain.card

import de.flashcards.open.domain.scheduler.Rating

/**
 * Kind of a card. It decides how the front is asked.
 *
 * - [BASIC]: front, flip, back – graded on four levels.
 * - [CLOZE]: cloze text, the gaps sit in the front as `{{…}}` (see [Cloze]).
 * - [INPUT]: the answer is typed in and compared against the back (see [AnswerCheck]).
 * - [CHOICE]: pick the answer from buttons; the back is correct, `options` are the wrong ones.
 * - [MATCH]: matching, the pairs sit in the back as `A => 1 | B => 2` (see [Matching]).
 *
 * Everything but [BASIC] is graded on three levels (correct / partly / wrong, see [SelfGrade]).
 */
enum class CardType(
    /** Label in the `Type` column on CSV export; the import recognises it again. */
    val csvName: String,
) {
    BASIC("Basic"),
    CLOZE("Cloze"),
    INPUT("Input"),
    CHOICE("Choice"),
    MATCH("Match"),
    ;

    /** Give an answer instead of merely flipping – and therefore grade on three levels. */
    val threeLevel get() = this != BASIC

    companion object {
        /**
         * Accepted spellings per type, normalised to lower case without separators. The German
         * names are kept as aliases so CSV files written for earlier versions still import.
         */
        private val NAMES: Map<String, CardType> = buildMap {
            listOf("basic", "standard", "simple", "normal", "card", "karte", "einfach", "frageantwort").forEach { put(it, BASIC) }
            listOf("cloze", "gap", "gaps", "fillin", "fillintheblank", "blank", "lücke", "luecke", "lückentext", "lueckentext", "lücken", "luecken").forEach { put(it, CLOZE) }
            listOf("input", "typing", "typein", "type", "text", "write", "eingabe", "eingabefeld", "tippen", "schreiben").forEach { put(it, INPUT) }
            listOf("choice", "buttons", "button", "mc", "multiple", "multiplechoice", "auswahl").forEach { put(it, CHOICE) }
            listOf("match", "matching", "pairs", "pair", "connect", "zuordnung", "zuordnungen", "zuordnen", "paare", "verbinden").forEach { put(it, MATCH) }
        }

        /** Reads the type from a CSV column; `null` if it is missing or unknown. */
        fun parse(raw: String?): CardType? {
            val key = raw?.trim()?.lowercase()?.replace(Regex("""[\s_\-.]+"""), "") ?: return null
            return if (key.isEmpty()) null else NAMES[key]
        }
    }
}

/**
 * Three-level self assessment for the card types that take an answer. It is mapped onto the SM-2
 * scheduler, which itself stays four-level.
 */
enum class SelfGrade(val rating: Rating) {
    WRONG(Rating.AGAIN),
    PARTIAL(Rating.HARD),
    CORRECT(Rating.GOOD),
}

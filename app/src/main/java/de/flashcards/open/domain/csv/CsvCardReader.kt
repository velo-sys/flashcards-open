package de.flashcards.open.domain.csv

import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.text.DecodedText
import de.flashcards.open.domain.text.DetectedEncoding
import de.flashcards.open.domain.text.TextDecoding

data class CsvCard(
    val front: String,
    val back: String,
    val tags: String,
    val type: CardType = CardType.BASIC,
    /**
     * Separated by `|`: for CHOICE the wrong answers, for INPUT further accepted ones, for MATCH
     * additional wrong targets.
     */
    val options: String = "",
)

data class ParsedCsv(
    val cards: List<CsvCard>,
    val delimiter: Char,
    val headerFound: Boolean,
    val encoding: DetectedEncoding,
    /** The header row contained a card type column. */
    val typeColumnFound: Boolean = false,
    /** Rows whose type was unknown; their type was inferred from the content. */
    val unknownTypes: Int = 0,
    /** Rows typed `Match` whose answer held no pair; they became basic cards. */
    val matchWithoutPairs: Int = 0,
)

/**
 * Turns a CSV file into cards – behaves like `parseCSV()` in the reference prototype. One
 * difference: the single-letter header words “q”/“a” of the reference are not recognised, because
 * data rows such as “Q;A” would otherwise be swallowed as a header.
 */
object CsvCardReader {
    // Accepted column names, lower case. German names are kept so older CSV files still import.
    private val FRONT = setOf("front", "question", "term", "prompt", "vorderseite", "frage", "begriff", "vorne")
    private val BACK = setOf("back", "answer", "definition", "solution", "rückseite", "rueckseite", "antwort", "hinten")
    private val TAGS = setOf("tags", "tag", "category", "topic", "kategorie", "thema")
    private val TYPE = setOf("type", "cardtype", "kind", "typ", "kartentyp", "art")
    private val OPTIONS = setOf("options", "option", "choices", "distractors", "wrong", "optionen", "auswahl", "alternativen", "distraktoren", "falsch")

    private val SEPARATOR_NAMES = mapOf(
        "comma" to ',', "semicolon" to ';', "tab" to '\t', "pipe" to '|', "space" to ' ', "colon" to ':',
    )
    private val SEPARATOR_LINE = Regex("""^#\s*separator\s*:\s*(\S+)\s*$""", RegexOption.IGNORE_CASE)
    private val META_LINE = Regex("""^#\w+\s*:.*$""")

    fun read(bytes: ByteArray): ParsedCsv = read(TextDecoding.decode(bytes))

    fun read(decoded: DecodedText): ParsedCsv {
        var text = decoded.text.removePrefix("\uFEFF")
        var explicitDelimiter: Char? = null

        // Read and strip Anki header lines (#separator:..., #html:...)
        while (true) {
            val lineEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
            val line = text.substring(0, lineEnd).trimEnd('\r')
            if (!META_LINE.matches(line)) break
            SEPARATOR_LINE.find(line)?.let { m ->
                val v = m.groupValues[1]
                explicitDelimiter = SEPARATOR_NAMES[v.lowercase()] ?: v.singleOrNull()
            }
            text = if (lineEnd >= text.length) "" else text.substring(lineEnd + 1)
        }

        val delimiter = explicitDelimiter ?: CsvParser.detectDelimiter(text)
        var rows = CsvParser.parse(text, delimiter).filter { row -> row.any { it.isNotBlank() } }

        var front = 0
        var back = 1
        var tags = if (rows.isNotEmpty() && rows[0].size >= 3) 2 else -1
        var type = -1
        var options = -1
        var headerFound = false
        if (rows.isNotEmpty()) {
            val h = rows[0].map { it.trim().lowercase() }
            val f = h.indexOfFirst { it in FRONT }
            val b = h.indexOfFirst { it in BACK }
            if (f >= 0 || b >= 0) {
                headerFound = true
                front = if (f >= 0) f else 0
                back = if (b >= 0) b else if (f == 0) 1 else 0
                tags = h.indexOfFirst { it in TAGS }
                type = h.indexOfFirst { it in TYPE }
                options = h.indexOfFirst { it in OPTIONS }
                rows = rows.drop(1)
            }
        }

        var unknownTypes = 0
        var matchWithoutPairs = 0
        val cards = rows.map { row ->
            val f = clean(row.getOrNull(front))
            val b = clean(row.getOrNull(back))
            val opts = if (options >= 0) clean(row.getOrNull(options)) else ""
            val rawType = if (type >= 0) clean(row.getOrNull(type)) else ""
            val parsedType = CardType.parse(rawType)
            if (rawType.isNotEmpty() && parsedType == null) unknownTypes++
            var cardType = parsedType ?: inferType(f, b, opts)
            // A match card without pairs could not be asked – it becomes a basic card
            if (cardType == CardType.MATCH && !Matching.hasPairs(b)) {
                matchWithoutPairs++
                cardType = CardType.BASIC
            }
            CsvCard(
                front = f,
                back = b,
                tags = if (tags >= 0) clean(row.getOrNull(tags)) else "",
                type = cardType,
                options = opts,
            )
        }.filter { it.front.isNotEmpty() || it.back.isNotEmpty() }

        return ParsedCsv(cards, delimiter, headerFound, decoded.encoding, type >= 0, unknownTypes, matchWithoutPairs)
    }

    /**
     * Without a type column (or with an unknown value) the type is inferred from the content: gaps
     * `{{…}}` in the front make a cloze card, pairs `A => 1 | B => 2` in the answer a match card,
     * answer options a choice card, everything else stays basic. That is what lets all five types
     * share one file.
     */
    private fun inferType(front: String, back: String, options: String): CardType = when {
        Cloze.hasGaps(front) -> CardType.CLOZE
        Matching.looksLikePairs(back) -> CardType.MATCH
        options.isNotEmpty() -> CardType.CHOICE
        else -> CardType.BASIC
    }

    /** A literal `\n` (e.g. coming from Excel) becomes a real line break. */
    private fun clean(s: String?): String = (s ?: "").replace("\\n", "\n").trim()
}

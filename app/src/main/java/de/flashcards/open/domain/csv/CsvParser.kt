package de.flashcards.open.domain.csv

/**
 * RFC 4180 parser: quoted fields, `""` as the escape, line breaks inside fields. Tolerant towards
 * quotes in the middle of a field (they are kept as text).
 */
object CsvParser {

    fun parse(text: String, delimiter: Char, maxRecords: Int = Int.MAX_VALUE): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false // field already holds characters, or started with a quote
        var i = 0
        val n = text.length

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
            fieldStarted = false
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = ArrayList()
        }

        while (i < n && records.size < maxRecords) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"'); i += 2; continue
                    }
                    inQuotes = false
                } else {
                    field.append(c)
                }
                i++
                continue
            }
            when {
                c == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                c == delimiter -> endField()
                c == '\r' -> {
                    endRecord()
                    if (i + 1 < n && text[i + 1] == '\n') i++
                }
                c == '\n' -> endRecord()
                else -> { field.append(c); fieldStarted = true }
            }
            i++
        }
        if (records.size < maxRecords && (field.isNotEmpty() || fields.isNotEmpty() || fieldStarted)) {
            endRecord()
        }
        return records
    }

    val CANDIDATES = listOf(';', '\t', ',', '|')

    /**
     * Picks the delimiter that yields the most consistent column count (> 1) across the first
     * records. Quoting is honoured while probing.
     */
    fun detectDelimiter(text: String, sampleRecords: Int = 10): Char {
        var best: Char? = null
        var bestScore = -1
        var bestColumns = 0
        for (candidate in CANDIDATES) {
            val rows = parse(text, candidate, sampleRecords * 2)
                .filter { row -> row.any { it.isNotBlank() } }
                .take(sampleRecords)
            if (rows.isEmpty()) continue
            val mode = rows.groupingBy { it.size }.eachCount().maxWithOrNull(
                compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key },
            ) ?: continue
            if (mode.key < 2) continue
            val score = mode.value
            if (score > bestScore || (score == bestScore && mode.key > bestColumns)) {
                best = candidate; bestScore = score; bestColumns = mode.key
            }
        }
        return best ?: ','
    }
}

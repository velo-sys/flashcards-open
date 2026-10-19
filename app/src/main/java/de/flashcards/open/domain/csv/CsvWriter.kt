package de.flashcards.open.domain.csv

/** Writes CSV as UTF-8 with BOM and semicolons, readable by the import again. */
object CsvWriter {
    fun write(header: List<String>, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder("\uFEFF")
        (listOf(header) + rows).forEach { row ->
            sb.append(row.joinToString(";") { quote(it) }).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun quote(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' } || s != s.trim()) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
}

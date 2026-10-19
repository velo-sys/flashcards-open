package de.flashcards.open.domain

import de.flashcards.open.domain.csv.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {
    @Test fun simpleSemicolon() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), CsvParser.parse("a;b\nc;d", ';'))
    }

    @Test fun quotesEscapesAndNewlines() {
        val text = "\"Hello; world\";\"He said \"\"Hi\"\"\"\n\"line 1\nline 2\";x\r\nlast;\"\""
        assertEquals(
            listOf(
                listOf("Hello; world", "He said \"Hi\""),
                listOf("line 1\nline 2", "x"),
                listOf("last", ""),
            ),
            CsvParser.parse(text, ';'),
        )
    }

    @Test fun crOnlyLineEndingsAndTrailingNewline() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), CsvParser.parse("a,b\rc,d\r\n", ','))
    }

    @Test fun quoteInsideUnquotedFieldIsLiteral() {
        assertEquals(listOf(listOf("5\" inch", "x")), CsvParser.parse("5\" inch;x", ';'))
    }

    @Test fun emptyFieldsAreKept() {
        assertEquals(listOf(listOf("a", "", "c")), CsvParser.parse("a;;c", ';'))
    }

    @Test fun detectsAllDelimiters() {
        assertEquals(';', CsvParser.detectDelimiter("Front;Back;Tags\na;b;c\nd;e;f"))
        assertEquals(',', CsvParser.detectDelimiter("dog,Hund\nstreet,\"Straße, Weg\"\napples,Äpfel"))
        assertEquals('\t', CsvParser.detectDelimiter("a\tb, with comma\nc\td; with semicolon"))
        assertEquals('|', CsvParser.detectDelimiter("a|b\nc|d\ne|f"))
    }

    @Test fun detectionRespectsQuotes() {
        // the commas only appear inside quotes → the semicolon is the delimiter
        assertEquals(';', CsvParser.detectDelimiter("\"a, b, c\";x\n\"d, e\";y\n\"f\";z"))
    }

    @Test fun detectionWithMultilineFields() {
        val text = "Front;Back;Tags\n\"Q1\";\"A\n- 1\n- 2\";T\nQ2;\"B\n\n![](x.png)\";T"
        assertEquals(';', CsvParser.detectDelimiter(text))
        assertEquals(3, CsvParser.parse(text, ';').size)
    }
}

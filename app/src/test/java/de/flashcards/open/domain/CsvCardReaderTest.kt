package de.flashcards.open.domain

import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.csv.CsvCard
import de.flashcards.open.domain.csv.CsvCardReader
import de.flashcards.open.domain.csv.CsvWriter
import de.flashcards.open.domain.text.DetectedEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CsvCardReaderTest {
    private fun read(s: String) = CsvCardReader.read(s.toByteArray(Charsets.UTF_8))

    @Test fun headerWithTags() {
        val r = read("Front;Back;Tags\nQ;A;t1 t2")
        assertTrue(r.headerFound)
        assertEquals(listOf(CsvCard("Q", "A", "t1 t2")), r.cards)
    }

    @Test fun headerInDifferentOrder() {
        val r = read("Tags,Answer,Question\nx,A,Q")
        assertEquals(listOf(CsvCard("Q", "A", "x")), r.cards)
    }

    @Test fun headerWordsFromSpec() {
        for (front in listOf("front", "question", "term", "vorderseite", "frage")) {
            for (back in listOf("back", "answer", "definition", "rückseite", "antwort")) {
                val r = read("$front;$back;category\nQ;A;T")
                assertTrue(r.headerFound)
                assertEquals(CsvCard("Q", "A", "T"), r.cards.single())
            }
        }
    }

    @Test fun withoutHeader() {
        val r = read("dog;Hund\ncat;Katze")
        assertFalse(r.headerFound)
        assertEquals(listOf(CsvCard("dog", "Hund", ""), CsvCard("cat", "Katze", "")), r.cards)
    }

    @Test fun withoutHeaderThirdColumnIsTags() {
        assertEquals(CsvCard("a", "b", "c"), read("a;b;c\nd;e;f").cards.first())
    }

    @Test fun ankiSeparatorHeader() {
        val r = read("#separator:Tab\n#html:true\nfront\tback\nQ, with comma\tA; with semicolon")
        assertEquals('\t', r.delimiter)
        assertEquals(CsvCard("Q, with comma", "A; with semicolon", ""), r.cards.single())
    }

    @Test fun ankiSeparatorPipeAndLiteral() {
        assertEquals('|', read("#separator:Pipe\na|b").delimiter)
        assertEquals(';', read("#separator:;\na;b").delimiter)
    }

    @Test fun literalBackslashNBecomesNewline() {
        assertEquals("line 1\nline 2", read("Q;line 1\\nline 2").cards.single().back)
    }

    @Test fun skipsEmptyRowsAndTrims() {
        val r = read("  Q  ;  A  \n;\n\n")
        assertEquals(listOf(CsvCard("Q", "A", "")), r.cards)
    }

    @Test fun sampleWindows1252File() {
        val r = CsvCardReader.read(sample("Networking/Basics.csv"))
        assertEquals(DetectedEncoding.WINDOWS_1252, r.encoding)
        assertEquals(';', r.delimiter)
        assertTrue(r.headerFound)
        assertEquals(3, r.cards.size)
        assertEquals("What is **Größe** (ß/ä/ö/ü)?", r.cards[0].front)
        assertTrue(r.cards[0].back.startsWith("A unit of measure – “quotation marks” & € 5\n- item 1"))
        assertTrue(r.cards[1].back.contains("![](Network diagram.png)"))
        assertTrue(r.cards[2].back.contains("```bash\necho hi\n```"))
        assertEquals("linux", r.cards[2].tags)
    }

    @Test fun sampleUtf8CommaFile() {
        val r = CsvCardReader.read(sample("Vocabulary/German-basics.csv"))
        assertEquals(DetectedEncoding.UTF8, r.encoding)
        assertEquals(',', r.delimiter)
        assertFalse(r.headerFound)
        assertEquals(listOf(CsvCard("dog", "Hund", ""), CsvCard("street", "Straße, Weg", ""), CsvCard("apples", "Äpfel", "")), r.cards)
    }

    @Test fun writerRoundTrip() {
        val rows = listOf(listOf("Größe; „x“", "line 1\nline \"2\"", "t"), listOf("a", "b", ""))
        val bytes = CsvWriter.write(listOf("Front", "Back", "Tags"), rows)
        assertEquals(0xEF, bytes[0].toInt() and 0xFF) // BOM
        val r = CsvCardReader.read(bytes)
        assertEquals(DetectedEncoding.UTF8_BOM, r.encoding)
        assertEquals(listOf(CsvCard("Größe; „x“", "line 1\nline \"2\"", "t"), CsvCard("a", "b", "")), r.cards)
    }

    // ---------------- Card types ----------------

    @Test fun readsTypeAndOptionsColumns() {
        val r = read("Front;Back;Tags;Type;Options\nQ;A;t;Choice;B|C")
        assertTrue(r.typeColumnFound)
        assertEquals(0, r.unknownTypes)
        assertEquals(CsvCard("Q", "A", "t", CardType.CHOICE, "B|C"), r.cards.single())
    }

    @Test fun withoutTypeColumnCardsStayBasic() {
        val r = read("Front;Back;Tags\nQ;A;t")
        assertFalse(r.typeColumnFound)
        assertEquals(CardType.BASIC, r.cards.single().type)
    }

    @Test fun inferesClozeFromGapsAndChoiceFromOptions() {
        val r = read("Front;Back;Options\nThe sky is {{blue}}.;color;\nColor?;Blue;Red|Green")
        assertEquals(listOf(CardType.CLOZE, CardType.CHOICE), r.cards.map { it.type })
    }

    @Test fun unknownTypeIsCountedAndFallsBackToContent() {
        val r = read("Front;Back;Type\nQ;A;Quizshow\n{{gap}} here;A;Quizshow")
        assertEquals(2, r.unknownTypes)
        assertEquals(listOf(CardType.BASIC, CardType.CLOZE), r.cards.map { it.type })
    }

    @Test fun readsTypesFromSampleData() {
        val r = CsvCardReader.read(sample("Card-types/All-types.csv"))
        assertEquals(DetectedEncoding.UTF8_BOM, r.encoding)
        assertTrue(r.typeColumnFound)
        assertEquals(0, r.unknownTypes)
        assertEquals(0, r.matchWithoutPairs)
        assertEquals(
            listOf(
                CardType.CLOZE, CardType.INPUT, CardType.CHOICE, CardType.BASIC, CardType.CLOZE,
                CardType.MATCH, CardType.MATCH,
            ),
            r.cards.map { it.type },
        )
        val choice = r.cards.single { it.type == CardType.CHOICE }
        assertEquals("Switch|Hub|Repeater", choice.options)
        assertEquals("Paris, France", r.cards.single { it.type == CardType.INPUT }.options)
        // one row with a type column and distractors, one row recognised from its content alone
        val matches = r.cards.filter { it.type == CardType.MATCH }
        assertEquals("HTTP => 80 | HTTPS => 443 | SSH => 22", matches[0].back)
        assertEquals("25|3389", matches[0].options)
        assertEquals(3, Matching.pairs(matches[1].back).size)
    }

    // ---------------- Match cards ----------------

    @Test fun readsMatchColumnWithDistractors() {
        val r = read("Front;Back;Tags;Type;Options\nMatch them;A => 1 | B => 2;t;Match;3|4")
        assertEquals(CsvCard("Match them", "A => 1 | B => 2", "t", CardType.MATCH, "3|4"), r.cards.single())
        assertEquals(0, r.matchWithoutPairs)
    }

    @Test fun inferesMatchFromPairsInTheAnswer() {
        val r = read("Front;Back\nMatch them;Red => warm | Blue => cold\nColor?;Blue")
        assertEquals(listOf(CardType.MATCH, CardType.BASIC), r.cards.map { it.type })
    }

    @Test fun matchWinsOverChoiceWhenOptionsAreDistractors() {
        val r = read("Front;Back;Options\nMatch them;A => 1 | B => 2;3|4")
        assertEquals(CardType.MATCH, r.cards.single().type)
    }

    @Test fun matchWithoutPairsBecomesBasicAndIsCounted() {
        val r = read("Front;Back;Type\nMatch them;An answer without pairs;Match")
        assertEquals(1, r.matchWithoutPairs)
        assertEquals(CardType.BASIC, r.cards.single().type)
    }

    @Test fun allTypesMixedInOneFile() {
        val r = read(
            """
            Front;Back;Type;Options
            Basic?;An answer;;
            The sky is {{blue}}.;color;;
            Capital of France?;Paris;Input;Paris, France
            OSI layer 3?;Router;Choice;Switch|Hub
            Match the ports;HTTP => 80 | SSH => 22;Match;443
            Match the countries;Berlin -> Germany | Paris -> France;;
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                CardType.BASIC, CardType.CLOZE, CardType.INPUT, CardType.CHOICE,
                CardType.MATCH, CardType.MATCH,
            ),
            r.cards.map { it.type },
        )
    }

    private fun sample(path: String): ByteArray {
        val base = listOf("../sample-data/Flashcards", "sample-data/Flashcards").map(::File).first { it.exists() }
        return File(base, path).readBytes()
    }
}

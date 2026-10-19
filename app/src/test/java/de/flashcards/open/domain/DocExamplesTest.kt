package de.flashcards.open.domain

import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.csv.CsvCardReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Parses every ```csv block of the documentation with the real reader, so docs cannot drift. */
class DocExamplesTest {
    private fun repoRoot(): File =
        listOf(File(".."), File(".")).first { File(it, "README.md").exists() }

    private fun csvBlocks(name: String): List<String> {
        val text = File(repoRoot(), name).readText()
        return Regex("```csv\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).map { it.groupValues[1] }.toList()
    }

    @Test fun everyDocumentedCsvBlockParses() {
        var blocks = 0
        var cards = 0
        for (doc in listOf("README.md", "CSV-FORMAT.md", "AI-PROMPT.md", "WALKTHROUGH.md", "CLAUDE.md")) {
            for (block in csvBlocks(doc)) {
                // skip blocks that only show a header row or the #separator line
                val dataLines = block.trim().lines().filter { it.isNotBlank() && !it.startsWith("#") }
                if (dataLines.size < 2) continue
                blocks++
                val r = CsvCardReader.read(block.toByteArray(Charsets.UTF_8))
                assertTrue("$doc: block without cards:\n$block", r.cards.isNotEmpty())
                assertEquals("$doc: unknown type in:\n$block", 0, r.unknownTypes)
                assertEquals("$doc: match card without pairs in:\n$block", 0, r.matchWithoutPairs)
                cards += r.cards.size
            }
        }
        println("documentation CSV: $blocks blocks, $cards cards")
        assertTrue("too few blocks found: $blocks", blocks >= 5)
    }

    @Test fun theReadmeOverviewBlockYieldsAllFiveTypes() {
        val block = csvBlocks("README.md").first { it.contains("Match the protocols") }
        val r = CsvCardReader.read(block.toByteArray(Charsets.UTF_8))
        assertEquals(
            listOf(CardType.BASIC, CardType.CLOZE, CardType.INPUT, CardType.CHOICE, CardType.MATCH),
            r.cards.map { it.type },
        )
        assertTrue(r.headerFound)
        assertTrue(r.typeColumnFound)
    }

    @Test fun theAiPromptExampleLinesParseIntoTheirDeclaredType() {
        val prompt = File(repoRoot(), "AI-PROMPT.md").readText()
        val examples = Regex("""^\s*Example: (.+)$""", RegexOption.MULTILINE)
            .findAll(prompt).map { it.groupValues[1] }.toList()
        assertEquals("expected five example lines in the prompt", 5, examples.size)
        val expected = listOf(CardType.BASIC, CardType.CLOZE, CardType.INPUT, CardType.CHOICE, CardType.MATCH)
        val csv = "Front;Back;Tags;Type;Options\n" + examples.joinToString("\n")
        val r = CsvCardReader.read(csv.toByteArray(Charsets.UTF_8))
        assertEquals(5, r.cards.size)
        assertEquals(expected, r.cards.map { it.type })
        assertEquals(0, r.unknownTypes)
        assertEquals(0, r.matchWithoutPairs)
        // every line has exactly four semicolons, as the prompt demands
        examples.forEach { assertEquals("four semicolons in: $it", 4, it.count { c -> c == ';' }) }
    }
}

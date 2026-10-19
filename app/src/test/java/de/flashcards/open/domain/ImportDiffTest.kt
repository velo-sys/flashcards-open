package de.flashcards.open.domain

import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.csv.ContentKey
import de.flashcards.open.domain.csv.CsvCard
import de.flashcards.open.domain.csv.ExistingCardRef
import de.flashcards.open.domain.csv.ImportDiffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImportDiffTest {
    @Test fun contentKeyIgnoresSurroundingWhitespaceAndNormalization() {
        assertEquals(ContentKey.of("über"), ContentKey.of("  u\u0308ber "))
        assertNotEquals(ContentKey.of("a"), ContentKey.of("b"))
    }

    @Test fun duplicatesGetUniqueKeys() {
        val keys = ContentKey.assign(listOf("x", "x", "y", "x"))
        assertEquals(4, keys.toSet().size)
        assertEquals(ContentKey.of("x"), keys[0])
    }

    @Test fun diffAddChangeRemove() {
        val existing = listOf(
            ExistingCardRef(1, ContentKey.of("A"), "a", ""),
            ExistingCardRef(2, ContentKey.of("B"), "b", ""),
            ExistingCardRef(3, ContentKey.of("C"), "c", ""),
        )
        val d = ImportDiffer.diff(existing, listOf(CsvCard("A", "a", ""), CsvCard("B", "b new", ""), CsvCard("D", "d", "")))
        assertEquals(1, d.added)
        assertEquals(1, d.changed)
        assertEquals(listOf(3L), d.toDelete)
        assertEquals(listOf(1L, 2L), d.toUpdate.map { it.first })
    }

    @Test fun newPairsOrDistractorsCountAsChangedAndKeepTheCard() {
        val existing = listOf(
            ExistingCardRef(1, ContentKey.of("Ports"), "HTTP => 80 | SSH => 22", "", CardType.MATCH, "443"),
            ExistingCardRef(2, ContentKey.of("Countries"), "Berlin => DE", "", CardType.MATCH, ""),
        )
        val d = ImportDiffer.diff(
            existing,
            listOf(
                // one pair more
                CsvCard("Ports", "HTTP => 80 | SSH => 22 | DNS => 53", "", CardType.MATCH, "443"),
                // only a different distractor
                CsvCard("Countries", "Berlin => DE", "", CardType.MATCH, "Vienna"),
            ),
        )
        assertEquals(0, d.added)
        assertEquals(0, d.removed)
        assertEquals(2, d.changed)
        assertEquals(listOf(1L, 2L), d.toUpdate.map { it.first })
    }

    @Test fun aCardThatBecomesAMatchingIsAnUpdateNotANewCard() {
        val existing = listOf(ExistingCardRef(1, ContentKey.of("Ports"), "HTTP 80, SSH 22", ""))
        val d = ImportDiffer.diff(existing, listOf(CsvCard("Ports", "HTTP => 80 | SSH => 22", "", CardType.MATCH)))
        assertEquals(0, d.added)
        assertEquals(1, d.changed)
        assertEquals(listOf(1L), d.toUpdate.map { it.first })
    }
}

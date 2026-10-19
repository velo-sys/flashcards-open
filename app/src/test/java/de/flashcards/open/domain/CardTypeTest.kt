package de.flashcards.open.domain

import de.flashcards.open.domain.card.AnswerCheck
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Choices
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.card.SelfGrade
import de.flashcards.open.domain.scheduler.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTypeTest {

    @Test fun parsesGermanAndEnglishNames() {
        assertEquals(CardType.CLOZE, CardType.parse("Lückentext"))
        assertEquals(CardType.CLOZE, CardType.parse("cloze"))
        assertEquals(CardType.INPUT, CardType.parse(" Eingabe "))
        assertEquals(CardType.CHOICE, CardType.parse("Multiple Choice"))
        assertEquals(CardType.CHOICE, CardType.parse("multiple-choice"))
        assertEquals(CardType.MATCH, CardType.parse("Zuordnung"))
        assertEquals(CardType.MATCH, CardType.parse("matching"))
        assertEquals(CardType.MATCH, CardType.parse(" Paare "))
        assertEquals(CardType.BASIC, CardType.parse("Standard"))
    }

    @Test fun unknownOrEmptyTypeIsNull() {
        assertNull(CardType.parse(null))
        assertNull(CardType.parse("   "))
        assertNull(CardType.parse("Quizshow"))
    }

    @Test fun onlyBasicIsRatedWithFourButtons() {
        assertFalse(CardType.BASIC.threeLevel)
        assertTrue(CardType.CLOZE.threeLevel)
        assertTrue(CardType.INPUT.threeLevel)
        assertTrue(CardType.CHOICE.threeLevel)
        assertTrue(CardType.MATCH.threeLevel)
    }

    @Test fun csvNamesSurviveExportAndImport() {
        CardType.entries.forEach { assertEquals(it, CardType.parse(it.csvName)) }
    }

    @Test fun selfGradeMapsOntoScheduler() {
        assertEquals(Rating.AGAIN, SelfGrade.WRONG.rating)
        assertEquals(Rating.HARD, SelfGrade.PARTIAL.rating)
        assertEquals(Rating.GOOD, SelfGrade.CORRECT.rating)
    }

    // ---------------- Cloze ----------------

    @Test fun readsPlainAnkiAndHintedGaps() {
        val gaps = Cloze.gaps("{{Berlin}} and {{c2::Bonn}} plus {{c3::Vienna::Danube}} and {{Rome::Tiber}}")
        assertEquals(listOf(1, 2, 3, 4), gaps.map { it.index })
        assertEquals(listOf("Berlin", "Bonn", "Vienna", "Rome"), gaps.map { it.answer })
        assertEquals(listOf(null, null, "Danube", "Tiber"), gaps.map { it.hint })
    }

    @Test fun rendersGapsClosedAndOpen() {
        val text = "The capital is {{c1::Berlin::city}}, the river is {{Spree}}."
        assertEquals("The capital is **[ city ]**, the river is **[ … ]**.", Cloze.render(text, reveal = false))
        assertEquals("The capital is **Berlin**, the river is **Spree**.", Cloze.render(text, reveal = true))
    }

    @Test fun textWithoutGapsStaysUntouched() {
        val text = "A perfectly normal **card** with ![](image.png)"
        assertFalse(Cloze.hasGaps(text))
        assertEquals(text, Cloze.render(text, reveal = false))
        assertEquals(emptyList<String>(), Cloze.answers(text))
    }

    @Test fun insertGapWrapsTheSelectionOrOpensAnEmptyOne() {
        // the selected “Berlin” becomes a gap, the cursor ends up behind it
        assertEquals(
            "The capital is {{Berlin}}." to 25,
            Cloze.insertGap("The capital is Berlin.", 15, 21),
        )
        // without a selection an empty pair, with the cursor inside it
        assertEquals("ab{{}}c" to 4, Cloze.insertGap("abc", 2, 2))
        // selecting backwards is the same, and positions out of range are clamped
        assertEquals(Cloze.insertGap("abc", 1, 3), Cloze.insertGap("abc", 3, 1))
        assertEquals("abc{{}}" to 5, Cloze.insertGap("abc", 99, 99))
    }

    @Test fun collectsAnswersIgnoringEmptyGaps() {
        assertEquals(listOf("Ohm", "Volt"), Cloze.answers("{{Ohm}} {{}} {{c3::Volt}}"))
    }

    // ---------------- Input field ----------------

    @Test fun ignoresCaseSpacingAndPunctuation() {
        assertEquals(SelfGrade.CORRECT, AnswerCheck.grade("  the   Supreme Court! ", "The Supreme Court"))
        assertEquals(SelfGrade.CORRECT, AnswerCheck.grade("Größe – €", "größe €"))
    }

    @Test fun typoCountsAsPartial() {
        assertEquals(SelfGrade.PARTIAL, AnswerCheck.grade("Mississipi", "Mississippi"))
        assertEquals(SelfGrade.WRONG, AnswerCheck.grade("Missouri", "Mississippi"))
    }

    @Test fun shortAnswerToLongOneIsPartial() {
        assertEquals(SelfGrade.PARTIAL, AnswerCheck.grade("Berlin", "Berlin is the capital of Germany"))
    }

    @Test fun emptyInputIsWrongAndMissingExpectedLeavesItOpen() {
        assertEquals(SelfGrade.WRONG, AnswerCheck.grade("   ", "Berlin"))
        assertEquals(SelfGrade.PARTIAL, AnswerCheck.grade("Berlin", ""))
    }

    @Test fun alternativesCountAsCorrect() {
        assertEquals(SelfGrade.CORRECT, AnswerCheck.grade("H2O", "Water", listOf("H2O", "dihydrogen monoxide")))
    }

    @Test fun distanceCountsSwappedNeighboursAsOneTypo() {
        assertEquals(0, AnswerCheck.distance("abc", "abc"))
        assertEquals(3, AnswerCheck.distance("", "abc"))
        assertEquals(3, AnswerCheck.distance("abc", ""))
        assertEquals(1, AnswerCheck.distance("abdc", "abcd"))
        assertEquals(2, AnswerCheck.distance("Käse", "Hase"))
    }

    // ---------------- Buttons ----------------

    @Test fun buildsChoicesWithExactlyOneCorrect() {
        val choices = Choices.build("Berlin", "Munich | Hamburg | Cologne", seed = 42)
        assertEquals(4, choices.size)
        assertEquals(1, choices.count { it.correct })
        assertEquals("Berlin", choices.single { it.correct }.text)
        assertEquals(setOf("Berlin", "Munich", "Hamburg", "Cologne"), choices.map { it.text }.toSet())
    }

    @Test fun orderIsStableForTheSameCard() {
        val a = Choices.build("Berlin", "Munich|Hamburg|Cologne", seed = 7).map { it.text }
        val b = Choices.build("Berlin", "Munich|Hamburg|Cologne", seed = 7).map { it.text }
        assertEquals(a, b)
    }

    @Test fun dropsBlanksDuplicatesAndTheCorrectAnswerFromDistractors() {
        val choices = Choices.build("Berlin", " berlin | Munich ||  Munich ", seed = 1)
        assertEquals(2, choices.size)
        assertEquals(setOf("Berlin", "Munich"), choices.map { it.text }.toSet())
    }

    @Test fun splitTrimsAndKeepsOrder() {
        assertEquals(listOf("a", "b", "c"), Choices.split(" a | b |c| "))
        assertEquals(emptyList<String>(), Choices.split("  "))
    }

    // ---------------- Match ----------------

    @Test fun readsPairsWithEveryArrow() {
        val pairs = Matching.pairs("HTTP => 80 | HTTPS -> 443 | SSH → 22 | DNS = 53")
        assertEquals(listOf("HTTP", "HTTPS", "SSH", "DNS"), pairs.map { it.item })
        assertEquals(listOf("80", "443", "22", "53"), pairs.map { it.target })
    }

    @Test fun readsPairsFromSeparateLines() {
        assertEquals(3, Matching.pairs("Red => warm\nBlue => cold\nGreen => neutral").size)
    }

    @Test fun dropsSegmentsWithoutArrowOrWithEmptySide() {
        val pairs = Matching.pairs("A => 1 | just text | => 2 | B => | C => 3")
        assertEquals(listOf("A", "C"), pairs.map { it.item })
        assertFalse(Matching.hasPairs("A perfectly normal answer"))
    }

    @Test fun inferenceNeedsTwoClearArrows() {
        assertTrue(Matching.looksLikePairs("A => 1 | B => 2"))
        assertTrue(Matching.looksLikePairs("A -> 1\nB → 2"))
        // A single pair is not a match card, and `=` shows up too often in ordinary answers
        assertFalse(Matching.looksLikePairs("A => 1"))
        assertFalse(Matching.looksLikePairs("E = mc² | F = m · a"))
        assertFalse(Matching.looksLikePairs("Berlin is the capital"))
    }

    @Test fun buildMixesTargetsAndAddsDistractors() {
        val board = Matching.build("HTTP => 80 | SSH => 22", "443|25", seed = 5)
        assertEquals(listOf("HTTP", "SSH"), board.items)
        assertEquals(listOf("80", "22"), board.solution)
        assertEquals(setOf("80", "22", "443", "25"), board.targets.toSet())
    }

    @Test fun targetOrderIsStableForTheSameCard() {
        val a = Matching.build("A => 1 | B => 2", "3|4", seed = 9).targets
        val b = Matching.build("A => 1 | B => 2", "3|4", seed = 9).targets
        assertEquals(a, b)
    }

    @Test fun distractorsThatRepeatASolutionAreDropped() {
        val board = Matching.build("HTTP => 80 | SSH => 22", "80 | 443 | 443", seed = 1)
        assertEquals(3, board.targets.size)
        assertEquals(setOf("80", "22", "443"), board.targets.toSet())
    }

    @Test fun twoItemsMayShareOneTarget() {
        val board = Matching.build("HTTP => Port 80 | WWW => port 80", "", seed = 3)
        assertEquals(1, board.targets.size)
        assertTrue(board.isCorrect(0, "Port 80"))
        assertTrue(board.isCorrect(1, "Port 80"))
        assertEquals(SelfGrade.CORRECT, Matching.grade(board, mapOf(0 to 0, 1 to 0)))
    }

    @Test fun checksTargetTextNotPosition() {
        val board = Matching.build("A => Berlin", "", seed = 1)
        assertTrue(board.isCorrect(0, " berlin! "))
        assertFalse(board.isCorrect(0, "Bonn"))
    }

    @Test fun gradesAllSomeAndNoneCorrect() {
        val board = Matching.build("A => 1 | B => 2 | C => 3", "", seed = 2)
        fun pick(target: String) = board.targets.indexOf(target)
        val all = mapOf(0 to pick("1"), 1 to pick("2"), 2 to pick("3"))
        assertEquals(SelfGrade.CORRECT, Matching.grade(board, all))
        assertEquals(SelfGrade.PARTIAL, Matching.grade(board, all + (2 to pick("1"))))
        assertEquals(
            SelfGrade.WRONG,
            Matching.grade(board, mapOf(0 to pick("2"), 1 to pick("3"), 2 to pick("1"))),
        )
    }

    @Test fun missingAssignmentsCountAsWrong() {
        val board = Matching.build("A => 1 | B => 2", "", seed = 4)
        assertEquals(1, board.correctCount(mapOf(0 to board.targets.indexOf("1"))))
        assertEquals(SelfGrade.PARTIAL, Matching.grade(board, mapOf(0 to board.targets.indexOf("1"))))
        assertEquals(SelfGrade.WRONG, Matching.grade(board, emptyMap()))
    }

    @Test fun nextOpenItemSkipsAssignedOnesAndWrapsAround() {
        assertEquals(1, Matching.nextOpen(3, 0, mapOf(0 to 0)))
        assertEquals(2, Matching.nextOpen(3, 0, mapOf(0 to 0, 1 to 1)))
        // from the last term it wraps around to the front
        assertEquals(1, Matching.nextOpen(3, 2, mapOf(2 to 0, 0 to 1)))
        // everything taken: the term stays put and gets the next target
        assertEquals(1, Matching.nextOpen(3, 1, mapOf(0 to 0, 1 to 1, 2 to 2)))
        assertEquals(0, Matching.nextOpen(0, 0, emptyMap()))
    }

    @Test fun joinWritesTheCanonicalFormThatPairsReadsBack() {
        // This is what the card editor stores – and exactly how it looks in a CSV
        val pairs = listOf(Matching.MatchPair("HTTP", "80"), Matching.MatchPair("SSH", "22"))
        assertEquals("HTTP => 80 | SSH => 22", Matching.join(pairs))
        assertEquals(pairs, Matching.pairs(Matching.join(pairs)))
        assertEquals("", Matching.join(emptyList()))
    }

    @Test fun rendersPairsAsMarkdownList() {
        assertEquals(
            "- **HTTP** => 80\n- **SSH** => 22",
            Matching.render("HTTP -> 80 | SSH → 22"),
        )
        assertEquals("", Matching.render("An answer without pairs"))
    }
}

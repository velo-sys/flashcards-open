package de.flashcards.open.domain

import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.study.QueueCard
import de.flashcards.open.domain.study.StudySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionTest {
    private fun q(vararg ids: Long) = ids.map { QueueCard(it, 1, CardState.NEW, 0, it.toInt()) }

    @Test fun rateAdvancesAndCounts() {
        val s = StudySession<String>(q(1, 2, 3))
        s.rate("a")
        assertEquals(2L, s.current!!.id)
        assertEquals(1, s.reviewed)
        assertEquals(2, s.remaining)
    }

    @Test fun requeueAndUndoRestoresQueue() {
        val s = StudySession<String>(q(1, 2, 3))
        s.rate("vorher-1", requeue = q(1).single(), requeueAt = 2)
        assertEquals(4, s.size)
        assertEquals(2L, s.current!!.id)
        val step = s.undo() as StudySession.Step.Rated
        assertEquals("vorher-1", step.undo)
        assertEquals(3, s.size)
        assertEquals(1L, s.current!!.id)
        assertEquals(0, s.reviewed)
    }

    @Test fun skipMovesToEndAndUndoMovesBack() {
        val s = StudySession<String>(q(1, 2, 3))
        assertTrue(s.skip())
        assertEquals(2L, s.current!!.id)
        s.rate("2"); s.rate("3")
        assertEquals(1L, s.current!!.id) // the skipped card goes to the end
        s.undo(); s.undo(); s.undo()
        assertEquals(1L, s.current!!.id)
        s.rate("1")
        assertEquals(2L, s.current!!.id)
    }

    @Test fun skipLastCardIsNoOp() {
        val s = StudySession<String>(q(1))
        assertFalse(s.skip())
        assertEquals(1L, s.current!!.id)
        assertFalse(s.canUndo)
    }

    @Test fun undoFromFinished() {
        val s = StudySession<String>(q(1, 2))
        s.rate("1"); s.rate("2")
        assertTrue(s.finished)
        s.undo()
        assertFalse(s.finished)
        assertEquals(2L, s.current!!.id)
    }

    @Test fun mixedHistoryIsLifo() {
        val s = StudySession<String>(q(1, 2, 3, 4))
        s.rate("1", requeue = q(1).single(), requeueAt = 3) // 2,3,1,4 from index 1
        s.skip()                                           // 2 moves to the end
        s.rate("3", requeue = q(3).single(), requeueAt = 5)
        repeat(3) { s.undo() }
        assertEquals(0, s.index)
        assertEquals(4, s.size)
        assertEquals(1L, s.current!!.id)
        assertNull(StudySession<String>(q()).undo())
    }
}

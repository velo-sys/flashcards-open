package de.flashcards.open.domain

import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.DAY_MS
import de.flashcards.open.domain.scheduler.Intervals
import de.flashcards.open.domain.scheduler.MINUTE_MS
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.domain.scheduler.Sm2Scheduler
import de.flashcards.open.domain.scheduler.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import java.io.File
import kotlin.random.Random

class SchedulerTest {
    private val now = 1_700_000_000_000L
    private fun s(r: Rating, st: SrsState = SrsState()) = Sm2Scheduler.schedule(st, r, now)

    // ---------- State transitions, one by one ----------
    @Test fun newAgain() = assertEquals(SrsState(CardState.LEARN, now + MINUTE_MS, 0, 2.5, 1, 0, 0), s(Rating.AGAIN))
    @Test fun newHard() = assertEquals(SrsState(CardState.LEARN, now + 330_000, 0, 2.5, 1, 0, 0), s(Rating.HARD))
    @Test fun newGood() = assertEquals(SrsState(CardState.LEARN, now + 10 * MINUTE_MS, 0, 2.5, 1, 0, 1), s(Rating.GOOD))
    @Test fun newEasy() = assertEquals(SrsState(CardState.REVIEW, now + 4 * DAY_MS, 4, 2.5, 1, 0, 0), s(Rating.EASY))

    @Test fun learnLastStepGoodGraduates() {
        val learn = SrsState(CardState.LEARN, now, 0, 2.5, 1, 0, 1)
        assertEquals(SrsState(CardState.REVIEW, now + DAY_MS, 1, 2.5, 2, 0, 0), s(Rating.GOOD, learn))
        assertEquals(now + 10 * MINUTE_MS, s(Rating.HARD, learn).dueAt)
    }

    @Test fun reviewTransitions() {
        val review = SrsState(CardState.REVIEW, now, 10, 2.5, 5, 0, 0)
        assertEquals(SrsState(CardState.RELEARN, now + MINUTE_MS, 5, 2.3, 6, 1, 0), s(Rating.AGAIN, review))
        assertEquals(SrsState(CardState.REVIEW, now + 12 * DAY_MS, 12, 2.35, 6, 0, 0), s(Rating.HARD, review))
        assertEquals(SrsState(CardState.REVIEW, now + 25 * DAY_MS, 25, 2.5, 6, 0, 0), s(Rating.GOOD, review))
        val easy = s(Rating.EASY, review)
        assertEquals(2.65, easy.ease, 1e-9)
        assertEquals(34, easy.intervalDays) // round(10 * 2.65 * 1.3) = 34
    }

    @Test fun relearnKeepsInterval() {
        val relearn = SrsState(CardState.RELEARN, now, 5, 2.3, 6, 1, 1)
        assertEquals(5, s(Rating.GOOD, relearn).intervalDays)
        assertEquals(CardState.REVIEW, s(Rating.GOOD, relearn).state)
        assertEquals(5, s(Rating.EASY, relearn).intervalDays)
        assertEquals(CardState.RELEARN, s(Rating.AGAIN, relearn).state)
    }

    @Test fun easeNeverBelowMinimum() {
        var st = SrsState(CardState.REVIEW, now, 3, 1.35, 1, 0, 0)
        st = s(Rating.HARD, st)
        assertEquals(1.3, st.ease, 1e-9)
    }

    @Test fun intervalLabels() {
        assertEquals("1 min", Intervals.format(MINUTE_MS))
        assertEquals("10 min", Intervals.format(10 * MINUTE_MS))
        assertEquals("6 min", Intervals.format(330_000))
        assertEquals("2 h", Intervals.format(2 * 60 * MINUTE_MS))
        assertEquals("4 d", Intervals.format(4 * DAY_MS))
        assertEquals("1.5 mo", Intervals.format(45 * DAY_MS))
        assertEquals("2 y", Intervals.format(730 * DAY_MS))
    }

    // ---------- Cross-checked against schedule() in reference/flashcards.html ----------
    @Test fun identicalToReferenceImplementation() {
        val html = listOf("../reference/flashcards.html", "reference/flashcards.html").map(::File).first { it.exists() }.readText()
        val day = Regex("""const DAY = \d+, MIN = \d+;""").find(html)!!.value
        val start = html.indexOf("function newSRS()")
        val end = html.indexOf("function deckStats(")
        val js = day + "\n" + html.substring(start, end)

        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initStandardObjects()
            cx.evaluateString(scope, js, "reference", 1, null)
            val schedule = scope.get("schedule", scope) as org.mozilla.javascript.Function
            val newSrs = scope.get("newSRS", scope) as org.mozilla.javascript.Function

            val rnd = Random(42)
            var checked = 0
            repeat(400) {
                var jsState = newSrs.call(cx, scope, scope, emptyArray()) as Scriptable
                var kt = SrsState()
                var t = now
                for (k in 0 until 30) {
                    // realistic time spans (JS numbers lose precision above 2^53)
                    if (kt.intervalDays > 36_500) break
                    val rating = 1 + rnd.nextInt(4)
                    jsState = schedule.call(cx, scope, scope, arrayOf(jsState, rating, t.toDouble())) as Scriptable
                    kt = Sm2Scheduler.schedule(kt, Rating.of(rating), t)
                    assertSame(jsState as NativeObject, kt)
                    checked++
                    t = maxOf(t + MINUTE_MS, kt.dueAt) + rnd.nextInt(3) * DAY_MS
                }
            }
            assertTrue("checked: $checked", checked > 5_000)
        } finally {
            Context.exit()
        }
    }

    private fun assertSame(js: NativeObject, kt: SrsState) {
        fun num(k: String) = (js[k] as Number).toDouble()
        assertEquals(js["state"].toString().uppercase(), kt.state.name)
        assertEquals(num("due").toLong(), kt.dueAt)
        assertEquals(num("ivl").toInt(), kt.intervalDays)
        assertEquals(num("ease"), kt.ease, 1e-12)
        assertEquals(num("reps").toInt(), kt.reps)
        assertEquals(num("lapses").toInt(), kt.lapses)
        assertEquals(num("step").toInt(), kt.step)
    }
}

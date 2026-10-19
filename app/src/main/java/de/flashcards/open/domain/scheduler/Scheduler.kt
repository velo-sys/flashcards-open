package de.flashcards.open.domain.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class CardState { NEW, LEARN, REVIEW, RELEARN }

enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

data class SrsState(
    val state: CardState = CardState.NEW,
    val dueAt: Long = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
)

/** Pluggable scheduler (FSRS could slot in here later). */
interface Scheduler {
    fun schedule(s: SrsState, rating: Rating, now: Long): SrsState
}

const val MINUTE_MS = 60_000L
const val DAY_MS = 86_400_000L

/** SM-2 variant, identical to `schedule()` in reference/flashcards.html. */
object Sm2Scheduler : Scheduler {
    val LEARN_STEPS = longArrayOf(1 * MINUTE_MS, 10 * MINUTE_MS)
    const val START_EASE = 2.5
    const val MIN_EASE = 1.3

    override fun schedule(s: SrsState, rating: Rating, now: Long): SrsState {
        var n = s.copy(reps = s.reps + 1)
        if (n.state != CardState.REVIEW) {
            val learnState = if (n.state == CardState.NEW) CardState.LEARN else n.state
            n = when (rating) {
                Rating.AGAIN -> n.copy(step = 0, dueAt = now + LEARN_STEPS[0], state = learnState)
                Rating.HARD -> {
                    val cur = LEARN_STEPS[n.step]
                    val next = LEARN_STEPS.getOrElse(n.step + 1) { cur }
                    n.copy(dueAt = now + (cur + next) / 2, state = learnState)
                }
                Rating.GOOD -> if (n.step + 1 < LEARN_STEPS.size) {
                    val step = n.step + 1
                    n.copy(step = step, dueAt = now + LEARN_STEPS[step], state = learnState)
                } else {
                    val ivl = if (n.state == CardState.RELEARN) max(1, n.intervalDays) else 1
                    n.copy(intervalDays = ivl, state = CardState.REVIEW, dueAt = now + ivl * DAY_MS, step = 0)
                }
                Rating.EASY -> {
                    val ivl = if (n.state == CardState.RELEARN) max(2, n.intervalDays) else 4
                    n.copy(intervalDays = ivl, state = CardState.REVIEW, dueAt = now + ivl * DAY_MS, step = 0)
                }
            }
            return n
        }
        // review
        return when (rating) {
            Rating.AGAIN -> n.copy(
                lapses = n.lapses + 1,
                ease = max(MIN_EASE, n.ease - 0.2),
                intervalDays = max(1, (n.intervalDays * 0.5).roundToInt()),
                state = CardState.RELEARN,
                step = 0,
                dueAt = now + LEARN_STEPS[0],
            )
            Rating.HARD -> {
                val ivl = max(n.intervalDays + 1, (n.intervalDays * 1.2).roundToInt())
                n.copy(ease = max(MIN_EASE, n.ease - 0.15), intervalDays = ivl, dueAt = now + ivl * DAY_MS)
            }
            Rating.GOOD -> {
                val ivl = max(n.intervalDays + 1, (n.intervalDays * n.ease).roundToInt())
                n.copy(intervalDays = ivl, dueAt = now + ivl * DAY_MS)
            }
            Rating.EASY -> {
                val ease = n.ease + 0.15
                val ivl = max(n.intervalDays + 2, (n.intervalDays * ease * 1.3).roundToInt())
                n.copy(ease = ease, intervalDays = ivl, dueAt = now + ivl * DAY_MS)
            }
        }
    }
}

object Intervals {
    /** “10 min”, “4 d” … like `fmtIvl()` in the reference prototype. */
    fun format(ms: Long): String {
        if (ms < 60 * MINUTE_MS) return "${max(1L, (ms.toDouble() / MINUTE_MS).roundToLong())} min"
        if (ms < DAY_MS) return "${(ms.toDouble() / (60 * MINUTE_MS)).roundToLong()} h"
        val d = ms.toDouble() / DAY_MS
        if (d < 30) return "${d.roundToLong()} d"
        if (d < 365) return "${oneDecimal(d / 30)} mo"
        return "${oneDecimal(d / 365)} y"
    }

    private fun oneDecimal(v: Double): String {
        val r = (v * 10).roundToLong()
        return if (r % 10 == 0L) "${r / 10}" else "${r / 10}.${r % 10}"
    }
}

object DayBounds {
    fun startOfDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfTomorrow(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}

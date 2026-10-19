package de.flashcards.open.domain.study

/**
 * The queue of a study session: grade, skip, undo.
 *
 * The session only knows positions in the queue. What a grade means for the database is the
 * caller's business: when grading it stores a payload of type [T] (e.g. the card as it was before
 * the answer) and gets it back from [undo] to roll its own change back.
 *
 * The history is a stack, [undo] always reverts the most recent step (LIFO).
 */
class StudySession<T>(initial: List<QueueCard> = emptyList()) {

    private val queue = ArrayList(initial)
    private val history = ArrayList<Step>()

    /** One step in the session's history. */
    sealed interface Step {
        /**
         * A graded card. [undo] is the caller's payload (the session's type [T]), [requeuedAt] the
         * position the card was put back into.
         */
        data class Rated(val undo: Any?, val requeuedAt: Int? = null) : Step

        /** A skipped card; before being skipped it sat at position [from]. */
        data class Skipped(val from: Int) : Step
    }

    /** Position of the current card in the queue. */
    var index: Int = 0
        private set

    /** Number of grades in this session (skips excluded, goes down again after [undo]). */
    var reviewed: Int = 0
        private set

    val size: Int get() = queue.size
    val current: QueueCard? get() = queue.getOrNull(index)
    val remaining: Int get() = (size - index).coerceAtLeast(0)
    val finished: Boolean get() = index >= size
    val canUndo: Boolean get() = history.isNotEmpty()

    /**
     * Grades the current card and moves on by one position.
     *
     * @param undo payload that lets the caller take the grade back.
     * @param requeue card to put back into the queue (learning cards that fall due again today).
     * @param requeueAt target position of the requeued card; the end if omitted.
     */
    fun rate(undo: T, requeue: QueueCard? = null, requeueAt: Int? = null) {
        if (finished) return
        var at: Int? = null
        if (requeue != null) {
            at = (requeueAt ?: size).coerceIn(index + 1, size)
            queue.add(at, requeue)
        }
        index++
        reviewed++
        history += Step.Rated(undo, at)
    }

    /**
     * Moves the current card to the end of the queue without grading it. Returns `false` if there
     * is no other card to switch to.
     */
    fun skip(): Boolean {
        if (remaining <= 1) return false
        val card = queue.removeAt(index)
        queue.add(card)
        history += Step.Skipped(index)
        return true
    }

    /**
     * Reverts the most recent step and returns it, or `null` if the history is empty. For
     * [Step.Rated] the caller has to roll its own change back using `undo`.
     */
    fun undo(): Step? {
        val step = history.removeLastOrNull() ?: return null
        when (step) {
            is Step.Rated -> {
                step.requeuedAt?.let { queue.removeAt(it) }
                index--
                reviewed--
            }
            is Step.Skipped -> {
                val card = queue.removeAt(size - 1)
                queue.add(step.from, card)
                index = step.from
            }
        }
        return step
    }
}

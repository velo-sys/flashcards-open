package de.flashcards.open.ui.study

import androidx.lifecycle.ViewModel
import de.flashcards.open.AppContainer
import de.flashcards.open.data.db.Card
import de.flashcards.open.data.db.Deck
import de.flashcards.open.data.db.Topic
import de.flashcards.open.domain.card.AnswerCheck
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Choices
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.card.SelfGrade
import de.flashcards.open.domain.scheduler.Intervals
import de.flashcards.open.domain.scheduler.Rating
import de.flashcards.open.domain.scheduler.Sm2Scheduler
import de.flashcards.open.domain.study.MixMode
import de.flashcards.open.domain.study.QueueCard
import de.flashcards.open.domain.study.StudyQueue
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.nav.StudyRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class StudyUi(
    val loading: Boolean = true,
    val cram: Boolean = false,
    val title: String = "",
    val card: Card? = null,
    val deck: Deck? = null,
    val topic: Topic? = null,
    val flipped: Boolean = false,
    /** Preview of the next intervals for Again/Hard/Good/Easy */
    val intervals: List<String> = emptyList(),
    val position: Int = 0,
    val queueSize: Int = 0,
    val reviewed: Int = 0,
    val finished: Boolean = false,
    val empty: Boolean = false,
    val multiDeck: Boolean = false,
    val type: CardType = CardType.BASIC,
    /** The front as it is currently shown (cloze closed or revealed). */
    val front: String = "",
    /** The options of a choice card, shuffled stably. */
    val choices: List<Choices.Choice> = emptyList(),
    /** Index of the option that was tapped. */
    val chosen: Int? = null,
    /** The answer typed into an input card. */
    val input: String = "",
    /** Terms and shuffled targets of a match card. */
    val board: Matching.Board? = null,
    /** The user's assignment: term index → target index in [Matching.Board.targets]. */
    val assignment: Map<Int, Int> = emptyMap(),
    /** The term that gets a target next. */
    val activeItem: Int = 0,
    /** The suggestion from the automatic check; the user can overrule it. */
    val suggestion: SelfGrade? = null,
) {
    val remaining get() = (queueSize - position).coerceAtLeast(0)
    val progress get() = if (queueSize == 0) 0f else position.toFloat() / queueSize

    /** Cards that take an answer are graded on three levels: correct / partly / wrong. */
    val threeLevel get() = type.threeLevel

    /** On choice and match cards the back is already part of the answer area. */
    val showBack get() = flipped && type != CardType.CHOICE && type != CardType.MATCH &&
        card?.backMd?.isNotBlank() == true

    /** Every term has a target – from here on Ⓐ checks the whole card. */
    val matchReady get() = board != null && board.size > 0 && assignment.size >= board.size

    /** Correctly matched terms, after revealing. */
    val matchCorrect get() = board?.correctCount(assignment) ?: 0
}

class StudyViewModel(private val c: AppContainer, private val route: StudyRoute) : ViewModel() {
    private val _ui = MutableStateFlow(StudyUi(cram = route.cram, title = route.title))
    val ui: StateFlow<StudyUi> = _ui

    private val queue = ArrayList<QueueCard>()
    private var index = 0
    private var decks: Map<Long, Deck> = emptyMap()
    private var topics: Map<Long, Topic> = emptyMap()
    private var busy = false

    init {
        launchSafe(c.messenger) {
            val ids = route.ids()
            decks = c.db.deckDao().byIds(ids).associateBy { it.id }
            topics = c.repo.allTopics().associateBy { it.id }
            val mode = route.mode.takeIf { it.isNotEmpty() }?.let { runCatching { MixMode.valueOf(it) }.getOrNull() }
                ?: c.settings.current().mixMode
            queue += if (route.cram) {
                c.repo.cramQueue(ids, route.tag.ifBlank { null })
            } else {
                c.repo.buildQueue(ids, mode, route.newLimit.takeIf { it >= 0 })
            }
            if (queue.isEmpty()) {
                _ui.update { it.copy(loading = false, empty = true) }
            } else {
                _ui.update { it.copy(queueSize = queue.size, multiDeck = decks.size > 1) }
                showCurrent()
            }
        }
    }

    private suspend fun showCurrent() {
        while (index < queue.size) {
            val card = c.repo.card(queue[index].id)
            if (card != null) {
                val deck = decks[card.deckId]
                val board = if (card.type == CardType.MATCH) {
                    Matching.build(card.backMd, card.options, seed = card.id)
                } else {
                    null
                }
                // A match card without pairs cannot be asked – as on import it becomes a basic card
                val type = if (board != null && board.size == 0) CardType.BASIC else card.type
                _ui.update {
                    it.copy(
                        loading = false, card = card, deck = deck, topic = deck?.let { d -> topics[d.topicId] },
                        flipped = false, intervals = emptyList(), position = index, queueSize = queue.size,
                        type = type,
                        front = front(card, flipped = false),
                        choices = if (card.type == CardType.CHOICE) {
                            Choices.build(card.backMd, card.options, seed = card.id)
                        } else {
                            emptyList()
                        },
                        board = board.takeIf { type == CardType.MATCH },
                        assignment = emptyMap(), activeItem = 0,
                        chosen = null, input = "", suggestion = null,
                    )
                }
                return
            }
            index++ // the card has been deleted meanwhile
        }
        _ui.update { it.copy(loading = false, card = null, finished = true, position = queue.size, queueSize = queue.size) }
    }

    /** Cloze cards show their gaps closed or revealed, every other type shows the front. */
    private fun front(card: Card, flipped: Boolean): String =
        if (card.type == CardType.CLOZE) Cloze.render(card.frontMd, flipped) else card.frontMd

    /** Reveals the answer. [suggestion] is what the automatic check proposes. */
    private fun reveal(suggestion: SelfGrade?, chosen: Int? = null) {
        val s = _ui.value
        val card = s.card ?: return
        if (s.flipped) return
        val now = System.currentTimeMillis()
        val intervals = if (route.cram) emptyList() else Rating.entries.map { r ->
            Intervals.format(Sm2Scheduler.schedule(card.srs, r, now).dueAt - now)
        }
        _ui.update {
            it.copy(
                flipped = true,
                intervals = intervals,
                front = front(card, flipped = true),
                suggestion = suggestion,
                chosen = chosen ?: it.chosen,
            )
        }
    }

    /** Flip or give up: without any input the user grades themselves. */
    fun flip() {
        val s = _ui.value
        // Choice and match cards are answered, not flipped
        if (!s.flipped && (s.type == CardType.CHOICE || s.type == CardType.MATCH)) return
        reveal(suggestion = null)
    }

    fun onInput(text: String) {
        if (_ui.value.flipped) return
        _ui.update { it.copy(input = text) }
    }

    /**
     * Check the answer: the typed text, or the whole assignment. Entering nothing counts as giving
     * up – there is no suggestion then and the user grades themselves.
     */
    fun check() {
        val s = _ui.value
        val card = s.card ?: return
        if (s.flipped) return
        val suggestion = when {
            s.type == CardType.MATCH ->
                s.board?.takeIf { s.assignment.isNotEmpty() }?.let { Matching.grade(it, s.assignment) }
            s.input.isBlank() -> null
            else -> AnswerCheck.grade(s.input, card.backMd, Choices.split(card.options))
        }
        reveal(suggestion)
    }

    /** Pick the term of a match card that gets a target next. */
    fun selectItem(itemIndex: Int) {
        val s = _ui.value
        val board = s.board ?: return
        if (s.flipped || itemIndex !in board.items.indices) return
        _ui.update { it.copy(activeItem = itemIndex) }
    }

    /**
     * Assign a target to the active term and jump to the next term that is still open.
     * A target may be used more than once – even the solution can reuse the same target.
     */
    fun assign(targetIndex: Int) {
        val s = _ui.value
        val board = s.board ?: return
        if (s.flipped || targetIndex !in board.targets.indices || board.size == 0) return
        val item = s.activeItem.coerceIn(0, board.size - 1)
        val assignment = s.assignment + (item to targetIndex)
        _ui.update { it.copy(assignment = assignment, activeItem = Matching.nextOpen(board.size, item, assignment)) }
    }

    /** Pick an option of a choice card. */
    fun choose(choiceIndex: Int) {
        val s = _ui.value
        if (s.flipped) return
        val choice = s.choices.getOrNull(choiceIndex) ?: return
        reveal(if (choice.correct) SelfGrade.CORRECT else SelfGrade.WRONG, chosen = choiceIndex)
    }

    fun rate(rating: Rating) {
        val s = _ui.value
        val card = s.card ?: return
        if (!s.flipped || busy) return
        busy = true
        launchSafe(c.messenger) {
            try {
                val qc = queue[index]
                if (route.cram) {
                    if (rating == Rating.AGAIN) queue.add(StudyQueue.cramRequeuePosition(index, queue.size), qc)
                } else {
                    val updated = c.repo.answer(card, rating)
                    // Requeue learning cards that fall due again today
                    if (c.repo.isLearning(updated) && updated.dueAt < c.repo.today().end) {
                        queue.add(StudyQueue.requeuePosition(index, queue.size, rating), qc.copy(state = updated.state, dueAt = updated.dueAt))
                    }
                }
                index++
                _ui.update { it.copy(reviewed = it.reviewed + 1) }
                showCurrent()
            } finally {
                busy = false
            }
        }
    }
}

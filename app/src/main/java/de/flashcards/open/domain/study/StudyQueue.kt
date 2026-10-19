package de.flashcards.open.domain.study

import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.Rating
import kotlin.random.Random

enum class MixMode { MIXED, BY_DECK }

/** The minimum card data needed to build the queue. */
data class QueueCard(
    val id: Long,
    val deckId: Long,
    val state: CardState,
    val dueAt: Long,
    val orderIndex: Int,
)

object StudyQueue {

    /**
     * Builds the study queue across one or more decks.
     * Order: learning cards first (by due date), then reviews and new cards interleaved
     * (2 reviews : 1 new, as in the reference prototype).
     *
     * @param deckOrder order of the decks (for “one deck after another”)
     * @param newRemaining new cards still allowed today, per deck
     * @param sessionNewLimit optional overall cap on new cards for this session
     */
    fun build(
        learn: List<QueueCard>,
        review: List<QueueCard>,
        new: List<QueueCard>,
        deckOrder: List<Long>,
        newRemaining: Map<Long, Int>,
        sessionNewLimit: Int?,
        mode: MixMode,
        random: Random = Random.Default,
    ): List<QueueCard> {
        val newByDeck = new.groupBy { it.deckId }.mapValues { (deckId, cards) ->
            cards.sortedBy { it.orderIndex }.take((newRemaining[deckId] ?: 0).coerceAtLeast(0))
        }
        // Session cap: take from the decks in turn so every deck is represented
        val allowedNew: Map<Long, List<QueueCard>> = if (sessionNewLimit == null) newByDeck else {
            val taken = HashMap<Long, MutableList<QueueCard>>()
            val iters = deckOrder.mapNotNull { d -> newByDeck[d]?.let { d to it.iterator() } }
            var left = sessionNewLimit.coerceAtLeast(0)
            var progress = true
            while (left > 0 && progress) {
                progress = false
                for ((d, it) in iters) {
                    if (left == 0) break
                    if (it.hasNext()) {
                        taken.getOrPut(d) { ArrayList() }.add(it.next()); left--; progress = true
                    }
                }
            }
            taken
        }

        val queue = ArrayList<QueueCard>(learn.size + review.size + allowedNew.values.sumOf { it.size })
        queue += learn.sortedBy { it.dueAt }

        when (mode) {
            MixMode.MIXED -> {
                val reviews = review.shuffled(random)
                val news = roundRobin(deckOrder.mapNotNull { allowedNew[it] })
                interleave(reviews, news, queue)
            }
            MixMode.BY_DECK -> {
                val reviewsByDeck = review.groupBy { it.deckId }
                for (d in deckOrder) {
                    interleave(reviewsByDeck[d].orEmpty().shuffled(random), allowedNew[d].orEmpty(), queue)
                }
            }
        }
        return queue
    }

    private fun interleave(reviews: List<QueueCard>, news: List<QueueCard>, out: MutableList<QueueCard>) {
        var r = 0
        var n = 0
        while (r < reviews.size || n < news.size) {
            if (r < reviews.size) out += reviews[r++]
            if (r < reviews.size) out += reviews[r++]
            if (n < news.size) out += news[n++]
        }
    }

    private fun <T> roundRobin(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) if (i < l.size) out += l[i]
        return out
    }

    /** Where a learning card that is due again today gets requeued (as in the reference). */
    fun requeuePosition(currentIndex: Int, queueSize: Int, rating: Rating): Int =
        minOf(queueSize, currentIndex + 1 + if (rating == Rating.AGAIN) 3 else 6)

    /** Cram mode: “Again” comes back 4 cards later. */
    fun cramRequeuePosition(currentIndex: Int, queueSize: Int): Int = minOf(queueSize, currentIndex + 4)
}

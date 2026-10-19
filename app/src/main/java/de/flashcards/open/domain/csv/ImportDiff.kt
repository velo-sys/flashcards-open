package de.flashcards.open.domain.csv

import de.flashcards.open.domain.card.CardType

data class ExistingCardRef(
    val id: Long,
    val contentKey: String,
    val back: String,
    val tags: String,
    val type: CardType = CardType.BASIC,
    val options: String = "",
)

data class IncomingCard(val contentKey: String, val card: CsvCard, val orderIndex: Int)

data class ImportDiff(
    val toInsert: List<IncomingCard>,
    /** existing card ID → new content (study progress is kept) */
    val toUpdate: List<Pair<Long, IncomingCard>>,
    val toDelete: List<Long>,
    val changed: Int,
) {
    val added get() = toInsert.size
    val removed get() = toDelete.size
}

object ImportDiffer {
    fun diff(existing: List<ExistingCardRef>, cards: List<CsvCard>): ImportDiff {
        val keys = ContentKey.assign(cards.map { it.front })
        val incoming = cards.mapIndexed { i, c -> IncomingCard(keys[i], c, i) }
        val byKey = existing.associateBy { it.contentKey }
        val toInsert = ArrayList<IncomingCard>()
        val toUpdate = ArrayList<Pair<Long, IncomingCard>>()
        var changed = 0
        for (inc in incoming) {
            val old = byKey[inc.contentKey]
            if (old == null) {
                toInsert += inc
            } else {
                val differs = old.back != inc.card.back || old.tags != inc.card.tags ||
                    old.type != inc.card.type || old.options != inc.card.options
                if (differs) changed++
                toUpdate += old.id to inc
            }
        }
        val keep = incoming.mapTo(HashSet()) { it.contentKey }
        val toDelete = existing.filter { it.contentKey !in keep }.map { it.id }
        return ImportDiff(toInsert, toUpdate, toDelete, changed)
    }
}

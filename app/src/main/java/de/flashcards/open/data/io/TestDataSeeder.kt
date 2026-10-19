package de.flashcards.open.data.io

import androidx.room.withTransaction
import de.flashcards.open.data.db.AppDatabase
import de.flashcards.open.data.db.Card
import de.flashcards.open.data.db.Deck
import de.flashcards.open.data.db.Topic
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.csv.ContentKey

/** Generates test data (by default 10 topics × 20 decks × 100 cards). */
object TestDataSeeder {
    private val EMOJIS = listOf("🌐", "📐", "🧪", "🇬🇧", "💻", "📜", "🎵", "🧬", "⚖️", "🗺️")

    suspend fun seed(db: AppDatabase, topics: Int = 10, decksPerTopic: Int = 20, cardsPerDeck: Int = 100) = db.withTransaction {
        val startIndex = db.topicDao().nextSortIndex()
        val existingTest = db.topicDao().all().count { it.name.startsWith("Test topic ") }
        for (t in 0 until topics) {
            val topicId = db.topicDao().insert(
                Topic(name = "Test topic ${existingTest + t + 1}", colorArgb = TopicColors.forIndex(startIndex + t), emoji = EMOJIS[t % EMOJIS.size], sortIndex = startIndex + t),
            )
            for (d in 0 until decksPerTopic) {
                val deckId = db.deckDao().insert(Deck(topicId = topicId, name = "Deck ${d + 1}", sortIndex = d))
                db.cardDao().insertAll(
                    (0 until cardsPerDeck).map { i ->
                        val card = sample(i, t, d)
                        Card(
                            deckId = deckId, frontMd = card.front, backMd = card.back, options = card.options,
                            type = card.type, tags = if (i % 2 == 0) "even" else "odd",
                            orderIndex = i, contentKey = ContentKey.of(card.front),
                        )
                    },
                )
            }
        }
    }

    private data class Sample(val front: String, val back: String, val type: CardType, val options: String = "")

    /** Every eighth card is a different type so the test data covers every rendering. */
    private fun sample(i: Int, t: Int, d: Int): Sample {
        val origin = "**T${t + 1}/D${d + 1}**"
        return when (i % 8) {
            1 -> Sample("Cloze ${i + 1}: Resistance is measured in {{c1::ohm::unit}}. $origin", "Ω – the unit of resistance", CardType.CLOZE)
            3 -> Sample("Input ${i + 1}: Capital of France? $origin", "Paris", CardType.INPUT, "Paris, France")
            5 -> Sample("Choice ${i + 1}: Which device works on OSI layer 3? $origin", "Router", CardType.CHOICE, "Switch|Hub|Repeater")
            7 -> Sample(
                "Match ${i + 1}: Match the protocols to their ports. $origin",
                "HTTP => 80 | HTTPS => 443 | SSH => 22 | DNS => 53",
                CardType.MATCH,
                "25|3389",
            )
            // Non-ASCII on purpose: this card doubles as a rendering and encoding check
            else -> Sample("Question ${i + 1} – size über café (€) $origin", "Answer ${i + 1}\n\n- item ä\n- item ß", CardType.BASIC)
        }
    }
}

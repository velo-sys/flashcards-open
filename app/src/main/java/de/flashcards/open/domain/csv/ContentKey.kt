package de.flashcards.open.domain.csv

import de.flashcards.open.domain.text.TextDecoding
import java.security.MessageDigest

/** A card's key for re-import: hash of the (normalised) front. */
object ContentKey {
    fun of(front: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(TextDecoding.nfc(front.trim()).toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    /** Hands out unique keys; duplicate fronts get a suffix (#2, #3 …). */
    fun assign(fronts: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return fronts.map { f ->
            val base = of(f)
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            if (n == 1) base else "$base#$n"
        }
    }
}

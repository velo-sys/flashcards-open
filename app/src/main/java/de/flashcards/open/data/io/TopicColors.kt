package de.flashcards.open.data.io

/** The palette of topic colors (ARGB). */
object TopicColors {
    val PALETTE = listOf(
        0xFF3B6FE0.toInt(), 0xFF2F9E5B.toInt(), 0xFFD98A1C.toInt(), 0xFFD64545.toInt(), 0xFF8E5BD6.toInt(),
        0xFF1FA2B5.toInt(), 0xFFC2457A.toInt(), 0xFF6B8E23.toInt(), 0xFF8D6E63.toInt(), 0xFF546E7A.toInt(),
        0xFFE0663B.toInt(), 0xFF3BA0E0.toInt(),
    )

    fun forIndex(i: Int) = PALETTE[Math.floorMod(i, PALETTE.size)]
}

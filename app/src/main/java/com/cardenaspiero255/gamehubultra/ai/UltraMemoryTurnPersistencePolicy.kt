package com.cardenaspiero255.gamehubultra.ai

object UltraMemoryTurnPersistencePolicy {
    fun shouldPersist(previous: List<String>, next: List<String>): Boolean {
        if (next.isEmpty()) return false
        val latestUserMessage = next.asReversed()
            .firstOrNull { it.startsWith("Tú:", ignoreCase = true) || it.startsWith("Tu:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        return UltraMemoryCommandParser.parse(latestUserMessage) == null
    }
}

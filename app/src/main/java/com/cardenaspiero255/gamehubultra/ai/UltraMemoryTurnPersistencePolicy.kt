package com.cardenaspiero255.gamehubultra.ai

object UltraMemoryTurnPersistencePolicy {
    fun shouldPersist(previous: List<String>, next: List<String>): Boolean {
        if (next.isEmpty()) return false
        val latestUserMessage = next.asReversed()
            .firstOrNull { it.startsWith("Tú:", ignoreCase = true) || it.startsWith("Tu:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        if (latestUserMessage.isBlank()) return false
        return UltraMemoryCommandParser.parse(latestUserMessage) == null
    }

    fun resetsConversationContext(message: String): Boolean =
        resetsConversationContext(UltraMemoryCommandParser.parse(message))

    fun resetsConversationContext(command: UltraMemoryCommand?): Boolean =
        when (command) {
            is UltraMemoryCommand.Forget,
            is UltraMemoryCommand.Delete,
            is UltraMemoryCommand.Archive,
            UltraMemoryCommand.ClearHistory,
            UltraMemoryCommand.ClearAll -> true

            else -> false
        }
}

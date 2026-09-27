package com.cardenaspiero255.gamehubultra.ai

/**
 * Gives typed Ultra chat the same contextual research decision used by voice.
 * The caller still supplies local chat as the fallback through
 * [UltraProductionQueryExecutor].
 */
object UltraTypedChatRoutePlanner {
    fun route(
        message: String,
        conversationHistory: List<String> = emptyList()
    ): UltraAgentRoute {
        val clean = message.trim()
        return UltraAgentRoute.Chat(
            message = clean,
            query = UltraContextualQueryPlanner.plan(
                message = clean,
                conversationHistory = conversationHistory
            )
        )
    }
}

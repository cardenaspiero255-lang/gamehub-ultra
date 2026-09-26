package com.cardenaspiero255.gamehubultra.ai

/**
 * Single production entry point for general chat queries.
 *
 * Until a trusted online provider is configured, changing/current information
 * safely abstains instead of falling back to potentially stale local-model
 * knowledge. Stable questions continue through the local fast path.
 */
object UltraProductionQueryExecutor {
    private val verifiedResearchEngine = UltraVerifiedResearchEngine(
        providers = emptyList()
    )
    private val coordinator = UltraQueryExecutionCoordinator(
        researchEngine = verifiedResearchEngine
    )

    fun answer(
        route: UltraAgentRoute.Chat,
        localChat: () -> String
    ): String {
        val request = route.query ?: return localChat()
        return coordinator.answer(
            request = request,
            localChat = localChat
        ).message
    }
}

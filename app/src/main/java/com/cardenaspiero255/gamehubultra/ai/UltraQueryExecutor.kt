package com.cardenaspiero255.gamehubultra.ai

/**
 * Boundary used by UI and voice layers to execute Ultra chat queries without
 * depending on how production research providers are assembled.
 */
interface UltraQueryExecutor {
    fun answer(
        route: UltraAgentRoute.Chat,
        stableKnowledgeFallback: (() -> String?)? = null,
        localChat: () -> String
    ): String
}

/**
 * Default query policy implementation. It owns only routing/fallback behavior;
 * provider construction belongs to the production composition layer.
 */
class DefaultUltraQueryExecutor(
    private val coordinator: UltraQueryExecutionCoordinator
) : UltraQueryExecutor {

    override fun answer(
        route: UltraAgentRoute.Chat,
        stableKnowledgeFallback: (() -> String?)?,
        localChat: () -> String
    ): String {
        val request = route.query ?: return localChat()
        val fallback =
            if (request.requiresInternet) {
                stableKnowledgeFallback ?: { localChat() }
            } else {
                { localChat() }
            }

        return coordinator.answer(
            request = request,
            localChat = fallback
        ).message
    }
}

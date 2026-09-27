package com.cardenaspiero255.gamehubultra.ai

data class UltraGeneralResearchDecision(
    val handled: Boolean,
    val result: UltraVerifiedResearchResult? = null
)

/**
 * Decides whether a chat turn must be handled by verified online research.
 * Stable/offline-safe questions remain available to the local chat path.
 */
class UltraGeneralResearchCoordinator(
    private val engine: UltraVerifiedResearchEngine
) : AutoCloseable {

    fun answer(route: UltraAgentRoute.Chat): UltraGeneralResearchDecision {
        val request = route.query ?: return UltraGeneralResearchDecision(
            handled = false
        )

        if (!request.requiresInternet) {
            return UltraGeneralResearchDecision(
                handled = false
            )
        }

        return UltraGeneralResearchDecision(
            handled = true,
            result = engine.answer(request)
        )
    }

    override fun close() {
        engine.close()
    }
}

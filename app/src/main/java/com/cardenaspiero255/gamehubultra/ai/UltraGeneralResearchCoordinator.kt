package com.cardenaspiero255.gamehubultra.ai

data class UltraGeneralResearchDecision(
    val handled: Boolean,
    val result: UltraVerifiedResearchResult? = null
)

/**
 * Handles only queries whose correctness requires verified online data.
 * OPTIONAL stable knowledge stays available to local chat and can be escalated
 * by [DefaultUltraQueryExecutor] if local knowledge cannot answer.
 */
class UltraGeneralResearchCoordinator(
    private val researchGateway: UltraResearchGateway
) : AutoCloseable {

    fun answer(route: UltraAgentRoute.Chat): UltraGeneralResearchDecision {
        val request = route.query ?: return UltraGeneralResearchDecision(
            handled = false
        )

        if (request.verificationMode != UltraVerificationMode.REQUIRED) {
            return UltraGeneralResearchDecision(
                handled = false
            )
        }

        return UltraGeneralResearchDecision(
            handled = true,
            result = researchGateway.answer(request)
        )
    }

    override fun close() = Unit
}

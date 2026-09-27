package com.cardenaspiero255.gamehubultra.ai

data class UltraQueryExecutionAnswer(
    val message: String,
    val verified: Boolean,
    val confidence: UltraAnswerConfidence? = null,
    val sources: List<String> = emptyList(),
    val fromCache: Boolean = false,
    val timedOut: Boolean = false,
    val fallbackUsed: Boolean = false,
    val abstained: Boolean = false
)

/**
 * Keeps stable/general knowledge on the local path and routes changing data
 * through the verified research engine. Online failures never fall back to an
 * unverified local answer that could present stale information as current.
 */
class UltraQueryExecutionCoordinator(
    private val researchEngine: UltraVerifiedResearchEngine
) {
    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String
    ): UltraQueryExecutionAnswer {
        if (!request.requiresInternet) {
            return UltraQueryExecutionAnswer(
                message = localChat(),
                verified = false
            )
        }

        val research = researchEngine.answer(request)
        val canUseLocalStableFallback =
            request.kind == UltraGeneralQueryKind.GENERAL_KNOWLEDGE &&
                !request.requiresFreshData &&
                research.abstained &&
                research.sources.isEmpty() &&
                !research.sensitiveInputBlocked

        if (canUseLocalStableFallback) {
            return UltraQueryExecutionAnswer(
                message = localChat(),
                verified = false,
                confidence = null,
                sources = research.sources,
                fromCache = research.fromCache,
                timedOut = research.timedOut,
                fallbackUsed = true,
                abstained = false
            )
        }

        return UltraQueryExecutionAnswer(
            message = research.message,
            verified = !research.abstained &&
                research.confidence != UltraAnswerConfidence.LOW,
            confidence = research.confidence,
            sources = research.sources,
            fromCache = research.fromCache,
            timedOut = research.timedOut,
            fallbackUsed = research.fallbackUsed,
            abstained = research.abstained
        )
    }
}

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
    private fun safeLocalAnswer(localChat: () -> String?): String? =
        try {
            localChat()?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val requiresVerifiedResearch =
            request.requiresInternet ||
                request.requiresFreshData ||
                request.kind == UltraGeneralQueryKind.CURRENT_DATA ||
                request.kind == UltraGeneralQueryKind.COMPARISON_RESEARCH

        if (!requiresVerifiedResearch) {
            val localAnswer = safeLocalAnswer(localChat)
            return if (localAnswer != null) {
                UltraQueryExecutionAnswer(
                    message = localAnswer,
                    verified = false
                )
            } else {
                UltraQueryExecutionAnswer(
                    message = "No pude responder eso con una fuente local disponible.",
                    verified = false,
                    abstained = true
                )
            }
        }

        val research = researchEngine.answer(request)
        val canUseLocalStableFallback =
            request.kind == UltraGeneralQueryKind.GENERAL_KNOWLEDGE &&
                !request.requiresFreshData &&
                research.abstained &&
                research.sources.isEmpty() &&
                !research.sensitiveInputBlocked

        if (canUseLocalStableFallback) {
            val localAnswer = safeLocalAnswer(localChat)
            if (localAnswer != null) {
                return UltraQueryExecutionAnswer(
                    message = localAnswer,
                    verified = false,
                    confidence = null,
                    sources = research.sources,
                    fromCache = research.fromCache,
                    timedOut = research.timedOut,
                    fallbackUsed = true,
                    abstained = false
                )
            }
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
    private fun safeLocalAnswer(localChat: () -> String?): String? =
        try {
            localChat()?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

}

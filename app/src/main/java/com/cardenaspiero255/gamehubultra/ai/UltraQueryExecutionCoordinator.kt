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

object UltraLocalChatQualityPolicy {
    private val capabilityFallbackMarkers = listOf(
        "chat local puede estar limitado",
        "local chat may be limited",
        "puedo hablar contigo sobre rendimiento, fps, temperatura, batería, red",
        "i can talk with you about performance, fps, temperature, battery, networking"
    )

    fun shouldUseResearchFallback(answer: String): Boolean {
        val normalized = answer
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
        return normalized.isBlank() ||
            capabilityFallbackMarkers.any(normalized::contains)
    }
}

/**
 * Stable/general knowledge prefers the local model for minimum latency. When
 * that model is unavailable and returns the capability-only fallback, the same
 * question is retried through the verified research engine. Changing/current
 * data always uses verified research first and never falls back to stale local
 * knowledge.
 */
class UltraQueryExecutionCoordinator(
    private val researchEngine: UltraVerifiedResearchEngine
) {
    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String
    ): UltraQueryExecutionAnswer {
        if (!request.requiresInternet) {
            val local = localChat()
            if (!UltraLocalChatQualityPolicy.shouldUseResearchFallback(local)) {
                return UltraQueryExecutionAnswer(
                    message = local,
                    verified = false
                )
            }

            val researchFallback = researchEngine.answer(request)
            if (
                !researchFallback.abstained &&
                researchFallback.confidence != UltraAnswerConfidence.LOW
            ) {
                return researchAnswer(
                    research = researchFallback,
                    localFallbackUsed = true
                )
            }

            return researchAnswer(
                research = researchFallback,
                localFallbackUsed = true
            )
        }

        return researchAnswer(
            research = researchEngine.answer(request),
            localFallbackUsed = false
        )
    }

    private fun researchAnswer(
        research: UltraVerifiedResearchResult,
        localFallbackUsed: Boolean
    ): UltraQueryExecutionAnswer =
        UltraQueryExecutionAnswer(
            message = research.message,
            verified = !research.abstained &&
                research.confidence != UltraAnswerConfidence.LOW,
            confidence = research.confidence,
            sources = research.sources,
            fromCache = research.fromCache,
            timedOut = research.timedOut,
            fallbackUsed = localFallbackUsed || research.fallbackUsed,
            abstained = research.abstained
        )
}

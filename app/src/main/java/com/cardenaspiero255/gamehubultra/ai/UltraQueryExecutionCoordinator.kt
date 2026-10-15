package com.cardenaspiero255.gamehubultra.ai

data class UltraQueryExecutionAnswer(
    val message: String,
    val verified: Boolean,
    val confidence: UltraAnswerConfidence? = null,
    val sources: List<String> = emptyList(),
    val independentSourceCount: Int = 0,
    val fromCache: Boolean = false,
    val timedOut: Boolean = false,
    val fallbackUsed: Boolean = false,
    val abstained: Boolean = false,
    val reasonCode: String? = null,
    val retryable: Boolean = false,
    val stage: String? = null,
    val upstreamStatus: Int? = null
)

/**
 * Separates answerability from verification.
 *
 * LOCAL queries never touch research. OPTIONAL queries prefer a useful local
 * answer and use research only when local knowledge is unavailable. REQUIRED
 * queries must use verified research and never fall back to potentially stale
 * local knowledge.
 */
class UltraQueryExecutionCoordinator(
    private val researchGateway: UltraResearchGateway
) {
    val supportsProviderPartitioning: Boolean
        get() = researchGateway.supportsProviderPartitioning

    val providerPartitionCapacity: Int?
        get() = researchGateway.providerPartitionCapacity

    private fun safeLocalAnswer(localChat: () -> String?): String? =
        try {
            localChat()
                ?.trim()
                ?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }

    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer =
        when (request.verificationMode) {
            UltraVerificationMode.LOCAL -> localOnly(localChat)
            UltraVerificationMode.OPTIONAL -> optionalVerification(
                request = request,
                localChat = localChat
            )
            UltraVerificationMode.REQUIRED -> researchOnly(request)
        }

    private fun localOnly(
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val localAnswer = safeLocalAnswer(localChat)
        return if (localAnswer != null) {
            UltraQueryExecutionAnswer(
                message = localAnswer,
                verified = false
            )
        } else {
            UltraQueryExecutionAnswer(
                message = "No estoy seguro de esa respuesta.",
                verified = false,
                abstained = true
            )
        }
    }

    private fun optionalVerification(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        safeLocalAnswer(localChat)?.let { localAnswer ->
            return UltraQueryExecutionAnswer(
                message = localAnswer,
                verified = false,
                fallbackUsed = true,
                abstained = false
            )
        }

        val research = researchGateway.answer(request)
        val answer = toExecutionAnswer(research)
        if (!answer.abstained) return answer

        return answer.copy(
            message =
                "No pude verificar esa respuesta con fuentes fiables disponibles ahora. " +
                    "No voy a inventarla; revisa tu conexión o inténtalo de nuevo.",
            fallbackUsed = answer.fallbackUsed
        )
    }

    private fun researchOnly(
        request: UltraGeneralQueryRequest
    ): UltraQueryExecutionAnswer =
        toExecutionAnswer(researchGateway.answer(request))

    private fun toExecutionAnswer(
        research: UltraVerifiedResearchResult
    ): UltraQueryExecutionAnswer =
        UltraQueryExecutionAnswer(
            message = research.message,
            verified = !research.abstained &&
                research.confidence != UltraAnswerConfidence.LOW,
            confidence = research.confidence,
            sources = research.sources,
            independentSourceCount = research.independentSourceCount,
            fromCache = research.fromCache,
            timedOut = research.timedOut,
            fallbackUsed = research.fallbackUsed,
            abstained = research.abstained,
            reasonCode = research.reasonCode,
            retryable = research.retryable,
            stage = research.stage,
            upstreamStatus = research.upstreamStatus
        )
}

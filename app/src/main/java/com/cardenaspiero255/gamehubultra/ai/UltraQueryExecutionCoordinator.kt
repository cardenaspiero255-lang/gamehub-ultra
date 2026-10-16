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

    /**
     * Never trust a manually supplied verificationMode to downgrade fresh,
     * internet-required or comparison queries. The effective mode is derived
     * from all request constraints before allowing any local answer.
     */
    fun answer(
        request: UltraGeneralQueryRequest,
        localChat: () -> String?
    ): UltraQueryExecutionAnswer {
        val mandatoryResearch =
            request.requiresFreshData ||
                request.requiresInternet ||
                request.kind == UltraGeneralQueryKind.CURRENT_DATA ||
                request.kind == UltraGeneralQueryKind.COMPARISON_RESEARCH ||
                request.verificationMode == UltraVerificationMode.REQUIRED

        return when {
            mandatoryResearch ->
                researchOnly(
                    request.copy(
                        verificationMode = UltraVerificationMode.REQUIRED,
                        requiresInternet = true
                    )
                )
            request.verificationMode == UltraVerificationMode.LOCAL ->
                localOnly(localChat)
            else -> optionalVerification(request, localChat)
        }
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
                abstained = false,
                reasonCode = "LOCAL_STABLE_UNVERIFIED",
                stage = "offline-stable"
            )
        }

        val answer = safeResearchAnswer(request)
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
        safeResearchAnswer(request)

    /**
     * A faulty research adapter must not cause a stale local answer to be
     * presented as verified. Do not retry an uncontrolled exception here:
     * the Frontier layer is the only owner of bounded research retries.
     */
    private fun safeResearchAnswer(
        request: UltraGeneralQueryRequest
    ): UltraQueryExecutionAnswer =
        try {
            toExecutionAnswer(researchGateway.answer(request))
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UltraQueryExecutionAnswer(
                message = "No pude completar la consulta con fuentes verificables.",
                verified = false,
                abstained = true,
                retryable = false,
                reasonCode = "RESEARCH_PROVIDER_ERROR",
                stage = "research"
            )
        }

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

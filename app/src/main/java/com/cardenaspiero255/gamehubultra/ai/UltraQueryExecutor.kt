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
 *
 * [frontierExecutionEngine] is optional so existing tests and non-production
 * callers can keep the legacy single-pass coordinator behavior. Production
 * explicitly enables Frontier orchestration.
 */
class DefaultUltraQueryExecutor(
    private val coordinator: UltraQueryExecutionCoordinator,
    private val frontierExecutionEngine: UltraFrontierExecutionEngine? = null
) : UltraQueryExecutor {

    override fun answer(
        route: UltraAgentRoute.Chat,
        stableKnowledgeFallback: (() -> String?)?,
        localChat: () -> String
    ): String {
        val request = route.query ?: return localChat()

        val localAnswer: () -> String? = when (request.verificationMode) {
            UltraVerificationMode.LOCAL -> {
                { localChat() }
            }

            UltraVerificationMode.OPTIONAL -> {
                {
                    stableKnowledgeFallback
                        ?.invoke()
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }
            }

            UltraVerificationMode.REQUIRED -> {
                { null }
            }
        }

        val execution = frontierExecutionEngine?.answer(
            request = request,
            localChat = localAnswer
        ) ?: coordinator.answer(
            request = request,
            localChat = localAnswer
        )
        if (
            request.verificationMode == UltraVerificationMode.OPTIONAL &&
            execution.abstained
        ) {
            val emergencyLocal = try {
                localChat()
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.takeIf(::isUsefulEmergencyLocalAnswer)
            } catch (_: Exception) {
                null
            }
            if (emergencyLocal != null) {
                return "Respuesta local (sin verificación en línea): $emergencyLocal"
            }
        }
        return execution.message
    }

    private fun isUsefulEmergencyLocalAnswer(value: String): Boolean {
        if (value.length < 20) return false
        val normalized = value.lowercase()
        if (
            normalized.startsWith("soy ultra") &&
            normalized.contains("puedo ayudarte")
        ) {
            return false
        }
        val blocked = listOf(
            "no pude verificar",
            "no pudo verificar",
            "servicio de consulta no",
            "asistente general online no",
            "hola soy ultra",
            "puedo ayudarte con"
        )
        return blocked.none(normalized::contains)
    }
}

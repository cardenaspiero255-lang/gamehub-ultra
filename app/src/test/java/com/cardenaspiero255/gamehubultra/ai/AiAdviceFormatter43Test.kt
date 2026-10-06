package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AiAdviceFormatter43Test {
    @Test
    fun `recovery explanation is appended when non blank`() {
        assertEquals(
            "Perfil recomendado. Ajusté la confianza.",
            AiAdviceFormatter.appendRecoveryExplanation(
                base = "Perfil recomendado.",
                recoveryExplanation = "  Ajusté la confianza.  "
            )
        )
    }

    @Test
    fun `blank recovery explanation leaves response unchanged`() {
        assertEquals(
            "Perfil recomendado.",
            AiAdviceFormatter.appendRecoveryExplanation(
                base = "Perfil recomendado.",
                recoveryExplanation = "   "
            )
        )
        assertEquals(
            "Perfil recomendado.",
            AiAdviceFormatter.appendRecoveryExplanation(
                base = "Perfil recomendado.",
                recoveryExplanation = null
            )
        )
    }
    @Test
    fun `full response includes localized advice and recovery explanation`() {
        val context = RuntimeEnvironment.getApplication()
        val response = AiAdviceFormatter.fullResponse(
            context,
            GameHubAiAdvice(
                readiness = 91,
                suggestedProfile = PerformanceProfile.BALANCED,
                reason = AiAdviceReason.BALANCED_GENERAL,
                localModelUsed = false,
                fallbackUsed = true,
                recoveryExplanation = "Ajusté la recomendación con tu historial."
            )
        )

        kotlin.test.assertTrue(response.contains("91"))
        kotlin.test.assertTrue(
            response.contains("Ajusté la recomendación con tu historial.")
        )
    }

}

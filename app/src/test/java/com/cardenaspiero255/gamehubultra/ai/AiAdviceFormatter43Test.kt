package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import android.content.Context
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `full response executes formatter composition without Android resources`() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(ArgumentMatchers.anyInt()))
            .thenReturn("Equilibrado")
        Mockito.`when`(
            context.getString(
                ArgumentMatchers.eq(R.string.ai_reason_with_readiness),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()
            )
        ).thenReturn("Preparación 91: estable")

        val response = AiAdviceFormatter.fullResponse(
            context,
            GameHubAiAdvice(
                readiness = 91,
                suggestedProfile = PerformanceProfile.BALANCED,
                reason = AiAdviceReason.BALANCED_GENERAL,
                localModelUsed = false,
                fallbackUsed = true,
                recoveryExplanation = null
            )
        )

        assertTrue(response.startsWith("Equilibrado."))
        assertTrue(response.contains("Preparación 91"))
    }
}

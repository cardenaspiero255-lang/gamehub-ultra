package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

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
}

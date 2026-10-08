package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraFrontierLocalQualityTest {
    @Test
    fun factualQuestionRejectsAssistantBoilerplateAsAnAnswer() {
        val frontier = UltraFrontierOrchestrator()
        val query = UltraGeneralQueryRouter.classify("¿Qué es un libro?")
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query
            )
        )

        val verdict = UltraFrontierCritic().review(
            plan = plan,
            candidate = UltraFrontierCandidate(
                message = "Hola, soy Ultra. Puedo ayudarte con juegos y optimización.",
                verified = false,
                confidence = null,
                sources = emptyList(),
                abstained = false
            )
        )

        assertEquals(UltraFrontierVerdict.FALLBACK_LOCAL, verdict)
    }

    @Test
    fun explicitUncertaintyIsNotAcceptedAsUsefulLocalKnowledge() {
        val frontier = UltraFrontierOrchestrator()
        val query = UltraGeneralQueryRouter.classify("¿Qué es un exoplaneta?")
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query
            )
        )

        val verdict = UltraFrontierCritic().review(
            plan = plan,
            candidate = UltraFrontierCandidate(
                message = "No estoy seguro de esa respuesta.",
                verified = false,
                confidence = null,
                sources = emptyList(),
                abstained = false
            )
        )

        assertEquals(UltraFrontierVerdict.FALLBACK_LOCAL, verdict)
    }
}

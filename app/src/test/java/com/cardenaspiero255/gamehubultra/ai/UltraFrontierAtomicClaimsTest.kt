package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierAtomicClaimsTest {
    @Test
    fun atomicClaimVerificationSeparatesFactsFromInference() {
        val evolution = UltraFrontierEvolutionController()
        val query = UltraGeneralQueryRouter.classify("noticias de Android hoy")
        val plan = UltraFrontierOrchestrator(evolution = evolution).plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )
        val answer = UltraQueryExecutionAnswer(
            message =
                "Android recibió una actualización de seguridad. " +
                    "Probablemente este cambio mejore el rendimiento.",
            verified = true,
            confidence = UltraAnswerConfidence.HIGH,
            sources = listOf("official-a", "official-b"),
            independentSourceCount = 2,
            abstained = false
        )

        val claims = evolution.verifyAtomicClaims(
            request = query,
            plan = plan,
            answer = answer,
            nowMillis = 1_000L
        )

        assertEquals(2, claims.size)
        assertEquals(UltraClaimStatus.VERIFIED, claims[0].status)
        assertEquals(UltraClaimStatus.INFERRED, claims[1].status)

        val gated = evolution.finalGate(
            request = query,
            plan = plan,
            answer = answer,
            nowMillis = 1_000L
        )
        assertFalse(gated.abstained)
        assertFalse(gated.verified)
        assertEquals("FRONTIER_INFERRED_CLAIMS", gated.reasonCode)
    }

    @Test
    fun atomicClaimExtractorCapsPathologicalLongAnswers() {
        val claims = UltraAtomicClaimExtractor().extract(
            (1..30).joinToString(". ") { "Afirmación factual número $it" }
        )

        assertTrue(claims.isNotEmpty())
        assertTrue(claims.size <= 12)
    }
}

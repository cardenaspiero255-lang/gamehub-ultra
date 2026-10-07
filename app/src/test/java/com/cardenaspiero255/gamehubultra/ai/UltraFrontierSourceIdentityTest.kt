package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraFrontierSourceIdentityTest {
    @Test
    fun sourceQuorumTreatsCaseAndWhitespaceVariantsAsTheSameSource() {
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                minimumDeepSources = 2
            )
        )
        val query = UltraGeneralQueryRouter.classify(
            "Compara dos procesadores actuales y dime cuál es mejor"
        )
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )

        val verdict = UltraFrontierCritic().review(
            plan = plan,
            candidate = UltraFrontierCandidate(
                message = "Comparación candidata.",
                verified = true,
                confidence = UltraAnswerConfidence.HIGH,
                sources = listOf(" Source-A ", "source-a"),
                abstained = false,
                retryable = false
            )
        )

        assertEquals(UltraFrontierVerdict.ABSTAIN, verdict)
    }
}

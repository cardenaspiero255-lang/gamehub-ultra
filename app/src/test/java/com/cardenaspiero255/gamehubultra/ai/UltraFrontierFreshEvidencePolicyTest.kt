package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraFrontierFreshEvidencePolicyTest {
    @Test
    fun freshResearchUsesStricterSourceQuorumThanStableVerifiedResearch() {
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                minimumVerifiedSources = 1,
                minimumFreshSources = 2
            )
        )

        val fresh = UltraGeneralQueryRouter.classify("noticias de Android hoy")
        val freshPlan = frontier.plan(
            UltraFrontierRequest(
                message = fresh.originalText,
                query = fresh,
                networkAvailable = true
            )
        )

        val stable = UltraGeneralQueryRouter
            .classify("¿Qué es ChatGPT?")
            .copy(verificationMode = UltraVerificationMode.REQUIRED)
        val stablePlan = frontier.plan(
            UltraFrontierRequest(
                message = stable.originalText,
                query = stable,
                networkAvailable = true
            )
        )

        assertTrue(freshPlan.requiresFreshResearch)
        assertEquals(2, freshPlan.minimumDistinctSources)
        assertEquals(1, stablePlan.minimumDistinctSources)
    }

    @Test
    fun freshResearchRejectsSingleSourceEvenAtHighConfidence() {
        val frontier = UltraFrontierOrchestrator(
            UltraFrontierPolicy(
                minimumFreshSources = 2,
                verifiedResearchPassBudget = 1
            )
        )
        val query = UltraGeneralQueryRouter.classify("precio actual de un teléfono")
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
                message = "Precio candidato.",
                verified = true,
                confidence = UltraAnswerConfidence.HIGH,
                sources = listOf("single-source"),
                abstained = false,
                retryable = false
            )
        )

        assertEquals(UltraFrontierVerdict.ABSTAIN, verdict)
    }
}

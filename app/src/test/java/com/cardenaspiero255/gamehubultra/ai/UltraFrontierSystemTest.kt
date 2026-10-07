package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFrontierSystemTest {
    private val frontier = UltraFrontierOrchestrator()

    @Test
    fun stableKnowledgeUsesFastLocalLaneWithoutWastingResearch() {
        val query = UltraGeneralQueryRouter.classify("Ultra, ¿qué es un libro?")

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = "Ultra, ¿qué es un libro?",
                query = query,
                networkAvailable = true
            )
        )

        assertEquals(UltraFrontierLane.LOCAL_FAST, plan.lane)
        assertFalse(plan.requiresFreshResearch)
        assertFalse(plan.steps.any { it.specialist == UltraFrontierSpecialist.RESEARCH })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.LOCAL_REASONER })
        assertEquals(UltraFrontierFallback.LOCAL_SAFE, plan.fallback)
    }

    @Test
    fun freshCurrentDataRequiresVerifiedResearchAndNeverStaleLocalFallback() {
        val query = UltraGeneralQueryRouter.classify("¿Qué clima hace hoy en Rancagua?")

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )

        assertEquals(UltraFrontierLane.VERIFIED_RESEARCH, plan.lane)
        assertTrue(plan.requiresFreshResearch)
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.RESEARCH })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.VERIFIER })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.CRITIC })
        assertEquals(UltraFrontierFallback.ABSTAIN, plan.fallback)
    }

    @Test
    fun comparisonResearchGetsDeeperBudgetAndSynthesis() {
        val query = UltraGeneralQueryRouter.classify(
            "Compara Snapdragon 8 Elite y Dimensity 9400 y dime cuál es mejor"
        )

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )

        assertEquals(UltraFrontierLane.DEEP_RESEARCH, plan.lane)
        assertTrue(plan.researchPassBudget >= 2)
        assertTrue(plan.sourceBudget >= 6)
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.SYNTHESIZER })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.VERIFIER })
    }

    @Test
    fun screenshotOrLogsActivateMultimodalSpecialistWithoutChangingSafetyRules() {
        val query = UltraGeneralQueryRouter.classify("¿Qué error aparece aquí?")

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                attachments = listOf(
                    UltraFrontierAttachment(UltraFrontierAttachmentKind.SCREENSHOT),
                    UltraFrontierAttachment(UltraFrontierAttachmentKind.LOG)
                )
            )
        )

        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.MULTIMODAL })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.CRITIC })
        assertFalse(plan.autoExecuteMutation)
    }

    @Test
    fun mutatingToolActionAlwaysRequiresExplicitConfirmation() {
        val query = UltraGeneralQueryRouter.classify("activa un perfil de rendimiento")

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                action = UltraFrontierActionRequest(
                    toolId = "optimizer.profile",
                    mutatesState = true
                )
            )
        )

        assertEquals(UltraFrontierLane.TOOL_ACTION, plan.lane)
        assertTrue(plan.requiresUserConfirmation)
        assertFalse(plan.autoExecuteMutation)
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.TOOL_GATEWAY })
        assertTrue(plan.steps.any { it.specialist == UltraFrontierSpecialist.SAFETY_GATE })
    }

    @Test
    fun noNetworkForFreshDataProducesSafeBlockedPlanInsteadOfFabrication() {
        val query = UltraGeneralQueryRouter.classify("precio actual del Red Magic")

        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = false
            )
        )

        assertEquals(UltraFrontierLane.BLOCKED, plan.lane)
        assertEquals(UltraFrontierFallback.ABSTAIN, plan.fallback)
        assertTrue(plan.blockedReason?.contains("conex", ignoreCase = true) == true)
    }

    @Test
    fun criticRetriesWeakVerifiedAnswerThenAbstainsAtBudgetLimit() {
        val query = UltraGeneralQueryRouter.classify("noticias de Android hoy")
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query,
                networkAvailable = true
            )
        )
        val critic = UltraFrontierCritic()

        val first = critic.review(
            plan,
            UltraFrontierCandidate(
                message = "No pude verificarlo con suficiente confianza.",
                verified = false,
                confidence = UltraAnswerConfidence.LOW,
                sources = emptyList(),
                abstained = true,
                attempt = 1
            )
        )
        val last = critic.review(
            plan,
            UltraFrontierCandidate(
                message = "No pude verificarlo con suficiente confianza.",
                verified = false,
                confidence = UltraAnswerConfidence.LOW,
                sources = emptyList(),
                abstained = true,
                attempt = plan.researchPassBudget
            )
        )

        assertEquals(UltraFrontierVerdict.RETRY_RESEARCH, first)
        assertEquals(UltraFrontierVerdict.ABSTAIN, last)
    }

    @Test
    fun criticAcceptsUsefulLocalStableKnowledge() {
        val query = UltraGeneralQueryRouter.classify("¿Qué es un exoplaneta?")
        val plan = frontier.plan(
            UltraFrontierRequest(
                message = query.originalText,
                query = query
            )
        )

        val verdict = UltraFrontierCritic().review(
            plan,
            UltraFrontierCandidate(
                message = "Un exoplaneta es un planeta que orbita una estrella distinta del Sol.",
                verified = false,
                confidence = null,
                sources = emptyList(),
                abstained = false
            )
        )

        assertEquals(UltraFrontierVerdict.ACCEPT, verdict)
    }
}

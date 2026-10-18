package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reproduces the eight unverified/failed results from the physical Vivo V25 Pro
 * test. A CI success is not a substitute for a new physical device test.
 */
class UltraV3PhysicalFeedbackRegressionTest {
    private val context = GameHubAiContext(
        selectedGamePackage = null,
        sustainedPerformanceSupported = false,
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = null,
        batteryPercent = 74,
        charging = false,
        refreshRateHz = 120f,
        networkValidated = false,
        networkLatencyMs = null,
        downstreamBandwidthKbps = null,
        storageFreePercent = 50,
        inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.X4,
        sessionActive = false
    )

    @Test
    fun penicillinQuestionReturnsHistoricalAnswerWithoutInventingCurrentSources() {
        val question = "Ultra, ¿quién descubrió la penicilina?"
        val route = assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question))
        assertEquals(UltraVerificationMode.OPTIONAL, route.query?.verificationMode)
        val answer = assertNotNull(
            GameHubAiAdvisor().generalKnowledgeChatOrNull(question, context, emptyList())
        )
        assertTrue(answer.contains("Alexander Fleming"), answer)
        assertTrue(answer.contains("1928"), answer)
    }

    @Test
    fun dinosaursCauseQuestionAnswersImpactRatherThanGenericDinosaurDefinition() {
        val question = "Ultra, ¿qué causó la extinción de los dinosaurios?"
        val answer = assertNotNull(
            GameHubAiAdvisor().generalKnowledgeChatOrNull(question, context, emptyList())
        )
        assertTrue(answer.contains("Chicxulub", ignoreCase = true), answer)
        assertTrue(answer.contains("asteroide", ignoreCase = true), answer)
    }

    @Test
    fun stableProcessorDefinitionIsAvailableWithNoNetworkOrLocalLlm() {
        val question = "Ultra, ¿qué es un procesador?"
        val route = assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question))
        assertEquals(UltraVerificationMode.OPTIONAL, route.query?.verificationMode)
        val advisor = GameHubAiAdvisor()
        val answer = assertNotNull(advisor.generalKnowledgeChatOrNull(question, context, emptyList()))
        assertTrue(answer.contains("instrucciones", ignoreCase = true), answer)
        var researchCalls = 0
        val executor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
                override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                    researchCalls++
                    error("Offline stable fact must not use a provider")
                }
            })
        )
        val result = executor.answer(
            route,
            stableKnowledgeFallback = { advisor.generalKnowledgeChatOrNull(question, context, emptyList()) },
            localChat = { "Sin respuesta" }
        )
        assertEquals(0, researchCalls)
        assertTrue(result.contains("instrucciones", ignoreCase = true), result)
    }

    @Test
    fun negativeProfilePhraseAcknowledgesNoMutationInsteadOfAbstaining() {
        val question = "Ultra, no desactives X4"
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse(question))
        assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question))
        val answer = assertNotNull(
            GameHubAiAdvisor().generalKnowledgeChatOrNull(question, context, emptyList())
        )
        assertTrue(answer.contains("no desactiv", ignoreCase = true), answer)
        assertTrue(answer.contains("X4"), answer)
    }

    @Test
    fun hypotheticalX4QuestionExplainsEffectAndNeverClaimsExecution() {
        val question = "Ultra, ¿qué pasaría si activara X4?"
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse(question))
        assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(question))
        val answer = assertNotNull(
            GameHubAiAdvisor().generalKnowledgeChatOrNull(question, context, emptyList())
        )
        assertTrue(answer.contains("rendimiento", ignoreCase = true), answer)
        assertTrue(answer.contains("no he activado", ignoreCase = true), answer)
    }

    @Test
    fun freshBitcoinAndPhoneComparisonsCannotBePresentedAsOfflinePrices() {
        val bitcoin = UltraGeneralQueryRouter.classify("Ultra, ¿cuánto vale Bitcoin ahora?")
        assertEquals(UltraVerificationMode.REQUIRED, bitcoin.verificationMode)
        assertTrue(bitcoin.requiresFreshData)
        val comparison = UltraGeneralQueryRouter.classify(
            "Ultra, compara RedMagic 11S Pro con RedMagic 12 Pro"
        )
        assertEquals(UltraGeneralQueryKind.COMPARISON_RESEARCH, comparison.kind)
        assertEquals(UltraVerificationMode.REQUIRED, comparison.verificationMode)
        assertFalse(comparison.originalText.isBlank())
    }
}

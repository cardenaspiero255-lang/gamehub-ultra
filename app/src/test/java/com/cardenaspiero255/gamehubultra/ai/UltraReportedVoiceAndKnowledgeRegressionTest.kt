package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandEngine
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Physical user repro: recognized "qué es el Big Bang" but the answer
 * abstained, and "desactiva X4" tried to find an installed app.
 */
class UltraReportedVoiceAndKnowledgeRegressionTest {
    private val deviceContext = GameHubAiContext(
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
    fun reportedStableDefinitionsAreAnswerableWithoutNetworkOrModel() {
        val advisor = GameHubAiAdvisor()
        listOf(
            "Ultra, ¿qué es el Big Bang?" to "universo",
            "Ultra, ¿qué es la Biblia?" to "textos",
            "Ultra, ¿qué era la Biblia?" to "textos",
            "Ultra, ¿qué es un manuscrito?" to "mano",
            "Ultra, ¿qué era un manuscrito?" to "mano",
            "Ultra, ¿qué es un manuscrito antiguo?" to "mano"
        ).forEach { (utterance, distinctiveWord) ->
            val route = assertIs<UltraAgentRoute.Chat>(
                UltraUnifiedAgentRouter.route(utterance)
            )
            assertEquals(UltraVerificationMode.OPTIONAL, route.query?.verificationMode)
            val answer = assertNotNull(advisor.generalKnowledgeChatOrNull(
                route.message,
                deviceContext,
                emptyList()
            ), utterance)
            assertTrue(answer.contains(distinctiveWord, ignoreCase = true), answer)
            assertFalse(answer.contains("fuentes suficientes", ignoreCase = true))
        }
    }

    @Test
    fun reportedDefinitionsUseOfflineFallbackWithoutStartingResearch() {
        val calls = AtomicInteger()
        val advisor = GameHubAiAdvisor()
        val executor = DefaultUltraQueryExecutor(
            coordinator = UltraQueryExecutionCoordinator(object : UltraResearchGateway {
                override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                    calls.incrementAndGet()
                    error("Stable definition must not call network")
                }
            })
        )
        listOf("¿Qué es el Big Bang?", "¿Qué es la Biblia?", "¿Qué era la Biblia?", "¿Qué es un manuscrito?").forEach {
            val answer = executor.answer(
                route = assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(it)),
                stableKnowledgeFallback = {
                    advisor.generalKnowledgeChatOrNull(it, deviceContext, emptyList())
                },
                localChat = { advisor.chat(it, deviceContext, emptyList()) }
            )
            assertTrue(answer.length > 35, answer)
            assertFalse(answer.contains("No encontré fuentes suficientes"), answer)
        }
        assertEquals(0, calls.get())
    }

    @Test
    fun currentPriceAboutKnownTopicStillRequiresResearch() {
        val query = UltraGeneralQueryRouter.classify(
            "¿Cuál es el precio actual de una Biblia?"
        )
        assertEquals(UltraVerificationMode.REQUIRED, query.verificationMode)
        assertTrue(query.requiresFreshData)
    }

    @Test
    fun turnOffX4IsProfileChangeNotAppLaunchForVoiceAndTypedInput() {
        listOf(
            "Ultra, desactiva X4",
            "desactiva el modo X4",
            "Ultra, quita el perfil X4",
            "Ultra, apaga X4",
            "disable X4"
        ).forEach { utterance ->
            val parsed = VoiceCommandParser.parse(utterance)
            assertEquals(VoiceCommand.SelectProfile(PerformanceProfile.BALANCED), parsed, utterance)
            val spoken = assertIs<UltraAgentRoute.Command>(
                UltraUnifiedAgentRouter.route(utterance)
            )
            assertEquals(parsed, spoken.command)
            val typed = assertIs<UltraAgentRoute.Command>(
                UltraTypedChatRoutePlanner.route(utterance)
            )
            assertEquals(parsed, typed.command)
        }
    }

    @Test
    fun turningOffX4NeverQueriesInstalledAppsAndPersistsBalanced() {
        val command = assertIs<VoiceCommand.SelectProfile>(
            UltraUnifiedAgentRouter.route("Ultra, desactiva X4")
                .let { assertIs<UltraAgentRoute.Command>(it).command }
        )
        val selected = mutableListOf<PerformanceProfile>()
        val action = VoiceCommandEngine.execute(
            command = command,
            gamesProvider = { error("X4 is a profile, not an installed app") },
            launchGame = { error("X4 must not launch an app") },
            saveSelectedGame = { error("X4 must not select a game") },
            saveSelectedProfile = { selected += it },
            isProfileAvailable = { true },
            statusProvider = { com.cardenaspiero255.gamehubultra.voice.VoiceDeviceStatus(74, "Normal") }
        )
        val result = assertIs<VoiceActionResult.ProfileSelected>(action)
        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertEquals(listOf(PerformanceProfile.BALANCED), selected)
    }

    @Test
    fun questionsAndNegativeCommandsCannotDisableX4() {
        listOf(
            "Ultra, no desactives X4",
            "Ultra, no quites X4",
            "Ultra, ¿cómo desactivar X4?",
            "Ultra, ¿cómo apagar X4?",
            "Ultra, ¿qué pasaría si desactivara X4?"
        ).forEach { utterance ->
            assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse(utterance), utterance)
            assertIs<UltraAgentRoute.Chat>(UltraUnifiedAgentRouter.route(utterance), utterance)
        }
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("desactiva Minecraft"))
    }
}

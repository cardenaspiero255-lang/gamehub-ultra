package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VoiceAiIntentTest {
    private val context = GameHubAiContext(
        cpuCores = 6,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = 0.4f,
        batteryPercent = 80,
        charging = true,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 40L,
        downstreamBandwidthKbps = 50_000L,
        storageFreePercent = 30,
        inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false
    )

    @Test
    fun parserRoutesAdviceQuestionToAi() {
        val command = VoiceCommandParser.parse(
            "GameHub, ¿qué modo me recomiendas?",
            GameHubAiAdvisor().intentResolver()
        )
        assertIs<VoiceCommand.AskAi>(command)
    }

    @Test
    fun engineCanReturnAiAdviceWithoutExecutingAnythingElse() {
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.AskAi("qué perfil me recomiendas"),
            gamesProvider = { emptyList() },
            launchGame = { error("launch must not be called") },
            saveSelectedGame = { error("game selection must not be called") },
            saveSelectedProfile = { error("profile selection must not be called") },
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            aiAdvisor = { GameHubAiAdvisor().advise(it, context) }
        )
        val ai = assertIs<VoiceActionResult.AiAdvice>(result)
        assertEquals(PerformanceProfile.X4, ai.advice.suggestedProfile)
    }
}

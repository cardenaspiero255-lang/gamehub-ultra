package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameHubAiAdvisorTest {
    private val healthyContext = GameHubAiContext(
        cpuCores = 8,
        totalRamMb = 8192,
        gpuAvailable = true,
        thermalStatus = 0,
        thermalHeadroom = 0.35f,
        batteryPercent = 90,
        charging = true,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 35L,
        downstreamBandwidthKbps = 100_000L,
        storageFreePercent = 45,
        inputDeviceCount = 1,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false
    )

    @Test
    fun deterministicFallbackWorksWithoutLocalModel() {
        val advisor = GameHubAiAdvisor()
        val result = advisor.advise("que modo me recomiendas", healthyContext)

        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
        assertEquals(PerformanceProfile.X4, result.suggestedProfile)
    }

    @Test
    fun thermalOrBatteryPressureFallsBackToBalanced() {
        val advisor = GameHubAiAdvisor()
        val hot = healthyContext.copy(thermalHeadroom = 0.9f)
        assertEquals(
            PerformanceProfile.BALANCED,
            advisor.advise("que modo me recomiendas", hot).suggestedProfile
        )

        val lowBattery = healthyContext.copy(batteryPercent = 10, charging = false)
        assertEquals(
            PerformanceProfile.BALANCED,
            advisor.advise("que modo me recomiendas", lowBattery).suggestedProfile
        )
    }

    @Test
    fun voiceResolverOnlyClaimsAiForAdviceQuestions() {
        val resolver = GameHubAiAdvisor().intentResolver()
        assertIs<VoiceCommand.AskAi>(
            resolver.resolve("¿Qué perfil me recomiendas?")
        )
        assertEquals(
            null,
            resolver.resolve("abre Resident Evil 4 Remake")
        )
    }

    @Test
    fun invalidModelCandidateIsIgnoredAndFallsBack() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate("OPEN_URL", "https://example.com")
        }

        val result = GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)
        assertFalse(result.localModelUsed)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun validModelProfileActionIsAccepted() {
        val adapter = object : LocalAiModelAdapter {
            override fun isAvailable() = true

            override fun advise(
                question: String,
                context: GameHubAiContext
            ) = LocalAiActionCandidate(AiActionAllowlist.PROFILE_BALANCED)
        }

        val result = GameHubAiAdvisor(adapter).advise("recomiéndame", healthyContext)
        assertTrue(result.localModelUsed)
        assertFalse(result.fallbackUsed)
        assertEquals(PerformanceProfile.BALANCED, result.suggestedProfile)
    }
}

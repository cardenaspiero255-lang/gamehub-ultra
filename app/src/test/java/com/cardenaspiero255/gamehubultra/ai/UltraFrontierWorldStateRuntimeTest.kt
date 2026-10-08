package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UltraFrontierWorldStateRuntimeTest {
    @Test
    fun lightweightBackgroundContextCanRefreshPlanningState() {
        UltraFrontierWorldStateRegistry.clear()
        val context = GameHubAiContext(
            selectedGamePackage = "voice.game",
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
            gpuAvailable = true,
            thermalStatus = 2,
            thermalHeadroom = 0.64f,
            batteryPercent = 37,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 35L,
            downstreamBandwidthKbps = 300_000L,
            storageFreePercent = 50,
            inputDeviceCount = 1,
            selectedProfile = PerformanceProfile.BALANCED,
            sessionActive = true
        )

        UltraFrontierWorldStateRegistry.update(
            context = context,
            nowMillis = 10_000L
        )

        val state = assertNotNull(
            UltraFrontierWorldStateRegistry.snapshotForPlanning(10_001L)
        )
        assertEquals("voice.game", state.selectedGamePackage)
        assertEquals(37, state.batteryPercent)
        assertEquals(35L, state.networkLatencyMs)
        assertEquals(2, state.thermalStatus)
    }

    @Test
    fun telemetrySamplesAutomaticallyProduceAndPublishUnifiedWorldState() {
        UltraFrontierWorldStateRegistry.clear()
        val context = GameHubAiContext(
            selectedGamePackage = "game.pkg",
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
            gpuAvailable = true,
            thermalStatus = 3,
            thermalHeadroom = 0.82f,
            batteryPercent = 12,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 28L,
            downstreamBandwidthKbps = 500_000L,
            storageFreePercent = 60,
            inputDeviceCount = 1,
            selectedProfile = PerformanceProfile.X4,
            sessionActive = true
        )
        val samples = listOf(
            SessionCoachSnapshot(0L, 16, 1, 0.50f, 120f, 28L, batteryCharging = false),
            SessionCoachSnapshot(10_000L, 15, 1, 0.58f, 120f, 28L, batteryCharging = false),
            SessionCoachSnapshot(20_000L, 14, 2, 0.66f, 120f, 29L, batteryCharging = false),
            SessionCoachSnapshot(30_000L, 13, 2, 0.74f, 120f, 29L, batteryCharging = false),
            SessionCoachSnapshot(40_000L, 12, 3, 0.82f, 120f, 30L, batteryCharging = false)
        )
        val adaptive = AdaptiveDecision(
            profile = PerformanceProfile.BALANCED,
            enableSustainedPerformance = false,
            score = 42,
            reason = "thermal pressure",
            changed = true
        )

        val state = UltraFrontierWorldStateUpdater.update(
            context = context,
            sessionSamples = samples,
            adaptiveDecision = adaptive,
            nowMillis = 50_000L
        )

        assertEquals(state, UltraFrontierWorldStateRegistry.snapshot())
        assertEquals("game.pkg", state.selectedGamePackage)
        assertEquals(42, state.adaptiveScore)
        assertNotNull(state.thermalRisk)
        assertNotNull(state.batteryRecommendation)
        assertTrue(state.preventAggressiveProfiles)
        assertEquals(
            state,
            UltraFrontierWorldStateRegistry.snapshotForPlanning(50_000L)
        )
        assertEquals(
            null,
            UltraFrontierWorldStateRegistry.snapshotForPlanning(
                50_000L + 2 * 60_000L + 1L
            )
        )
        UltraFrontierWorldStateRegistry.clear()
    }
}

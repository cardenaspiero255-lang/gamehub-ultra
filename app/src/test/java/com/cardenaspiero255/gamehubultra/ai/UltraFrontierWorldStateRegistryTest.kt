package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.BatteryGamingRecommendation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import com.cardenaspiero255.gamehubultra.domain.ThermalTrend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UltraFrontierWorldStateRegistryTest {
    @Test
    fun freshAdvancedSignalsSurviveBaseContextRefreshForSameGame() {
        UltraFrontierWorldStateRegistry.clear()
        UltraFrontierWorldStateRegistry.update(
            UltraFrontierWorldState(
                selectedGamePackage = "game.pkg",
                sessionActive = false,
                selectedProfile = PerformanceProfile.BALANCED,
                networkValidated = true,
                networkLatencyMs = 30L,
                batteryPercent = 25,
                charging = false,
                thermalStatus = 3,
                thermalHeadroom = 0.8f,
                thermalTrend = ThermalTrend.RISING_FAST,
                thermalRisk = ThermalRisk.HIGH,
                thermalConfidence = 0.9f,
                batteryRecommendation = BatteryGamingRecommendation.BALANCED,
                preventAggressiveProfiles = true,
                adaptiveScore = 55,
                timestampMillis = 1_000L
            )
        )

        UltraFrontierWorldStateRegistry.update(
            context = context("game.pkg"),
            nowMillis = 2_000L
        )

        val state = UltraFrontierWorldStateRegistry.snapshot()
        assertEquals(ThermalRisk.HIGH, state?.thermalRisk)
        assertEquals(ThermalTrend.RISING_FAST, state?.thermalTrend)
        assertEquals(
            BatteryGamingRecommendation.BALANCED,
            state?.batteryRecommendation
        )
        UltraFrontierWorldStateRegistry.clear()
    }

    @Test
    fun staleAdvancedSignalsAreDroppedOnLaterContextRefresh() {
        UltraFrontierWorldStateRegistry.clear()
        UltraFrontierWorldStateRegistry.update(
            UltraFrontierWorldState(
                selectedGamePackage = "game.pkg",
                sessionActive = false,
                selectedProfile = PerformanceProfile.BALANCED,
                networkValidated = true,
                networkLatencyMs = 30L,
                batteryPercent = 25,
                charging = false,
                thermalStatus = 3,
                thermalHeadroom = 0.8f,
                thermalTrend = ThermalTrend.RISING_FAST,
                thermalRisk = ThermalRisk.HIGH,
                thermalConfidence = 0.9f,
                batteryRecommendation = BatteryGamingRecommendation.BALANCED,
                preventAggressiveProfiles = true,
                adaptiveScore = 55,
                timestampMillis = 1_000L
            )
        )

        UltraFrontierWorldStateRegistry.update(
            context = context("game.pkg"),
            nowMillis = 1_000L + 5 * 60_000L
        )

        val state = UltraFrontierWorldStateRegistry.snapshot()
        assertNull(state?.thermalRisk)
        assertNull(state?.batteryRecommendation)
        UltraFrontierWorldStateRegistry.clear()
    }

    private fun context(game: String) = GameHubAiContext(
        selectedGamePackage = game,
        sustainedPerformanceSupported = true,
        cpuCores = 8,
        totalRamMb = 8_192,
        gpuAvailable = true,
        thermalStatus = 1,
        thermalHeadroom = 0.3f,
        batteryPercent = 70,
        charging = false,
        refreshRateHz = 120f,
        networkValidated = true,
        networkLatencyMs = 20L,
        downstreamBandwidthKbps = 100_000L,
        storageFreePercent = 50,
        inputDeviceCount = 1,
        selectedProfile = PerformanceProfile.X4,
        sessionActive = true
    )
}

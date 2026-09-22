package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertTrue

class GamingReadinessTest {
    @Test
    fun strongConfigurationScoresHigh() {
        val result = GamingReadinessCalculator.calculate(
            GamingReadinessInput(
                cpuCores = 8,
                totalRamMb = 12_288,
                gpuAvailable = true,
                thermalStatus = 0,
                thermalHeadroom = 0.8f,
                batteryPercent = 90,
                charging = true,
                refreshRateHz = 144f,
                networkValidated = true,
                networkLatencyMs = 25,
                downstreamBandwidthKbps = 100_000,
                storageFreePercent = 60,
                inputDeviceCount = 1
            )
        )

        assertTrue(result.score >= 80)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun constrainedConfigurationIsFlagged() {
        val result = GamingReadinessCalculator.calculate(
            GamingReadinessInput(
                cpuCores = 2,
                totalRamMb = 2048,
                gpuAvailable = false,
                thermalStatus = 3,
                thermalHeadroom = 0.1f,
                batteryPercent = 10,
                charging = false,
                refreshRateHz = 60f,
                networkValidated = false,
                networkLatencyMs = 180,
                downstreamBandwidthKbps = 2_000,
                storageFreePercent = 5,
                inputDeviceCount = 0
            )
        )

        assertTrue(result.score < 60)
        assertTrue(result.label.isNotBlank())
        assertTrue(result.reasons.size >= 5)
    }
}

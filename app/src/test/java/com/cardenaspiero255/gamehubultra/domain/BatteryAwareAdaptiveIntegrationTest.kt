package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryAwareAdaptiveIntegrationTest {
    @Test
    fun `battery constraint can downshift an aggressive profile without fake thermal pressure`() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0,
        )
        val result = optimizer.evaluate(
            key = AdaptiveGameKey("game.battery", "1"),
            activeProfile = PerformanceProfile.X4,
            samples = listOf(
                AdaptiveTrendSample(
                    thermalStatus = 1,
                    batteryPercent = 60,
                    refreshRateHz = 120f,
                    memoryUsedPercent = 45,
                    latencyMs = 30,
                    batteryConstrained = true,
                    batteryConstraintReason = "drenaje de batería elevado",
                ),
            ),
            nowMillis = 10_000L,
        )

        assertTrue(result.changed)
        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertTrue(result.reason.contains("drenaje", ignoreCase = true))
    }

    @Test
    fun `mid battery percentage alone no longer invents a battery constraint`() {
        val optimizer = PerGameAdaptiveOptimizer(
            confirmationsRequired = 1,
            cooldownMillis = 0,
        )
        val result = optimizer.evaluate(
            key = AdaptiveGameKey("game.battery", "1"),
            activeProfile = PerformanceProfile.X4,
            samples = listOf(
                AdaptiveTrendSample(
                    thermalStatus = 1,
                    batteryPercent = 40,
                    refreshRateHz = 120f,
                    memoryUsedPercent = 45,
                    latencyMs = 30,
                    batteryConstrained = false,
                ),
            ),
            nowMillis = 10_000L,
        )

        assertFalse(result.changed)
        assertEquals(PerformanceProfile.X4, result.profile)
    }
}

package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryAwareGamingEngineTest {
    private val engine = BatteryAwareGamingEngine()

    @Test
    fun `measured discharging history estimates hourly drain and constrains aggressive profiles`() {
        val samples = listOf(
            snapshot(minutes = 0, percent = 80, charging = false),
            snapshot(minutes = 10, percent = 76, charging = false),
            snapshot(minutes = 20, percent = 72, charging = false),
        )

        val assessment = engine.assess(samples)

        assertEquals(8, assessment.observedDropPercent)
        assertEquals(24f, assessment.drainPercentPerHour)
        assertEquals(BatteryGamingRecommendation.BALANCED, assessment.recommendation)
        assertTrue(assessment.preventAggressiveProfiles)
        assertTrue(assessment.reason.contains("drenaje", ignoreCase = true))
    }

    @Test
    fun `charging samples do not masquerade as battery drain`() {
        val samples = listOf(
            snapshot(minutes = 0, percent = 20, charging = true),
            snapshot(minutes = 10, percent = 25, charging = true),
            snapshot(minutes = 20, percent = 30, charging = true),
        )

        val assessment = engine.assess(samples)

        assertTrue(assessment.charging)
        assertNull(assessment.drainPercentPerHour)
        assertEquals(BatteryGamingRecommendation.CHARGING, assessment.recommendation)
        assertFalse(assessment.preventAggressiveProfiles)
    }

    @Test
    fun `critical battery blocks aggressive profiles even before a drain estimate exists`() {
        val assessment = engine.assess(
            listOf(snapshot(minutes = 0, percent = 12, charging = false)),
        )

        assertEquals(BatteryGamingRecommendation.CONSERVE, assessment.recommendation)
        assertTrue(assessment.preventAggressiveProfiles)
        assertTrue(assessment.reason.contains("baja", ignoreCase = true))
    }

    @Test
    fun `power save mode is treated as an explicit battery constraint`() {
        val assessment = engine.assess(
            listOf(
                snapshot(
                    minutes = 0,
                    percent = 75,
                    charging = false,
                    powerSaveMode = true,
                ),
            ),
        )

        assertEquals(BatteryGamingRecommendation.CONSERVE, assessment.recommendation)
        assertTrue(assessment.preventAggressiveProfiles)
        assertTrue(assessment.reason.contains("ahorro", ignoreCase = true))
    }

    private fun snapshot(
        minutes: Int,
        percent: Int,
        charging: Boolean,
        powerSaveMode: Boolean = false,
    ) = SessionCoachSnapshot(
        timestampMillis = minutes * 60_000L,
        batteryPercent = percent,
        thermalStatus = 1,
        thermalHeadroom = 0.20f,
        refreshRateHz = 120f,
        latencyMs = 30L,
        batteryCharging = charging,
        powerSaveMode = powerSaveMode,
    )
}

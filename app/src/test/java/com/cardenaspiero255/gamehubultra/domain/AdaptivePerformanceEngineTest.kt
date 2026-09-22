package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptivePerformanceEngineTest {
    @Test
    fun requiresTwoConsistentSamplesBeforeChangingProfile() {
        val engine = AdaptivePerformanceEngine(
            confirmationsRequired = 2,
            initialProfile = PerformanceProfile.BALANCED
        )
        val ready = snapshot(
            batteryPercent = 90,
            charging = true,
            sustained = true,
            hints = true
        )

        val first = engine.evaluate(ready)
        assertEquals(PerformanceProfile.BALANCED, first.profile)
        assertEquals(1, first.pendingConfirmations)
        assertEquals(false, first.changed)

        val second = engine.evaluate(ready)
        assertEquals(PerformanceProfile.X4, second.profile)
        assertEquals(true, second.changed)
        assertEquals(true, second.enableSustainedPerformance)
    }

    @Test
    fun thermalConstraintTakesPriority() {
        val engine = AdaptivePerformanceEngine(initialProfile = PerformanceProfile.X4)
        val constrained = snapshot(
            thermalStatus = 3,
            thermalHeadroom = 0.1f,
            batteryPercent = 90,
            charging = true,
            sustained = true,
            hints = true
        )

        engine.evaluate(constrained)
        val decision = engine.evaluate(constrained)

        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertEquals(false, decision.enableSustainedPerformance)
        assertEquals(true, decision.changed)
    }

    @Test
    fun powerSaveAndLowBatteryStayConservative() {
        val engine = AdaptivePerformanceEngine(initialProfile = PerformanceProfile.X4)

        val powerSave = snapshot(
            batteryPercent = 90,
            charging = true,
            powerSave = true,
            sustained = true,
            hints = true
        )
        engine.evaluate(powerSave)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(powerSave).profile)

        val lowBattery = snapshot(
            batteryPercent = 10,
            charging = false,
            sustained = true,
            hints = true
        )
        engine.evaluate(lowBattery)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(lowBattery).profile)
    }

    @Test
    fun inactiveSessionNeverRecommendsAggressiveProfile() {
        val engine = AdaptivePerformanceEngine(initialProfile = PerformanceProfile.X4)
        val idle = snapshot(
            thermalHeadroom = 0.2f,
            batteryPercent = 100,
            charging = true,
            sessionActive = false,
            sustained = true,
            hints = true
        )

        engine.evaluate(idle)
        val decision = engine.evaluate(idle)

        assertEquals(PerformanceProfile.BALANCED, decision.profile)
        assertEquals(false, decision.enableSustainedPerformance)
    }

    @Test
    fun thermalThresholdUsesDirectionalHysteresis() {
        val engine = AdaptivePerformanceEngine(initialProfile = PerformanceProfile.X4)

        val warmButBelowEntry = snapshot(
            thermalStatus = 0,
            thermalHeadroom = 0.70f,
            batteryPercent = 90,
            charging = true,
            sustained = true
        )
        assertEquals(PerformanceProfile.X4, engine.evaluate(warmButBelowEntry).profile)

        val nearSevere = warmButBelowEntry.copy(thermalHeadroom = 0.81f)
        engine.evaluate(nearSevere)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(nearSevere).profile)

        val recovering = warmButBelowEntry.copy(thermalHeadroom = 0.65f)
        engine.evaluate(recovering)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(recovering).profile)

        val cool = warmButBelowEntry.copy(thermalHeadroom = 0.55f)
        engine.evaluate(cool)
        assertEquals(PerformanceProfile.X4, engine.evaluate(cool).profile)
    }

    @Test
    fun batteryThresholdUsesEntryAndExitHysteresis() {
        val engine = AdaptivePerformanceEngine(initialProfile = PerformanceProfile.X4)

        val safeBattery = snapshot(
            thermalHeadroom = 0.20f,
            batteryPercent = 52,
            charging = false,
            sustained = true
        )
        assertEquals(PerformanceProfile.X4, engine.evaluate(safeBattery).profile)

        val lowBattery = safeBattery.copy(batteryPercent = 49)
        engine.evaluate(lowBattery)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(lowBattery).profile)

        val recoveringButBelowEntry = lowBattery.copy(batteryPercent = 52)
        engine.evaluate(recoveringButBelowEntry)
        assertEquals(PerformanceProfile.BALANCED, engine.evaluate(recoveringButBelowEntry).profile)

        val recovered = lowBattery.copy(batteryPercent = 66)
        engine.evaluate(recovered)
        assertEquals(PerformanceProfile.X4, engine.evaluate(recovered).profile)
    }

    @Test
    fun fallsBackToInterpolationWhenSustainedModeIsUnavailable() {
        val engine = AdaptivePerformanceEngine()
        val ready = snapshot(
            batteryPercent = 80,
            charging = false,
            sustained = false,
            hints = true
        )

        engine.evaluate(ready)
        val decision = engine.evaluate(ready)

        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, decision.profile)
        assertEquals(false, decision.enableSustainedPerformance)
    }

    private fun snapshot(
        thermalStatus: Int? = 0,
        thermalHeadroom: Float? = 0.2f,
        batteryPercent: Int? = 80,
        charging: Boolean = false,
        powerSave: Boolean = false,
        sustained: Boolean = false,
        hints: Boolean = false,
        sessionActive: Boolean = true
    ) = AdaptiveRuntimeSnapshot(
        thermalStatus = thermalStatus,
        thermalHeadroom = thermalHeadroom,
        batteryPercent = batteryPercent,
        charging = charging,
        powerSaveMode = powerSave,
        sessionActive = sessionActive,
        sustainedPerformanceSupported = sustained,
        performanceHintsAvailable = hints
    )
}

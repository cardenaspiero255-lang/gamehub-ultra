package com.cardenaspiero255.gamehubultra.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerGameAdaptiveOptimizer47Test {
    @Test
    fun isolatesStatePerGameVersion() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 1000)
        val hot = listOf(AdaptiveTrendSample(4, 50, 60f, 80, 100))
        val v1 = AdaptiveGameKey("game.a", "1")
        val v2 = AdaptiveGameKey("game.a", "2")

        optimizer.evaluate(v1, PerformanceProfile.X4, hot, 0)
        assertTrue(optimizer.evaluate(v1, PerformanceProfile.X4, hot, 100).changed)

        val firstV2 = optimizer.evaluate(v2, PerformanceProfile.X4, hot, 100)
        assertFalse(firstV2.changed)
        assertEquals(PerformanceProfile.X4, firstV2.profile)
    }

    @Test
    fun combinesRequiredTrendsAndRecordsReason() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        val samples = listOf(
            AdaptiveTrendSample(1, 70, 120f, 55, 30),
            AdaptiveTrendSample(2, 58, 90f, 75, 70),
            AdaptiveTrendSample(3, 44, 60f, 91, 140)
        )

        val result = optimizer.evaluate(
            AdaptiveGameKey("game.a", "1"),
            PerformanceProfile.X4,
            samples,
            10_000
        )

        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertTrue(result.changed)
        assertTrue(result.reason.contains("tendencia"))
    }

    @Test
    fun emptySamplesNeverChangeActiveProfile() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)

        val result = optimizer.evaluate(
            AdaptiveGameKey("game.a", "1"),
            PerformanceProfile.X4,
            emptyList(),
            0
        )

        assertFalse(result.changed)
        assertEquals(PerformanceProfile.X4, result.profile)
        assertTrue(result.reason.contains("Sin muestras"))
    }

    @Test
    fun externalProfileChangeResetsPendingAutomaticDecision() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 1000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        assertFalse(optimizer.evaluate(key, PerformanceProfile.X4, hot, 0).changed)

        val reconciled = optimizer.evaluate(
            key = key,
            activeProfile = PerformanceProfile.BALANCED,
            samples = hot,
            nowMillis = 100
        )

        assertFalse(reconciled.changed)
        assertEquals(PerformanceProfile.BALANCED, reconciled.profile)
        assertTrue(reconciled.reason.contains("externo", ignoreCase = true))
    }

    @Test
    fun manualBalancedProfileIsNotAutomaticallyEscalated() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 0)
        val key = AdaptiveGameKey("game.a", "1")
        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 35, 25),
            AdaptiveTrendSample(0, 88, 120f, 36, 24),
            AdaptiveTrendSample(0, 86, 120f, 37, 23)
        )

        assertFalse(optimizer.evaluate(key, PerformanceProfile.BALANCED, stable, 0).changed)
        val second = optimizer.evaluate(key, PerformanceProfile.BALANCED, stable, 100)

        assertFalse(second.changed)
        assertEquals(PerformanceProfile.BALANCED, second.profile)
    }

    @Test
    fun cooldownAndHysteresisPreventOscillationButAllowOwnedRecovery() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 5000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val downshift = optimizer.evaluate(key, PerformanceProfile.X4, hot, 0)
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        val good = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val held = optimizer.evaluate(key, PerformanceProfile.BALANCED, good, 1000)
        assertFalse(held.changed)
        assertTrue(held.reason.contains("enfriamiento"))

        val recovered = optimizer.evaluate(key, PerformanceProfile.BALANCED, good, 6000)
        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.X4, recovered.profile)
    }
    @Test
    fun recoversTheExactProfileOwnedBeforeAutomaticDownshift() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 1000)
        val key = AdaptiveGameKey("game.a", "1")
        val hot = listOf(AdaptiveTrendSample(4, 40, 60f, 90, 120))

        val downshift = optimizer.evaluate(
            key,
            PerformanceProfile.FRAME_INTERPOLATION,
            hot,
            0
        )
        assertTrue(downshift.changed)
        assertEquals(PerformanceProfile.BALANCED, downshift.profile)

        val stable = listOf(
            AdaptiveTrendSample(0, 90, 120f, 30, 20),
            AdaptiveTrendSample(0, 88, 120f, 31, 22),
            AdaptiveTrendSample(0, 86, 120f, 32, 24)
        )
        val recovered = optimizer.evaluate(
            key,
            PerformanceProfile.BALANCED,
            stable,
            1500
        )

        assertTrue(recovered.changed)
        assertEquals(PerformanceProfile.FRAME_INTERPOLATION, recovered.profile)
    }

}

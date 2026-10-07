package com.cardenaspiero255.gamehubultra.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerGameAdaptiveOptimizer47Test {
    @Test fun isolatesStatePerGameVersion() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 1000)
        val hot = listOf(AdaptiveTrendSample(4, 50, 60f, 80, 100))
        val a = AdaptiveGameKey("game.a", "1")
        val b = AdaptiveGameKey("game.b", "1")
        optimizer.evaluate(a, PerformanceProfile.X4, hot, 0)
        assertTrue(optimizer.evaluate(a, PerformanceProfile.X4, hot, 100).changed)
        assertFalse(optimizer.evaluate(b, PerformanceProfile.X4, hot, 100).changed)
    }

    @Test fun combinesRequiredTrendsAndRecordsReason() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 1, cooldownMillis = 0)
        val samples = listOf(AdaptiveTrendSample(1,70,120f,55,30), AdaptiveTrendSample(2,58,90f,75,70), AdaptiveTrendSample(3,44,60f,91,140))
        val result = optimizer.evaluate(AdaptiveGameKey("game.a","1"), PerformanceProfile.X4, samples, 10000)
        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertTrue(result.changed)
        assertTrue(result.reason.contains("tendencia"))
    }

    @Test fun cooldownAndHysteresisPreventOscillation() {
        val optimizer = PerGameAdaptiveOptimizer(confirmationsRequired = 2, cooldownMillis = 5000)
        val key = AdaptiveGameKey("game.a","1")
        val hot = listOf(AdaptiveTrendSample(4,40,60f,90,120))
        optimizer.evaluate(key, PerformanceProfile.X4, hot, 0)
        assertTrue(optimizer.evaluate(key, PerformanceProfile.X4, hot, 100).changed)
        val good = listOf(AdaptiveTrendSample(0,90,120f,30,20))
        val held = optimizer.evaluate(key, PerformanceProfile.BALANCED, good, 1000)
        assertFalse(held.changed)
        assertTrue(held.reason.contains("enfriamiento"))
    }
}

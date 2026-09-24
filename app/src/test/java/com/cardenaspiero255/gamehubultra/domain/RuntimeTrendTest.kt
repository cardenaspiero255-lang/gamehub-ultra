package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeTrendTest {
    @Test
    fun directionDetectsUpDownAndStableChanges() {
        assertEquals(RuntimeTrendDirection.UP, RuntimeTrend.direction(40, 43))
        assertEquals(RuntimeTrendDirection.DOWN, RuntimeTrend.direction(43, 40))
        assertEquals(RuntimeTrendDirection.STABLE, RuntimeTrend.direction(40, 41))
        assertEquals(RuntimeTrendDirection.UNAVAILABLE, RuntimeTrend.direction(null, 41))
    }

    @Test
    fun fromClampsThermalHeadroomAndRoundsRefreshRate() {
        val sample = RuntimeTrend.from(91, 1.5f, 62, 119.6f)
        assertEquals(91, sample.batteryPercent)
        assertEquals(100, sample.thermalHeadroomPercent)
        assertEquals(62, sample.ramUsedPercent)
        assertEquals(120, sample.refreshRateHz)
    }
}

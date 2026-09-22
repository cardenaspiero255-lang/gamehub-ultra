package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PerformanceProfileTest {
    @Test
    fun profilesHaveStableTitles() {
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)
    }
}

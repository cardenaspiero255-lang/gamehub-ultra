package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceProfileTest {
    @Test
    fun profilesHaveStableTitles() {
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)
    }

    @Test
    fun interpolationProfileDoesNotClaimUnsupportedControl() {
        assertTrue(PerformanceProfile.FRAME_INTERPOLATION.description.contains("No puede forzar"))
        assertFalse(PerformanceProfile.FRAME_INTERPOLATION.description.contains("activa interpolación"))
    }
}

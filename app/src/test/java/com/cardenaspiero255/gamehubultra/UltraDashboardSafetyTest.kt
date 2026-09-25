package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraDashboardSafetyTest {
    @Test
    fun thermalHeadroomIsDisplayedAsEnvelopeUsage() {
        assertEquals(20, thermalEnvelopeUsagePercent(0.20f))
        assertEquals(85, thermalEnvelopeUsagePercent(0.85f))
        assertEquals(120, thermalEnvelopeUsagePercent(1.20f))
        assertNull(thermalEnvelopeUsagePercent(Float.NaN))
        assertNull(thermalEnvelopeUsagePercent(-0.1f))
    }

    @Test
    fun dashboardTelemetryStaysEnabledWithoutActiveGameSession() {
        val idlePlan = dashboardTelemetryPlan(sessionId = null)
        assertTrue(idlePlan.collectDashboardTelemetry)
        assertFalse(idlePlan.recordSessionEvents)

        val activePlan = dashboardTelemetryPlan(sessionId = "session-1")
        assertTrue(activePlan.collectDashboardTelemetry)
        assertTrue(activePlan.recordSessionEvents)
    }

    @Test
    fun openingQuickVoiceRequestsImmediateReveal() {
        assertTrue(shouldRevealQuickVoiceControls(wasOpen = false, isOpen = true))
        assertFalse(shouldRevealQuickVoiceControls(wasOpen = true, isOpen = false))
        assertFalse(shouldRevealQuickVoiceControls(wasOpen = true, isOpen = true))
    }

    @Test
    fun boosterBadgesDoNotPretendToBeMeasuredFps() {
        val presentations = PerformanceProfile.entries.map(::boosterPresentation)
        presentations.forEach { presentation ->
            assertFalse(presentation.badge.matches(Regex("\\d+")))
        }
        assertEquals("API", boosterPresentation(PerformanceProfile.FRAME_INTERPOLATION).badge)
        assertEquals("SPM", boosterPresentation(PerformanceProfile.X4).badge)
    }
}

package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraFinalExperienceGateTest {
    @Test
    fun finalExperienceIsReadyOnlyWhenEveryRequiredSurfaceIsReady() {
        val summary = UltraFinalExperienceGate.evaluate(
            dashboardReady = true,
            profileReady = true,
            diagnosticsReady = true,
            libraryReady = true,
            sessionHistoryReady = true,
            accessibilityReady = true,
            performanceReady = true,
            unsupportedClaimsAvoided = true
        )

        assertTrue(summary.readyForRelease)
        assertEquals("8/8", summary.readinessRatio)
        assertEquals("Ultra final listo", summary.statusLabel)
    }

    @Test
    fun finalExperienceBlocksCompletionWhenAnySurfaceIsMissing() {
        val summary = UltraFinalExperienceGate.evaluate(
            dashboardReady = true,
            profileReady = true,
            diagnosticsReady = false,
            libraryReady = true,
            sessionHistoryReady = false,
            accessibilityReady = true,
            performanceReady = true,
            unsupportedClaimsAvoided = true
        )

        assertFalse(summary.readyForRelease)
        assertEquals("6/8", summary.readinessRatio)
        assertEquals(
            listOf("Diagnósticos", "Historial"),
            summary.missingSurfaces
        )
        assertEquals("Diagnósticos · Historial", summary.missingSummary)
    }

    @Test
    fun finalExperienceAlwaysSurfacesSafetyClaimState() {
        val summary = UltraFinalExperienceGate.evaluate(
            dashboardReady = true,
            profileReady = true,
            diagnosticsReady = true,
            libraryReady = true,
            sessionHistoryReady = true,
            accessibilityReady = true,
            performanceReady = true,
            unsupportedClaimsAvoided = false
        )

        assertFalse(summary.readyForRelease)
        assertTrue("Sin claims falsos" in summary.missingSurfaces)
        assertEquals("Revisión pendiente", summary.statusLabel)
    }
}

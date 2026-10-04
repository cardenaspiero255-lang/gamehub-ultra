package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraInSessionVoiceCar38Test {
    @Test
    fun availableSessionMetricsStayShortAndGrounded() {
        val answer = UltraInSessionVoiceResponder.respond(
            query = "Ultra, estado de la partida",
            metrics = UltraSessionMetrics(
                sessionActive = true,
                refreshRateHz = 120f,
                batteryPercent = 64,
                thermalLabel = "moderada",
                latencyMs = 28
            )
        )

        assertTrue(answer.handled)
        assertTrue(answer.message.contains("120"))
        assertTrue(answer.message.contains("64"))
        assertTrue(answer.message.contains("moderada"))
        assertTrue(answer.message.contains("28"))
        assertTrue(answer.message.length <= 160)
    }

    @Test
    fun unavailableMetricsAreNeverInvented() {
        val answer = UltraInSessionVoiceResponder.respond(
            query = "Ultra, cuantos FPS y RAM tengo",
            metrics = UltraSessionMetrics(sessionActive = true)
        )

        assertTrue(answer.handled)
        assertFalse(answer.message.contains(Regex("""\\b\\d+\\s*(FPS|MB|GB)\\b""", RegexOption.IGNORE_CASE)))
        assertTrue(answer.message.contains("no disponible", ignoreCase = true))
    }

    @Test
    fun sessionResponseDoesNotActivateOutsideGameSession() {
        val answer = UltraInSessionVoiceResponder.respond(
            query = "Ultra, estado de la partida",
            metrics = UltraSessionMetrics(sessionActive = false, batteryPercent = 80)
        )

        assertEquals(UltraInSessionVoiceResponse.NotHandled, answer)
    }
    @Test
    fun runtimeMetricsAdapterUsesOnlyAvailableDiagnostics() {
        val metrics = UltraSessionMetricsFactory.fromRuntime(
            selectedGamePackage = "com.example.game",
            refreshRateHz = 120f,
            batteryPercent = 70,
            thermalLabel = "normal",
            totalRamMb = 8192,
            networkLatencyMs = null
        )

        assertTrue(metrics.sessionActive)
        assertEquals(120f, metrics.refreshRateHz)
        assertEquals(70, metrics.batteryPercent)
        assertEquals("normal", metrics.thermalLabel)
        assertEquals(8192L, metrics.ramMb)
        assertEquals(null, metrics.fps)
        assertEquals(null, metrics.latencyMs)
    }

    @Test
    fun sessionProfileChangeIsAllowedOnlyWhenRuntimeMarksItSafe() {
        val command = VoiceCommand.SelectProfile(
            com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.BALANCED
        )

        assertTrue(UltraSessionProfileSafety.canApply(command, sessionActive = true, safelySupported = true))
        assertFalse(UltraSessionProfileSafety.canApply(command, sessionActive = true, safelySupported = false))
        assertFalse(UltraSessionProfileSafety.canApply(command, sessionActive = false, safelySupported = true))
    }

}

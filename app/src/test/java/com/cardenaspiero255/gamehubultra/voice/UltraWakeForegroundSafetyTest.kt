package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakeForegroundSafetyTest {
    @Test
    fun foregroundMicrophonePromotionSucceedsWithoutStopping() {
        var stops = 0
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = {},
            stop = { stops++ }
        )
        assertTrue(started)
        assertEquals(0, stops)
    }

    @Test
    fun deniedMicrophonePermissionStopsServiceInsteadOfCrashing() {
        var stops = 0
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw SecurityException("Microphone type not permitted") },
            stop = { stops++ }
        )
        assertFalse(started)
        assertEquals(1, stops)
    }

    @Test
    fun backgroundForegroundServiceRestrictionStopsGracefully() {
        var stops = 0
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw IllegalStateException("Background FGS disallowed") },
            stop = { stops++ }
        )
        assertFalse(started)
        assertEquals(1, stops)
    }

    @Test
    fun rejectedForegroundMicClearsPersistentListeningBeforeStopping() {
        var enabled = true
        var stoppedWithEnabled = false
        val success = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw SecurityException("Microphone unavailable") },
            stop = { stoppedWithEnabled = enabled },
            disableContinuousVoice = { enabled = false }
        )
        assertFalse(success)
        assertFalse(enabled)
        assertFalse(stoppedWithEnabled)
    }

    @Test
    fun foregroundRejectionStillStopsWhenDisablingPersistentStateThrows() {
        var stopCalls = 0
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw SecurityException("Permission revoked") },
            stop = { stopCalls++ },
            disableContinuousVoice = { throw IllegalStateException("Storage unavailable") }
        )
        assertFalse(started)
        assertEquals(1, stopCalls)
    }

    @Test
    fun foregroundRejectionStillDisablesListeningWhenStopThrows() {
        var disabled = false
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw IllegalStateException("Foreground start restricted") },
            stop = { throw IllegalStateException("Already stopped") },
            disableContinuousVoice = { disabled = true }
        )
        assertFalse(started)
        assertTrue(disabled)
    }

    @Test
    fun stoppingAlreadyDestroyedServiceDoesNotCrashRecovery() {
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw SecurityException("Microphone revoked") },
            stop = { throw IllegalStateException("Already stopped") }
        )
        assertFalse(started)
    }
}

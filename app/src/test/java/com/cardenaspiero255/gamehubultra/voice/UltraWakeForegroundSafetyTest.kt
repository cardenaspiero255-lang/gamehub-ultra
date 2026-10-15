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
    fun stoppingAlreadyDestroyedServiceDoesNotCrashRecovery() {
        val started = UltraWakeForegroundSafety.tryStart(
            ensureForeground = { throw SecurityException("Microphone revoked") },
            stop = { throw IllegalStateException("Already stopped") }
        )
        assertFalse(started)
    }
}

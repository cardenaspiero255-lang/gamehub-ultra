package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceRecognitionRetryGateTest {
    @Test
    fun reentrantFallbackRetryIsBlockedUntilPostedRetryRuns() {
        val gate = VoiceRecognitionRetryGate()

        assertTrue(gate.trySchedule())
        assertFalse(gate.trySchedule())

        gate.onRetryDispatched()

        assertTrue(gate.trySchedule())
    }

    @Test
    fun resetCancelsPendingRetryState() {
        val gate = VoiceRecognitionRetryGate()

        assertTrue(gate.trySchedule())
        gate.reset()

        assertTrue(gate.trySchedule())
    }
}

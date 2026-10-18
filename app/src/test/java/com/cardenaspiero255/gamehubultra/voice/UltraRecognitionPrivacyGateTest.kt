package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraRecognitionPrivacyGateTest {
    @Test
    fun alreadyQueuedRecognitionCallbacksAreInvalidatedByPrivacyErasure() {
        val gate = UltraRecognitionPrivacyGate()
        val preErase = gate.start()
        assertTrue(gate.isCurrent(preErase))
        UltraPrivacyEpoch.invalidate()
        assertFalse(gate.isCurrent(preErase))
        val postErase = gate.start()
        assertTrue(gate.isCurrent(postErase))
        assertFalse(gate.isCurrent(preErase))
    }

    @Test
    fun staleSpeechRecognizerCallbacksAreRejectedAfterRestartOrStop() {
        val gate = UltraRecognitionPrivacyGate()
        val first = gate.start()
        val second = gate.start()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        gate.stop()
        assertFalse(gate.isCurrent(second))
    }
}

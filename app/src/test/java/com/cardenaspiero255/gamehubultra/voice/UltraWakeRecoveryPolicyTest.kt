package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class UltraWakeRecoveryPolicyTest {
    @Test
    fun repeatedRecognizerFailuresBackOffInsteadOfSpinning() {
        val policy = UltraWakeRecoveryPolicy()
        assertEquals(500L, policy.nextDelayMillis())
        assertEquals(1_000L, policy.nextDelayMillis())
        assertEquals(2_000L, policy.nextDelayMillis())
        assertEquals(4_000L, policy.nextDelayMillis())
        assertEquals(8_000L, policy.nextDelayMillis())
        assertEquals(16_000L, policy.nextDelayMillis())
        assertEquals(30_000L, policy.nextDelayMillis())
        repeat(100) { assertEquals(30_000L, policy.nextDelayMillis()) }
    }

    @Test
    fun successfulRecognitionRestoresFastRecovery() {
        val policy = UltraWakeRecoveryPolicy()
        repeat(7) { policy.nextDelayMillis() }
        policy.onRecognitionHealthy()
        assertEquals(500L, policy.nextDelayMillis())
    }

    @Test
    fun failurePolicyRejectsInvalidConfiguration() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            UltraWakeRecoveryPolicy(initialDelayMillis = 0L)
        }
    }
}

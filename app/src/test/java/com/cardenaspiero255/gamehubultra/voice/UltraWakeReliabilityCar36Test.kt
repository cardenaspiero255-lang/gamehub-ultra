package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakeReliabilityCar36Test {
    @Test
    fun strictSensitivityRejectsNearMissWakeWord() {
        assertFalse(UltraWakeWordMatcher.contains("ultraa abre resident evil", UltraWakeSensitivity.STRICT))
    }

    @Test
    fun balancedSensitivityAcceptsCommonSingleEditRecognition() {
        assertTrue(UltraWakeWordMatcher.contains("ultraa abre resident evil", UltraWakeSensitivity.BALANCED))
    }

    @Test
    fun wakeInvocationMustBeAtStartInsteadOfBackgroundMention() {
        assertFalse(UltraWakeWordMatcher.isExplicitInvocation("en el juego dicen ultra abre la puerta"))
        assertTrue(UltraWakeWordMatcher.isExplicitInvocation("Ultra abre Resident Evil"))
    }

    @Test
    fun repeatedIdenticalCommandIsSuppressedInsideCooldown() {
        val gate = UltraWakeRepeatGate(cooldownMillis = 1_500L)
        assertTrue(gate.shouldAccept("Ultra abre Resident Evil", nowMillis = 1_000L))
        assertFalse(gate.shouldAccept("ultra, abre resident evil", nowMillis = 2_000L))
        assertTrue(gate.shouldAccept("Ultra abre Resident Evil", nowMillis = 2_501L))
    }

    @Test
    fun differentCommandIsNotBlockedByRepeatCooldown() {
        val gate = UltraWakeRepeatGate(cooldownMillis = 1_500L)
        assertTrue(gate.shouldAccept("Ultra abre Resident Evil", nowMillis = 1_000L))
        assertTrue(gate.shouldAccept("Ultra modo equilibrado", nowMillis = 1_100L))
    }
}

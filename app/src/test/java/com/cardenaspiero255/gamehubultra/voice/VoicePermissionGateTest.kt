package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePermissionGateTest {
    @Test
    fun requiresMicrophonePermission() {
        assertFalse(VoicePermissionGate.canStartRecognition(false, true))
    }

    @Test
    fun requiresRecognitionProvider() {
        assertFalse(VoicePermissionGate.canStartRecognition(true, false))
    }

    @Test
    fun startsOnlyWhenBothRequirementsAreMet() {
        assertTrue(VoicePermissionGate.canStartRecognition(true, true))
    }
}

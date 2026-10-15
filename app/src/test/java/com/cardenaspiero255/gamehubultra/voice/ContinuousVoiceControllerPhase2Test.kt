package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContinuousVoiceControllerPhase2Test {
    @Test
    fun microphoneServiceStartDeniedByAndroidDoesNotCrashOrRemainEnabled() {
        val gateway = RestrictedGateway()
        val controller = ContinuousVoiceController(gateway)
        assertEquals(ContinuousVoiceChange.SYSTEM_RESTRICTED, controller.setEnabled(true))
        assertFalse(controller.isEnabled())
        assertEquals(1, gateway.stopCalls)
    }

    @Test
    fun microphonePermissionSecurityExceptionDisablesContinuousModeSafely() {
        val gateway = RestrictedGateway()
        gateway.failure = SecurityException("RECORD_AUDIO denied by Android")
        val controller = ContinuousVoiceController(gateway)
        assertEquals(ContinuousVoiceChange.SYSTEM_RESTRICTED, controller.setEnabled(true))
        assertFalse(controller.isEnabled())
        assertEquals(1, gateway.stopCalls)
    }

    @Test
    fun restoreAfterBackgroundRestrictionClearsMisleadingPersistentSetting() {
        val gateway = RestrictedGateway()
        gateway.enabled = true
        val controller = ContinuousVoiceController(gateway)
        assertFalse(controller.resumeIfEnabled())
        assertFalse(controller.isEnabled())
        assertEquals(1, gateway.stopCalls)
    }

    @Test
    fun permissionGrantDoesNotBypassSystemSupportCheck() {
        val gateway = RestrictedGateway()
        gateway.supported = false
        val controller = ContinuousVoiceController(gateway)
        assertEquals(ContinuousVoiceChange.SYSTEM_RESTRICTED, controller.enableAfterPermissionGranted())
        assertFalse(gateway.enabled)
        assertEquals(0, gateway.startCalls)
    }

    private class RestrictedGateway : ContinuousVoiceGateway {
        var enabled = false
        var supported = true
        var failure: RuntimeException = IllegalStateException("Foreground microphone service start denied")
        var startCalls = 0
        var stopCalls = 0
        override fun isEnabled() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun hasRecordAudioPermission() = true
        override fun canRunContinuousVoice() = supported
        override fun startWakeService() {
            startCalls += 1
            throw failure
        }
        override fun stopWakeService() { stopCalls += 1 }
    }
}

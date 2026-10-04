package com.cardenaspiero255.gamehubultra.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContinuousVoiceControllerCar40Test {
    @Test
    fun revokedPermissionDisablesContinuousVoiceInsteadOfRestartLoop() {
        val gateway = FakeGateway(storedEnabled = true, permission = false)
        val controller = ContinuousVoiceController(gateway)

        assertFalse(controller.resumeIfEnabled())
        assertFalse(gateway.storedEnabled)
        assertEquals(1, gateway.stopCalls)
        assertEquals(0, gateway.startCalls)
    }

    @Test
    fun systemRestrictionFailsClosedWithoutPersistingAlwaysOnState() {
        val gateway = FakeGateway(storedEnabled = false, permission = true, canRun = false)
        val controller = ContinuousVoiceController(gateway)

        assertEquals(ContinuousVoiceChange.SYSTEM_RESTRICTED, controller.setEnabled(true))
        assertFalse(gateway.storedEnabled)
        assertEquals(0, gateway.startCalls)
    }

    private class FakeGateway(
        var storedEnabled: Boolean,
        var permission: Boolean,
        var canRun: Boolean = true
    ) : ContinuousVoiceGateway {
        var startCalls = 0
        var stopCalls = 0
        override fun isEnabled() = storedEnabled
        override fun setEnabled(enabled: Boolean) { this.storedEnabled = enabled }
        override fun hasRecordAudioPermission() = permission
        override fun canRunContinuousVoice() = canRun
        override fun startWakeService() { startCalls++ }
        override fun stopWakeService() { stopCalls++ }
    }
}

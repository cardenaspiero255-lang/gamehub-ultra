package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuousVoiceControllerTest {

    @Test
    fun resumeStartsWakeServiceOnlyWhenEnabledAndPermissionGranted() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = true,
            permissionGranted = true
        )
        val controller = ContinuousVoiceController(gateway)

        val resumed = controller.resumeIfEnabled()

        assertTrue(resumed)
        assertEquals(1, gateway.startCalls)
        assertEquals(0, gateway.stopCalls)
    }

    @Test
    fun resumeDoesNothingWithoutPermission() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = true,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val resumed = controller.resumeIfEnabled()

        assertFalse(resumed)
        assertEquals(0, gateway.startCalls)
        assertTrue(gateway.storedEnabled)
    }

    @Test
    fun enablingWithoutPermissionRequestsPermissionWithoutPersistingState() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = false,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(true)

        assertEquals(
            ContinuousVoiceChange.PERMISSION_REQUIRED,
            result
        )
        assertFalse(gateway.storedEnabled)
        assertEquals(0, gateway.startCalls)
    }

    @Test
    fun enablingWithPermissionPersistsAndStartsWakeService() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = false,
            permissionGranted = true
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(true)

        assertEquals(ContinuousVoiceChange.ENABLED, result)
        assertTrue(gateway.storedEnabled)
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun permissionGrantCompletesPendingEnablement() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = false,
            permissionGranted = true
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.enableAfterPermissionGranted()

        assertEquals(ContinuousVoiceChange.ENABLED, result)
        assertTrue(gateway.storedEnabled)
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun disablingPersistsAndStopsServiceEvenWhenPermissionIsMissing() {
        val gateway = FakeContinuousVoiceGateway(
            storedEnabled = true,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(false)

        assertEquals(ContinuousVoiceChange.DISABLED, result)
        assertFalse(gateway.storedEnabled)
        assertEquals(1, gateway.stopCalls)
    }

    @Test
    fun currentEnabledStateComesFromGateway() {
        val gateway = FakeContinuousVoiceGateway(storedEnabled = true)
        val controller = ContinuousVoiceController(gateway)

        assertTrue(controller.isEnabled())

        gateway.storedEnabled = false
        assertFalse(controller.isEnabled())
    }

    private class FakeContinuousVoiceGateway(
        var storedEnabled: Boolean,
        var permissionGranted: Boolean = true
    ) : ContinuousVoiceGateway {
        var startCalls = 0
        var stopCalls = 0

        override fun isEnabled(): Boolean = storedEnabled

        override fun setEnabled(enabled: Boolean) {
            this.storedEnabled = enabled
        }

        override fun hasRecordAudioPermission(): Boolean =
            permissionGranted

        override fun canRunContinuousVoice(): Boolean = true

        override fun startWakeService() {
            startCalls += 1
        }

        override fun stopWakeService() {
            stopCalls += 1
        }
    }
}

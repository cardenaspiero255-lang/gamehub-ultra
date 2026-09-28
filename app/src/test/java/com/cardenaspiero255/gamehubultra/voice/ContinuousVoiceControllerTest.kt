package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuousVoiceControllerTest {

    @Test
    fun resumeStartsWakeServiceOnlyWhenEnabledAndPermissionGranted() {
        val gateway = FakeContinuousVoiceGateway(
            enabled = true,
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
            enabled = true,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val resumed = controller.resumeIfEnabled()

        assertFalse(resumed)
        assertEquals(0, gateway.startCalls)
        assertTrue(gateway.enabled)
    }

    @Test
    fun enablingWithoutPermissionRequestsPermissionWithoutPersistingState() {
        val gateway = FakeContinuousVoiceGateway(
            enabled = false,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(true)

        assertEquals(
            ContinuousVoiceChange.PERMISSION_REQUIRED,
            result
        )
        assertFalse(gateway.enabled)
        assertEquals(0, gateway.startCalls)
    }

    @Test
    fun enablingWithPermissionPersistsAndStartsWakeService() {
        val gateway = FakeContinuousVoiceGateway(
            enabled = false,
            permissionGranted = true
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(true)

        assertEquals(ContinuousVoiceChange.ENABLED, result)
        assertTrue(gateway.enabled)
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun permissionGrantCompletesPendingEnablement() {
        val gateway = FakeContinuousVoiceGateway(
            enabled = false,
            permissionGranted = true
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.enableAfterPermissionGranted()

        assertEquals(ContinuousVoiceChange.ENABLED, result)
        assertTrue(gateway.enabled)
        assertEquals(1, gateway.startCalls)
    }

    @Test
    fun disablingPersistsAndStopsServiceEvenWhenPermissionIsMissing() {
        val gateway = FakeContinuousVoiceGateway(
            enabled = true,
            permissionGranted = false
        )
        val controller = ContinuousVoiceController(gateway)

        val result = controller.setEnabled(false)

        assertEquals(ContinuousVoiceChange.DISABLED, result)
        assertFalse(gateway.enabled)
        assertEquals(1, gateway.stopCalls)
    }

    @Test
    fun currentEnabledStateComesFromGateway() {
        val gateway = FakeContinuousVoiceGateway(enabled = true)
        val controller = ContinuousVoiceController(gateway)

        assertTrue(controller.isEnabled())

        gateway.enabled = false
        assertFalse(controller.isEnabled())
    }

    private class FakeContinuousVoiceGateway(
        var enabled: Boolean,
        var permissionGranted: Boolean = true
    ) : ContinuousVoiceGateway {
        var startCalls = 0
        var stopCalls = 0

        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override fun hasRecordAudioPermission(): Boolean =
            permissionGranted

        override fun startWakeService() {
            startCalls += 1
        }

        override fun stopWakeService() {
            stopCalls += 1
        }
    }
}

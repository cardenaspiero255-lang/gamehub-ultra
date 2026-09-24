package com.cardenaspiero255.gamehubultra.platform

import android.media.AudioDeviceInfo
import android.view.InputDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeripheralClassifierTest {
    @Test
    fun classifiesInputSourcesWithoutFalsePositives() {
        assertEquals(
            setOf(PeripheralKind.GAMEPAD),
            PeripheralClassifier.classifyInputSources(InputDevice.SOURCE_GAMEPAD)
        )
        val keyboardMouse = PeripheralClassifier.classifyInputSources(
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE
        )
        assertTrue(PeripheralKind.KEYBOARD in keyboardMouse)
        assertTrue(PeripheralKind.MOUSE in keyboardMouse)
        assertTrue(PeripheralKind.GAMEPAD !in keyboardMouse)
        assertTrue(PeripheralClassifier.classifyInputSources(0).isEmpty())
    }

    @Test
    fun externalAudioClassifierOnlyAcceptsKnownExternalTypes() {
        assertTrue(PeripheralClassifier.isExternalAudioType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(PeripheralClassifier.isExternalAudioType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(!PeripheralClassifier.isExternalAudioType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
}

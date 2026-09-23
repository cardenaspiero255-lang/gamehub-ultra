package com.cardenaspiero255.gamehubultra.platform

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice

data class InputAccessorySummary(
    val gamepads: Int,
    val keyboards: Int,
    val mice: Int,
    val externalAudio: Boolean
) {
    val total: Int get() = gamepads + keyboards + mice
}

object InputAccessoryDetector {
    fun detect(context: Context): InputAccessorySummary {
        val inputManager = context.getSystemService(InputManager::class.java)
        val ids = inputManager?.inputDeviceIds.orEmpty()
        var gamepads = 0
        var keyboards = 0
        var mice = 0

        ids.forEach { id ->
            val device = inputManager?.getInputDevice(id) ?: return@forEach
            if (device.isVirtual) return@forEach
            val sources = device.sources
            if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) gamepads++
            if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) keyboards++
            if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) mice++
        }

        val audioManager = context.getSystemService(android.media.AudioManager::class.java)
        val externalAudio = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { device ->
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
            } == true

        return InputAccessorySummary(gamepads, keyboards, mice, externalAudio)
    }
}

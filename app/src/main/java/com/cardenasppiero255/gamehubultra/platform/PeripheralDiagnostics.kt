package com.cardenaspiero255.gamehubultra.platform
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.InputDevice

data class PeripheralDiagnostics(val gamepadCount:Int,val keyboardCount:Int,val mouseCount:Int,val externalAudioCount:Int) {
    val totalExternalInputCount:Int get() = gamepadCount + keyboardCount + mouseCount
}
object PeripheralDiagnosticsProvider {
    fun get(context: Context): PeripheralDiagnostics {
        val input=context.getSystemService(android.hardware.input.InputManager::class.java)
        var gamepads=0; var keyboards=0; var mice=0
        input?.inputDeviceIds?.forEach { id ->
            val device=input.getInputDevice(id) ?: return@forEach
            if(device.isVirtual) return@forEach
            val sources=device.sources
            if((sources and InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD || (sources and InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK) gamepads++
            if((sources and InputDevice.SOURCE_KEYBOARD)==InputDevice.SOURCE_KEYBOARD) keyboards++
            if((sources and InputDevice.SOURCE_MOUSE)==InputDevice.SOURCE_MOUSE) mice++
        }
        val audio=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M) context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.count(::isExternalAudioDevice) ?: 0 else 0
        return PeripheralDiagnostics(gamepads,keyboards,mice,audio)
    }
    private fun isExternalAudioDevice(device: AudioDeviceInfo): Boolean = when(device.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,AudioDeviceInfo.TYPE_HDMI,AudioDeviceInfo.TYPE_DOCK,AudioDeviceInfo.TYPE_USB_ACCESSORY,AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_LINE_ANALOG,AudioDeviceInfo.TYPE_LINE_DIGITAL,AudioDeviceInfo.TYPE_AUX_LINE,AudioDeviceInfo.TYPE_HEARING_AID -> true
        AudioDeviceInfo.TYPE_HDMI_ARC,AudioDeviceInfo.TYPE_HDMI_EARC,AudioDeviceInfo.TYPE_BLE_HEADSET,AudioDeviceInfo.TYPE_BLE_SPEAKER,AudioDeviceInfo.TYPE_BLE_BROADCAST,AudioDeviceInfo.TYPE_DOCK_ANALOG -> true
        else -> false
    }
}
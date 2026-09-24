package com.cardenaspiero255.gamehubultra.platform
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.InputDevice

enum class PeripheralKind { GAMEPAD, KEYBOARD, MOUSE }

data class PeripheralDeviceInfo(
    val name: String,
    val kinds: Set<PeripheralKind>
)

data class PeripheralDiagnostics(
    val gamepadCount: Int,
    val keyboardCount: Int,
    val mouseCount: Int,
    val externalAudioCount: Int,
    val inputDevices: List<PeripheralDeviceInfo> = emptyList(),
    val externalAudioDevices: List<String> = emptyList()
) {
    val totalExternalInputCount: Int
        get() = gamepadCount + keyboardCount + mouseCount
}

object PeripheralClassifier {
    fun classifyInputSources(sources: Int): Set<PeripheralKind> = buildSet {
        if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) add(PeripheralKind.GAMEPAD)
        if ((sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD) add(PeripheralKind.KEYBOARD)
        if ((sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) add(PeripheralKind.MOUSE)
    }

    fun isExternalAudioType(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_DOCK,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_DOCK_ANALOG -> true
        else -> false
    }
}

object PeripheralDiagnosticsProvider {
    fun get(context: Context): PeripheralDiagnostics {
        val input = context.getSystemService(android.hardware.input.InputManager::class.java)
        val devices = buildList {
            input?.inputDeviceIds?.forEach { id ->
                val device = input.getInputDevice(id) ?: return@forEach
                if (device.isVirtual) return@forEach
                val kinds = PeripheralClassifier.classifyInputSources(device.sources)
                if (kinds.isNotEmpty()) {
                    add(
                        PeripheralDeviceInfo(
                            name = device.name?.takeIf { it.isNotBlank() } ?: "Input #$id",
                            kinds = kinds
                        )
                    )
                }
            }
        }

        val audioDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.filter { PeripheralClassifier.isExternalAudioType(it.type) }
                ?.map { it.productName?.toString()?.takeIf(String::isNotBlank) ?: "Audio externo" }
                ?.distinct()
                .orEmpty()
        } else emptyList()

        return PeripheralDiagnostics(
            gamepadCount = devices.count { PeripheralKind.GAMEPAD in it.kinds },
            keyboardCount = devices.count { PeripheralKind.KEYBOARD in it.kinds },
            mouseCount = devices.count { PeripheralKind.MOUSE in it.kinds },
            externalAudioCount = audioDevices.size,
            inputDevices = devices,
            externalAudioDevices = audioDevices
        )
    }
}

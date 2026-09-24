package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.DeviceInfo

object OptimizationFingerprint {
    fun from(
        device: DeviceInfo,
        gamePackage: String?,
        gameVersion: String?,
        emulatorBackend: String?,
        driverFingerprint: String?
    ): String = listOf(
        device.manufacturer,
        device.model,
        device.androidVersion,
        device.sdkInt.toString(),
        device.cpuModel,
        device.cpuCores.toString(),
        device.totalRamMb.toString(),
        device.gpuVendor.orEmpty(),
        device.gpuRenderer.orEmpty(),
        gamePackage.orEmpty(),
        gameVersion.orEmpty(),
        emulatorBackend.orEmpty(),
        driverFingerprint.orEmpty()
    ).joinToString("¦")
}

object EmulatorBackendDetector {
    fun detect(): String? {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        return when {
            hardware.contains("ranchu") || hardware.contains("goldfish") ||
                fingerprint.contains("generic") -> "Android Emulator"
            else -> null
        }
    }
}

package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.OptimizationFingerprint
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo

internal object OptimizationContextKeyFactory {
    fun from(
        device: DeviceInfo,
        gamePackage: String?,
        gameVersion: String?,
        emulatorBackend: String?
    ): OptimizationContextKey {
        val driverFingerprint = driverFingerprint(device)
        return OptimizationContextKey(
            deviceFingerprint = OptimizationFingerprint.from(
                device = device,
                gamePackage = gamePackage,
                gameVersion = gameVersion,
                emulatorBackend = emulatorBackend,
                driverFingerprint = driverFingerprint
            ),
            gamePackage = gamePackage.orEmpty(),
            gameVersion = gameVersion,
            emulatorBackend = emulatorBackend,
            driverFingerprint = driverFingerprint
        )
    }

    internal fun driverFingerprint(device: DeviceInfo): String? {
        val gpuVendor = device.gpuVendor?.trim().orEmpty()
        val gpuRenderer = device.gpuRenderer?.trim().orEmpty()
        return if (gpuVendor.isBlank() && gpuRenderer.isBlank()) {
            null
        } else {
            "$gpuVendor|$gpuRenderer"
        }
    }
}

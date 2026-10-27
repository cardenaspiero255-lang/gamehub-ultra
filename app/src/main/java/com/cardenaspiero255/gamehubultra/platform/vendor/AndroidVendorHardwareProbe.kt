package com.cardenaspiero255.gamehubultra.platform.vendor

import android.os.Build

/**
 * CAR-55: Android-only, read-only SoC metadata boundary.
 * API 31 is the first supported public SOC_MANUFACTURER/SOC_MODEL surface.
 * Never interpret Build fields as access to Qualcomm/MediaTek/Exynos SDKs.
 */
internal object AndroidVendorHardwareProbe {
    fun observe(
        sdkInt: Int = Build.VERSION.SDK_INT,
        socReadout: () -> Pair<String?, String?> = {
            Build.SOC_MANUFACTURER to Build.SOC_MODEL
        }
    ): VendorHardwareObservation {
        val info = if (sdkInt >= Build.VERSION_CODES.S) {
            runCatching(socReadout).getOrNull()
        } else {
            null
        }
        return VendorHardwareObservation(
            socManufacturer = info?.first,
            socModel = info?.second,
            deviceManufacturer = Build.MANUFACTURER,
            gpuRenderer = null
        )
    }

    /**
     * Generic Android is always available. No optional vendor adapter is
     * installed by default, and this call never changes another app.
     */
    fun evaluate(
        adapters: List<VendorOptionalAdapter> = emptyList()
    ): VendorAdapterDecision = VendorAdapterFramework.evaluate(observe(), adapters)
}

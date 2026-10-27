package com.cardenaspiero255.gamehubultra.platform.vendor

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * CAR-55: Android-only, read-only SoC metadata boundary.
 * API 31 is the first supported public SOC_MANUFACTURER/SOC_MODEL surface.
 * Never interpret Build fields as access to Qualcomm/MediaTek/Exynos SDKs.
 */
internal object AndroidVendorHardwareProbe {
    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31SocFields {
        fun read(): Pair<String?, String?> =
            Build.SOC_MANUFACTURER to Build.SOC_MODEL
    }

    fun observe(
        sdkInt: Int = Build.VERSION.SDK_INT,
        socReadout: () -> Pair<String?, String?> = {
            // Do not resolve API-31 fields on older Android, even through a lambda.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Api31SocFields.read()
            } else {
                null to null
            }
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

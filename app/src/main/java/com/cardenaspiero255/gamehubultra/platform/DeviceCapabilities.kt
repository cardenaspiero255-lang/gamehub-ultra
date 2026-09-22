package com.cardenaspiero255.gamehubultra.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager

data class DeviceCapabilities(
    val sustainedPerformanceSupported: Boolean,
    val thermalStatusAvailable: Boolean
)

object DeviceCapabilitiesProvider {
    fun get(context: Context): DeviceCapabilities {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val sustained = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            powerManager?.isSustainedPerformanceModeSupported == true
        return DeviceCapabilities(
            sustainedPerformanceSupported = sustained,
            thermalStatusAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
    }
}

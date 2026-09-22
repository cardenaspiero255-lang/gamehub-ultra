package com.cardenaspiero255.gamehubultra.platform

import android.content.Context
import android.os.Build
import android.os.PowerManager

data class DeviceCapabilities(
    val sustainedPerformanceSupported: Boolean,
    val thermalStatusAvailable: Boolean,
    val performanceHintsAvailable: Boolean
)

object DeviceCapabilitiesProvider {
    fun get(context: Context): DeviceCapabilities {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return DeviceCapabilities(
            sustainedPerformanceSupported =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    powerManager?.isSustainedPerformanceModeSupported == true,
            thermalStatusAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            performanceHintsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSystemService("performance_hint") != null
        )
    }
}

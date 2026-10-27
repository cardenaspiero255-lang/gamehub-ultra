package com.cardenaspiero255.gamehubultra.platform.gpu

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * CAR-53: only Android's public graphics feature readouts are observed.
 * This does not load, switch, install or select any vendor drivers.
 */
internal interface GpuFeatureReadouts {
    fun openGlEsVersion(context: Context): String?
    fun vulkanAvailable(context: Context): Boolean?
    fun vulkanHardwareLevel(context: Context): Int?
}

internal object FrameworkGpuFeatureReadouts : GpuFeatureReadouts {
    override fun openGlEsVersion(context: Context): String? =
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.deviceConfigurationInfo?.glEsVersion
        }.getOrNull()

    override fun vulkanAvailable(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) false else runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        }.getOrNull()

    override fun vulkanHardwareLevel(context: Context): Int? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) null else runCatching {
            context.packageManager.systemAvailableFeatures
                ?.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
                ?.version?.takeIf { it >= 0 }
        }.getOrNull()
}

internal object AndroidGpuCapabilityProbe {
    fun read(
        context: Context,
        vendor: String?,
        renderer: String?,
        readouts: GpuFeatureReadouts = FrameworkGpuFeatureReadouts
    ): GpuCapabilityReport {
        val appContext = context.applicationContext
        return GpuCapabilityMatrix.from(
            GpuCapabilityObservation(
                vendor = vendor,
                renderer = renderer,
                openGlEsRawVersion = runCatching { readouts.openGlEsVersion(appContext) }.getOrNull(),
                vulkanHardwareLevel = runCatching { readouts.vulkanHardwareLevel(appContext) }.getOrNull(),
                vulkanFeatureAvailable = runCatching { readouts.vulkanAvailable(appContext) }.getOrNull(),
                // Public Android APIs here do not grant per-game GPU driver switching.
                verifiedDriverSelectionAvailable = false
            )
        )
    }
}

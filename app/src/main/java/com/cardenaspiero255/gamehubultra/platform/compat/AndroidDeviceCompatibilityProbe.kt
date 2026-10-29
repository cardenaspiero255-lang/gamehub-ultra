package com.cardenaspiero255.gamehubultra.platform.compat

import android.content.Context
import android.os.Build
import com.cardenaspiero255.gamehubultra.platform.GpuInfo
import com.cardenaspiero255.gamehubultra.platform.GpuInfoProvider
import com.cardenaspiero255.gamehubultra.platform.gpu.AndroidGpuCapabilityProbe
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.gpu.FrameworkGpuFeatureReadouts
import com.cardenaspiero255.gamehubultra.platform.gpu.GpuFeatureReadouts
import com.cardenaspiero255.gamehubultra.platform.vendor.AndroidVendorHardwareProbe
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorHardwareObservation
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorAdapterFramework
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import java.util.Locale

/** Observations only, not a device identifier, game compatibility proof or driver selection. */
data class CompatibilityDeviceSnapshot(
    val key: CompatibilityDeviceKey,
    val openGlEs: CapabilityEvidence,
    val vulkan: CapabilityEvidence,
    val deviceClassObserved: Boolean
) {
    val driverChanged: Boolean get() = false
    val alternateDriverSelectable: Boolean get() = false
}

/**
 * CAR-56: classify real Android public SoC metadata (API 31+) plus EGL GPU
 * renderer read in our own ephemeral GL context; query GLES/Vulkan feature
 * availability through CAR-53. No telephony IDs, Build.FINGERPRINT, raw model
 * storage, reflection, vendor SDKs or control over other games.
 */
internal object AndroidDeviceCompatibilityProbe {
    private val familyMarkers = listOf(
        CompatibilityGpuFamily.ADRENO to Regex("""\badreno\b"""),
        CompatibilityGpuFamily.MALI to Regex("""\bmali\b"""),
        CompatibilityGpuFamily.POWERVR to Regex("""\bpowervr\b""")
    )
    private val virtualRenderer =
        Regex("""\b(angle|swiftshader|llvmpipe|virgl|virtio|software|emulator)\b""")

    fun gpuFamilyOf(renderer: String?): CompatibilityGpuFamily {
        val value = renderer?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (value.isEmpty() || value.length > 96 || virtualRenderer.containsMatchIn(value)) {
            return CompatibilityGpuFamily.UNKNOWN
        }
        return familyMarkers.filter { (_, pattern) -> pattern.containsMatchIn(value) }
            .singleOrNull()?.first ?: CompatibilityGpuFamily.UNKNOWN
    }

    fun read(
        context: Context,
        socObservation: () -> VendorHardwareObservation = { AndroidVendorHardwareProbe.observe() },
        gpuReadout: () -> GpuInfo? = { GpuInfoProvider.get() },
        gpuReadouts: GpuFeatureReadouts = FrameworkGpuFeatureReadouts
    ): CompatibilityDeviceSnapshot {
        val soc = runCatching { socObservation() }.getOrNull()
        val vendorFamily = soc?.let(VendorAdapterFramework::detectFamily)
            ?: VendorSocFamily.GENERIC
        val gpu = runCatching { gpuReadout() }.getOrNull()
        val gpuFamily = gpuFamilyOf(gpu?.renderer)
        val api = Build.VERSION.SDK_INT
        val report = AndroidGpuCapabilityProbe.read(
            context,
            vendor = gpu?.vendor,
            renderer = gpu?.renderer,
            readouts = gpuReadouts
        )
        return CompatibilityDeviceSnapshot(
            key = CompatibilityDeviceKey(vendorFamily, gpuFamily, api),
            openGlEs = report.openGlEs,
            vulkan = report.vulkan,
            deviceClassObserved = vendorFamily != VendorSocFamily.GENERIC &&
                gpuFamily != CompatibilityGpuFamily.UNKNOWN
        )
    }
}

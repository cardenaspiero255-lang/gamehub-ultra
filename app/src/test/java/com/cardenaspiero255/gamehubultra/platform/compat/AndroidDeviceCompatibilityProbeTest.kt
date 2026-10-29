package com.cardenaspiero255.gamehubultra.platform.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cardenaspiero255.gamehubultra.platform.GpuInfo
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.gpu.GpuFeatureReadouts
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorHardwareObservation
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceCompatibilityProbeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val trustedSoc = VendorHardwareObservation("MediaTek", "Dimensity 1300")
    private val features = object : GpuFeatureReadouts {
        override fun openGlEsVersion(context: Context): String? = "3.2"
        override fun vulkanAvailable(context: Context): Boolean? = true
        override fun vulkanHardwareLevel(context: Context): Int? = 1
    }

    @Test fun observedEglGpuAndPublicAndroidFeaturesFormNonUniqueDeviceKey() {
        val observation = AndroidDeviceCompatibilityProbe.read(
            context,
            socObservation = { trustedSoc },
            gpuReadout = { GpuInfo("ARM", "Mali-G77 MC9") },
            gpuReadouts = features
        )
        assertEquals(VendorSocFamily.MEDIATEK, observation.key.socFamily)
        assertEquals(CompatibilityGpuFamily.MALI, observation.key.gpuFamily)
        assertEquals(35, observation.key.androidApiLevel)
        assertEquals(CapabilityEvidence.VERIFIED, observation.openGlEs)
        assertEquals(CapabilityEvidence.VERIFIED, observation.vulkan)
        assertTrue(observation.deviceClassObserved)
        assertFalse(observation.driverChanged)
        assertFalse(observation.alternateDriverSelectable)
    }

    @Test fun ambiguousEmulatedOrContradictoryRendererNeverCreatesTrustedGpuClass() {
        for (renderer in listOf(null, "Adreno Mali", "ANGLE (Adreno 740)", "SwiftShader", "llvmpipe")) {
            val result = AndroidDeviceCompatibilityProbe.read(
                context,
                socObservation = { trustedSoc },
                gpuReadout = { GpuInfo("GPU", renderer) },
                gpuReadouts = features
            )
            assertEquals(CompatibilityGpuFamily.UNKNOWN, result.key.gpuFamily)
            assertFalse(result.deviceClassObserved)
        }
    }

    @Test fun realAdrenoAndPowerVrRenderersAreClassifiedFromEglOnly() {
        assertEquals(CompatibilityGpuFamily.ADRENO,
            AndroidDeviceCompatibilityProbe.gpuFamilyOf("Adreno (TM) 750"))
        assertEquals(CompatibilityGpuFamily.POWERVR,
            AndroidDeviceCompatibilityProbe.gpuFamilyOf("PowerVR GE8320"))
        assertEquals(CompatibilityGpuFamily.UNKNOWN,
            AndroidDeviceCompatibilityProbe.gpuFamilyOf("Qualcomm GPU"))
    }

    @Test fun absentSoCMetadataOrGpuReadoutMustFailClosed() {
        val result = AndroidDeviceCompatibilityProbe.read(
            context,
            socObservation = { VendorHardwareObservation(null, null, "Samsung", "Adreno 740") },
            gpuReadout = { null },
            gpuReadouts = features
        )
        assertEquals(VendorSocFamily.GENERIC, result.key.socFamily)
        assertEquals(CompatibilityGpuFamily.UNKNOWN, result.key.gpuFamily)
        assertFalse(result.deviceClassObserved)
    }

    @Test fun unavailableVulkanAndFailedFeatureProbeNeverClaimsVerifiedVulkan() {
        val failing = object : GpuFeatureReadouts {
            override fun openGlEsVersion(context: Context): String? = null
            override fun vulkanAvailable(context: Context): Boolean? = throw SecurityException()
            override fun vulkanHardwareLevel(context: Context): Int? = null
        }
        val result = AndroidDeviceCompatibilityProbe.read(
            context,
            socObservation = { trustedSoc },
            gpuReadout = { GpuInfo("ARM", "Mali-G77") },
            gpuReadouts = failing
        )
        assertEquals(CapabilityEvidence.UNKNOWN, result.vulkan)
        assertEquals(CapabilityEvidence.UNKNOWN, result.openGlEs)
    }

    @Test fun unexpectedVendorOrEglExceptionsKeepGenericFallback() {
        val result = AndroidDeviceCompatibilityProbe.read(
            context,
            socObservation = { throw SecurityException("blocked") },
            gpuReadout = { throw IllegalStateException("EGL unavailable") },
            gpuReadouts = features
        )
        assertFalse(result.deviceClassObserved)
        assertEquals(VendorSocFamily.GENERIC, result.key.socFamily)
        assertEquals(CompatibilityGpuFamily.UNKNOWN, result.key.gpuFamily)
    }

    @Test fun defaultAndroidProbeIsReadOnlyAndNeverEnablesVendorMutations() {
        val observation = AndroidDeviceCompatibilityProbe.read(context)
        assertFalse(observation.driverChanged)
        assertFalse(observation.alternateDriverSelectable)
    }

    @Test fun realProbeNeverAuthenticatesRuleJustBecauseItWasStored() {
        val result = AndroidDeviceCompatibilityProbe.read(
            context,
            socObservation = { trustedSoc },
            gpuReadout = { GpuInfo("ARM", "Mali-G77") },
            gpuReadouts = features
        )
        assertFalse(result.driverChanged)
        assertFalse(result.alternateDriverSelectable)
        assertTrue(result.deviceClassObserved)
    }
}

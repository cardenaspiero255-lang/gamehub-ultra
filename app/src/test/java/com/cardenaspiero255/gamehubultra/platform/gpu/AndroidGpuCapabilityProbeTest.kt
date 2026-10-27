package com.cardenaspiero255.gamehubultra.platform.gpu

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidGpuCapabilityProbeTest {
    @Test fun injectedAndroidReadoutsCanReportVerifiedVulkanSafely() {
        val features = object : GpuFeatureReadouts {
            override fun openGlEsVersion(context: Context) = "3.2"
            override fun vulkanAvailable(context: Context) = true
            override fun vulkanHardwareLevel(context: Context) = 1
        }
        val report = AndroidGpuCapabilityProbe.read(
            ApplicationProvider.getApplicationContext(), "Qualcomm", "Adreno 740", features
        )
        assertEquals(CapabilityEvidence.VERIFIED, report.vulkan)
        assertEquals("3.2", report.openGlEsVersion)
        assertFalse(report.alternateDriverSelectable)
        assertFalse(report.driverWasChanged)
    }

    @Test fun failingAndroidApiCallsRemainUnknownRatherThanInventingSupport() {
        val features = object : GpuFeatureReadouts {
            override fun openGlEsVersion(context: Context): String? = error("Unavailable")
            override fun vulkanAvailable(context: Context): Boolean? = error("Unavailable")
            override fun vulkanHardwareLevel(context: Context): Int? = error("Unavailable")
        }
        val report = AndroidGpuCapabilityProbe.read(
            ApplicationProvider.getApplicationContext(), null, null, features
        )
        assertEquals(CapabilityEvidence.UNKNOWN, report.vulkan)
        assertEquals(CapabilityEvidence.UNKNOWN, report.openGlEs)
        assertFalse(report.alternateDriverSelectable)
    }

    @Test fun publicFrameworkReadoutsDoNotCrashOrLoadAnAlternateDriver() {
        val report = AndroidGpuCapabilityProbe.read(
            ApplicationProvider.getApplicationContext(), "GPU", "Renderer"
        )
        assertFalse(report.alternateDriverSelectable)
        assertFalse(report.driverWasChanged)
    }
}

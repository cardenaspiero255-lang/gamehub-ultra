package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VoiceOptimizationFeedbackContextRobolectricTest {
    @Test
    fun `blocking voice enrichment loads the Android scoped feedback context`() {
        val context = RuntimeEnvironment.getApplication()
        val device = DeviceInfo(
            manufacturer = "Vivo",
            model = "V25 Pro",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = listOf("arm64-v8a"),
            cpuModel = "Dimensity",
            cpuCores = 8,
            totalRamMb = 12_288,
            gpuVendor = "ARM",
            gpuRenderer = "Mali-G77"
        )
        val base = GameHubAiContext(
            selectedGamePackage = context.packageName,
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 12_288,
            gpuAvailable = true,
            thermalStatus = 0,
            thermalHeadroom = 0.2f,
            batteryPercent = 80,
            charging = false,
            refreshRateHz = 120f,
            networkValidated = true,
            networkLatencyMs = 30L,
            downstreamBandwidthKbps = 100_000L,
            storageFreePercent = 50,
            inputDeviceCount = 1,
            selectedProfile = PerformanceProfile.BALANCED,
            sessionActive = true
        )

        val enriched = VoiceOptimizationFeedbackContext.enrichBlockingOrBase(
            base = base,
            context = context,
            device = device
        )

        assertEquals(base, enriched)
    }
}

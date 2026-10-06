package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.EmulatorBackendDetector
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

        @Suppress("DEPRECATION")
        val gameVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
        val key = VoiceOptimizationFeedbackContext.buildContextKey(
            device = device,
            gamePackage = context.packageName,
            gameVersion = gameVersion,
            emulatorBackend = EmulatorBackendDetector.detect()
        )
        val store = GameOptimizationMemoryStore(context)
        val seeded = OptimizationObservation(
            contextKey = key.serialized,
            profile = PerformanceProfile.X4,
            timestampMillis = 123L,
            feedbackDecision = OptimizationFeedbackDecision.REJECTED
        )

        runBlocking {
            store.clearAll()
            store.record(key, seeded)
        }
        try {
            val enriched = VoiceOptimizationFeedbackContext.enrichBlockingOrBase(
                base = base,
                context = context,
                device = device
            )

            assertTrue(
                enriched.optimizationObservations.any {
                    it.id == seeded.id &&
                        it.feedbackDecision == OptimizationFeedbackDecision.REJECTED
                }
            )
        } finally {
            runBlocking { store.clearAll() }
        }
    }

    @Test
    fun `direct Android enrichment builds the scoped key path`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val device = DeviceInfo(
            manufacturer = "Test",
            model = "Device",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = listOf("arm64-v8a"),
            cpuModel = "cpu",
            cpuCores = 8,
            totalRamMb = 8192,
            gpuVendor = "ARM",
            gpuRenderer = "Mali"
        )
        val base = GameHubAiContext(
            selectedGamePackage = null,
            sustainedPerformanceSupported = true,
            cpuCores = 8,
            totalRamMb = 8192,
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
            sessionActive = false
        )
        val store = GameOptimizationMemoryStore(context)
        store.clearAll()

        try {
            val enriched = VoiceOptimizationFeedbackContext.enrich(
                base = base,
                context = context,
                device = device
            )

            assertTrue(enriched.optimizationObservations.isEmpty())
        } finally {
            store.clearAll()
        }
    }
}

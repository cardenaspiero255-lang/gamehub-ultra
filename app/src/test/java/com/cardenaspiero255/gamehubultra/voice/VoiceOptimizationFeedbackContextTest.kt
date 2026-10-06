package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import org.mockito.Mockito
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKeyFactory
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceOptimizationFeedbackContextTest {
    @Test
    fun `voice context carries scoped rejected and reverted recommendations`() = runBlocking {
        val key = OptimizationContextKey("device", "game.a", "1", null, "gpu")
        val history = listOf(
            OptimizationObservation(
                contextKey = key.serialized,
                profile = PerformanceProfile.X4,
                feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                timestampMillis = 2L
            ),
            OptimizationObservation(
                contextKey = key.serialized,
                profile = PerformanceProfile.FRAME_INTERPOLATION,
                feedbackDecision = OptimizationFeedbackDecision.REVERTED,
                timestampMillis = 1L
            )
        )
        val repository = object : GameOptimizationMemoryStateRepository {
            override fun observationsFlow(contextKey: OptimizationContextKey): Flow<List<OptimizationObservation>> =
                flowOf(if (contextKey == key) history else emptyList())

            override suspend fun record(
                contextKey: OptimizationContextKey,
                observation: OptimizationObservation
            ) = Unit

            override suspend fun pruneTo(contextKey: OptimizationContextKey) = Unit
            override suspend fun clearAll() = Unit
            override suspend fun clearGame(contextKey: OptimizationContextKey) = Unit
        }
        val base = GameHubAiContext(
            selectedGamePackage = "game.a",
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
            sessionActive = true
        )

        val enriched = VoiceOptimizationFeedbackContext.enrich(base, repository, key)

        assertEquals(history, enriched.optimizationObservations)
    }

    @Test
    fun `voice optimization key keeps game version backend and gpu scope`() {
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

        val key = VoiceOptimizationFeedbackContext.buildContextKey(
            device = device,
            gamePackage = "game.a",
            gameVersion = "42",
            emulatorBackend = "native"
        )

        assertEquals("game.a", key.gamePackage)
        assertEquals("42", key.gameVersion)
        assertEquals("native", key.emulatorBackend)
        assertEquals("ARM|Mali-G77", key.driverFingerprint)
    }

    @Test
    fun `voice optimization key omits blank gpu fingerprint safely`() {
        val device = DeviceInfo(
            manufacturer = "Test",
            model = "Device",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = emptyList(),
            cpuModel = "cpu",
            cpuCores = 4,
            totalRamMb = 4096,
            gpuVendor = null,
            gpuRenderer = null
        )

        val key = VoiceOptimizationFeedbackContext.buildContextKey(
            device = device,
            gamePackage = null,
            gameVersion = null,
            emulatorBackend = null
        )

        assertEquals("", key.gamePackage)
        assertEquals(null, key.driverFingerprint)
    }


    @Test
    fun `safe voice enrichment returns loaded context and falls back on failure`() {
        val base = GameHubAiContext(
            selectedGamePackage = "game.a",
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
            sessionActive = true
        )
        val loaded = base.copy(batteryPercent = 79)

        assertEquals(
            loaded,
            VoiceOptimizationFeedbackContext.enrichOrBase(base) { loaded }
        )
        assertEquals(
            base,
            VoiceOptimizationFeedbackContext.enrichOrBase(base) {
                error("storage unavailable")
            }
        )
    }

    @Test
    fun `voice and ui optimization keys share the same gpu normalization`() {
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
        val partialGpu = device.copy(gpuVendor = " ARM ", gpuRenderer = null)
        val expected = OptimizationContextKeyFactory.from(
            device = partialGpu,
            gamePackage = "com.example.game",
            gameVersion = "1.2.3",
            emulatorBackend = null
        )
        val actual = VoiceOptimizationFeedbackContext.buildContextKey(
            device = partialGpu,
            gamePackage = "com.example.game",
            gameVersion = "1.2.3",
            emulatorBackend = null
        )

        assertEquals("ARM|", expected.driverFingerprint)
        assertEquals(expected, actual)

        val emptyGpu = partialGpu.copy(gpuVendor = " ", gpuRenderer = null)
        assertEquals(
            null,
            OptimizationContextKeyFactory.from(
                device = emptyGpu,
                gamePackage = "com.example.game",
                gameVersion = null,
                emulatorBackend = null
            ).driverFingerprint
        )
    }


    @Test
    fun `android voice enrichment falls back when storage context is unavailable`() {
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
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)

        assertEquals(
            base,
            VoiceOptimizationFeedbackContext.enrichBlockingOrBase(base, context, device)
        )
    }

    @Test
    fun `android voice enrichment survives package metadata lookup failure`() {
        val base = GameHubAiContext(
            selectedGamePackage = "missing.package",
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
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(context)

        assertEquals(
            base,
            VoiceOptimizationFeedbackContext.enrichBlockingOrBase(base, context, device)
        )
    }

}

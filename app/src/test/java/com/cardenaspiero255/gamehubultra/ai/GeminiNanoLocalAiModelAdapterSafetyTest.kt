package com.cardenaspiero255.gamehubultra.ai

import android.os.Looper
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * No device ML Kit installation is required: the UI-thread boundary must
 * return before the lazy Generation.getClient() call can initialize.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GeminiNanoLocalAiModelAdapterSafetyTest {
    private val context = GameHubAiContext(
        selectedGamePackage = "com.example.test",
        sustainedPerformanceSupported = false,
        cpuCores = 4,
        totalRamMb = 4096,
        gpuAvailable = true,
        thermalStatus = null,
        thermalHeadroom = null,
        batteryPercent = null,
        charging = false,
        refreshRateHz = null,
        networkValidated = false,
        networkLatencyMs = null,
        downstreamBandwidthKbps = null,
        storageFreePercent = 40,
        inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED,
        sessionActive = false
    )

    @Test
    fun mainLooperNeverBlocksOnModelStatusGenerationChatOrSpeechIntent() {
        assertTrue(Looper.myLooper() == Looper.getMainLooper())
        val adapter = GeminiNanoLocalAiModelAdapter()
        try {
            val started = System.nanoTime()
            assertFalse(adapter.isAvailable())
            assertNull(adapter.advise("Recomienda un perfil", context))
            assertNull(adapter.chat("¿Cómo va el juego?", context, emptyList()))
            assertNull(adapter.interpretVoiceIntent("Abre Minecraft"))
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            assertTrue(elapsedMs < 1_000L, "UI requests must return without a model wait")
        } finally {
            adapter.close()
        }
    }

    @Test
    fun closedNanoAdapterNeverSchedulesAnyNewModelRequests() {
        val adapter = GeminiNanoLocalAiModelAdapter()
        adapter.close()
        adapter.close()
        assertFalse(adapter.isAvailable())
        assertNull(adapter.advise("X4", context))
        assertNull(adapter.chat("Hola", context, emptyList()))
        assertNull(adapter.interpretVoiceIntent("Activa X4"))
    }
}

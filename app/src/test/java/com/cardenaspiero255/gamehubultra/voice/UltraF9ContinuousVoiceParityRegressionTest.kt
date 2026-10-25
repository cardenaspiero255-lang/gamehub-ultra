package com.cardenaspiero255.gamehubultra.voice

import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regressions reported from physical API34 continuous listening versus one-shot microphone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9ContinuousVoiceParityRegressionTest {
    private fun <T> field(service: UltraWakeService, name: String): T =
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(service) as T

    private fun setField(service: UltraWakeService, name: String, value: Any?) {
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .set(service, value)
    }

    private fun bundle(text: String): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    @Test
    fun ttsRetiresPersistentMicBeforeOpeningStopOnlyRecognition() {
        val service = UltraWakeService()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val speech = Mockito.mock(SpeechRecognizer::class.java)
        setField(service, "recognizer", speech)
        setField(service, "persistentSessionActive", true)
        assertTrue(coordinator.tryStartRecognition())
        assertTrue(coordinator.tryBeginCommand(keepRecognitionActive = true))
        val retiredGeneration: Long = field(service, "recognitionGeneration")

        UltraWakeService::class.java.getDeclaredMethod("retirePersistentCaptureBeforeSpeaking")
            .apply { isAccessible = true }.invoke(service)

        assertFalse(field<Boolean>(service, "persistentSessionActive"))
        assertFalse(coordinator.isRecognitionActive())
        assertTrue(coordinator.isCommandRunning())
        assertTrue(field<SpeechRecognizer?>(service, "recognizer") == null)
        assertTrue(field<Long>(service, "recognitionGeneration") > retiredGeneration)
        Mockito.verify(speech).cancel()
        Mockito.verify(speech).destroy()
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
    }

    @Test
    fun nonStopFinalResultRearmsStopListenerWhileTtsContinues() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val handler = Mockito.mock(Handler::class.java)
        setField(service, "mainHandler", handler)
        assertTrue(coordinator.tryBeginCommand())
        guard.onPlaybackStarted("Una comparación larga que sigue hablando.")
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        setField(service, "recognitionGeneration", 12L)
        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration", Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val stopOnly = method.invoke(service, 12L, true) as RecognitionListener

        stopOnly.onResults(bundle("ruido de fondo sin orden de parada"))

        assertTrue(coordinator.isCommandRunning())
        assertFalse(coordinator.isRecognitionActive())
        assertTrue(guard.isPlaybackActive())
        Mockito.verify(handler).postDelayed(
            Mockito.any(Runnable::class.java), Mockito.eq(280L)
        )
    }

    @Test
    fun continuousX4SelectionMatchesOneShotEvenWithoutSustainedHardwareMode() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(
            DeviceCapabilitiesProvider.get(context).sustainedPerformanceSupported,
            "Emulator fixture must lack sustained-performance hardware support"
        )
        val repository = GameHubProductionComposition.selectionRepository(context)
        repository.saveSelectedGameAndProfile("com.example.game", PerformanceProfile.BALANCED)
        val aliases = Mockito.mock(GameAliasStateRepository::class.java)
        Mockito.`when`(aliases.aliases()).thenReturn(emptyMap())
        val runtime = UltraWakeCommandRuntime(
            context = context,
            queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
            aiAdvisor = GameHubAiAdvisor(),
            aliasRepository = aliases,
            memoryStore = Mockito.mock(UltraConversationMemoryStore::class.java),
            conversationLedger = UltraVoiceConversationLedger()
        )

        val answer = runtime.execute("Ultra, activa X4")

        assertContains(answer, "Perfil X4 seleccionado")
        assertFalse(answer.contains("no está disponible"), answer)
    }
    @Test
    fun failedStopOnlyRecognizerStartupRetriesWhileSpeechIsActive() {
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.create().get()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val handler = Mockito.mock(Handler::class.java)
        setField(service, "mainHandler", handler)
        val recognizer = Mockito.mock(SpeechRecognizer::class.java)
        Mockito.doThrow(IllegalStateException("ASR busy"))
            .`when`(recognizer).startListening(Mockito.any(Intent::class.java))
        val speechApi = Mockito.mockStatic(SpeechRecognizer::class.java)
        try {
            speechApi.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(service) }
                .thenReturn(true)
            speechApi.`when`<SpeechRecognizer> {
                SpeechRecognizer.createSpeechRecognizer(service)
            }.thenReturn(recognizer)
            assertTrue(coordinator.tryBeginCommand())
            guard.onPlaybackStarted("Continuo leyendo algo importante...")
            UltraWakeService::class.java.getDeclaredMethod(
                "startRecognition", Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }.invoke(service, true)

            assertTrue(coordinator.isCommandRunning())
            assertFalse(coordinator.isRecognitionActive())
            Mockito.verify(recognizer).destroy()
            Mockito.verify(handler).postDelayed(
                Mockito.any(Runnable::class.java), Mockito.eq(560L)
            )
        } finally {
            speechApi.close()
            controller.destroy()
        }
    }

}

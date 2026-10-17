package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.mockito.Mockito
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the Android service integration of consented TTS barge-in, including
 * partial-stop safety and generation invalidation. Hardware permission to
 * recognize while speaking still requires a real-device acceptance test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class UltraWakeSpeechInterruptionRobolectricTest {
    private fun <T> field(service: UltraWakeService, name: String): T =
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(service) as T

    private fun listener(service: UltraWakeService): RecognitionListener =
        field(service, "listener")

    private fun bundle(text: String): Bundle = Bundle().apply {
        putStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION,
            arrayListOf(text)
        )
    }

    @Test
    fun explicitStopPartialCompletesOnlyActiveSpeechAndEndsCommand() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("Un texto largo que Ultra está leyendo.")

        listener(service).onPartialResults(bundle("Ultra, para"))

        assertFalse(generation.isActive(token))
        assertFalse(coordinator.isCommandRunning())
    }

    @Test
    fun questionContainingParaCannotStopCurrentSpeech() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("El asistente está explicando algo.")

        listener(service).onPartialResults(bundle("Ultra, para qué sirve un procesador"))

        assertTrue(generation.isActive(token))
        assertTrue(coordinator.isCommandRunning())
    }

    @Test
    fun oldGenerationCannotStopSpeechThroughLatePartialResult() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("Respuesta actual.")
        UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
            .setLong(service, 3L)
        val listenerForGeneration =
            UltraWakeService::class.java.getDeclaredMethod(
                "listenerForGeneration", Long::class.javaPrimitiveType
            ).apply { isAccessible = true }
        val stale = listenerForGeneration.invoke(service, 2L) as RecognitionListener
        val current = listenerForGeneration.invoke(service, 3L) as RecognitionListener

        stale.onPartialResults(bundle("Ultra, para"))
        assertTrue(generation.isActive(token))

        current.onPartialResults(bundle("Ultra, para"))
        assertFalse(generation.isActive(token))
    }

    @Test
    fun interruptionRecognitionOnlyOpensWhenSpeechIsActuallyPlaying() {
        val service = UltraWakeService()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val start = UltraWakeService::class.java.getDeclaredMethod(
            "startRecognition", Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }

        start.invoke(service, true)
        assertFalse(coordinator.isRecognitionActive())
        assertTrue(coordinator.tryBeginCommand())
        start.invoke(service, true)
        assertFalse(coordinator.isRecognitionActive())
    }

    @Test
    fun consentedSpeakingSessionStartsOneLegacyRecognizerForStop() {
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildService(UltraWakeService::class.java)
        val service = controller.create().get()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val recognition = Mockito.mock(SpeechRecognizer::class.java)
        val speechApi = Mockito.mockStatic(SpeechRecognizer::class.java)
        try {
            speechApi.`when`<Boolean> { SpeechRecognizer.isRecognitionAvailable(service) }
                .thenReturn(true)
            speechApi.`when`<SpeechRecognizer> {
                SpeechRecognizer.createSpeechRecognizer(service)
            }.thenReturn(recognition)
            assertTrue(coordinator.tryBeginCommand())
            guard.onPlaybackStarted("Una respuesta de voz reproducida.")
            val schedule = UltraWakeService::class.java.getDeclaredMethod(
                "scheduleSpeechInterruptionListener"
            ).apply { isAccessible = true }
            schedule.invoke(service)

            // Drive the same scheduled Runnable deterministically; no physical
            // microphone is fabricated by this Robolectric test.
            val runnable: Runnable = field(service, "listenDuringSpeech")
            runnable.run()

            Mockito.verify(recognition).startListening(
                Mockito.any(Intent::class.java)
            )
            assertTrue(coordinator.isRecognitionActive())
        } finally {
            speechApi.close()
            controller.destroy()
        }
    }
}

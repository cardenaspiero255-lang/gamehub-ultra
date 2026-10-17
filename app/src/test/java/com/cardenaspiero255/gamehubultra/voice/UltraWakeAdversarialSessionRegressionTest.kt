package com.cardenaspiero255.gamehubultra.voice

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Adversarial ASR callback ordering; hardware acceptance remains separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class UltraWakeAdversarialSessionRegressionTest {
    private fun <T> field(service: UltraWakeService, name: String): T =
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(service) as T

    private fun listener(service: UltraWakeService, generation: Long): RecognitionListener =
        UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration", Long::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, generation) as RecognitionListener

    private fun transcript(value: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(value))
    }

    @Test
    fun staleFinalResultCannotEnqueueProfileActionDuringNewSession() {
        val service = UltraWakeService()
        val queue: UltraWakeCommandQueue = field(service, "commandQueue")
        val generationField = UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
        generationField.setLong(service, 41L)
        val stale = listener(service, 41L)
        generationField.setLong(service, 42L)
        stale.onResults(transcript("Ultra, activa X4"))
        assertTrue(queue.poll() == null, "Stale ASR must not enqueue X4")
    }

    @Test
    fun staleRecognizerBusyErrorCannotReleaseNewRecognition() {
        val service = UltraWakeService()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val generationField = UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
        generationField.setLong(service, 51L)
        val stale = listener(service, 51L)
        generationField.setLong(service, 52L)
        assertTrue(coordinator.tryStartRecognition())
        stale.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertTrue(coordinator.isRecognitionActive(), "Old error must not release current ASR")
    }

    @Test
    fun repeatedLateStopCannotInterruptNewSpeechGeneration() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val speech: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val generationField = UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
        generationField.setLong(service, 61L)
        val stale = listener(service, 61L)
        generationField.setLong(service, 62L)
        val active = speech.begin()
        guard.onPlaybackStarted("Respuesta nueva")
        repeat(3) { stale.onPartialResults(transcript("Ultra, para")) }
        assertTrue(speech.isActive(active), "Stale partial callbacks cannot stop new TTS")
    }

    @Test
    fun micCanRestartAfterRecognitionSessionFinishes() {
        val coordinator = UltraWakeCommandCoordinator()
        assertTrue(coordinator.tryStartRecognition())
        coordinator.onRecognitionFinished()
        assertFalse(coordinator.isRecognitionActive())
        assertTrue(coordinator.tryStartRecognition())
    }
}

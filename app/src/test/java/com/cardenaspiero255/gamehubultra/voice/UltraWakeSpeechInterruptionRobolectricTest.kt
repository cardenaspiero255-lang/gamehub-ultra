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

    @Test
    fun completedSpeechReleasesInFlightInterruptionRecognition() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("La respuesta ha terminado.")
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
            .setLong(service, 7L)

        UltraWakeService::class.java.getDeclaredMethod(
            "finishCommandAndResume",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, token, true)

        assertFalse(coordinator.isRecognitionActive())
        assertFalse(coordinator.isCommandRunning())
        val currentGeneration: Long = field(service, "recognitionGeneration")
        assertTrue(currentGeneration > 7L, "Old ASR callbacks must be retired")
        assertTrue(coordinator.tryStartRecognition(), "Mic must be available for next wake")
    }

    @Test
    fun interruptionOnlyFinalStopEndsSpeechWithoutQueueingActions() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val queue: UltraWakeCommandQueue = field(service, "commandQueue")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("Respuesta larga.")
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }.setLong(service, 17L)
        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val interrupt = method.invoke(service, 17L, true) as RecognitionListener
        interrupt.onResults(bundle("Ultra, para"))
        assertFalse(generation.isActive(token))
        assertFalse(coordinator.isCommandRunning())
        assertTrue(queue.poll() == null)
    }

    @Test
    fun repeatedInterruptionErrorsHaveBoundedRetries() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        guard.onPlaybackStarted("Respuesta larga.")
        // Verify the actual scheduling calls rather than relying on a
        // Robolectric Scheduler method unavailable under the current looper mode.
        val handler = Mockito.mock(android.os.Handler::class.java)
        UltraWakeService::class.java.getDeclaredField("mainHandler")
            .apply { isAccessible = true }.set(service, handler)
        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        for (attempt in 1..3) {
            assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
            UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
                .apply { isAccessible = true }.setLong(service, attempt.toLong())
            val interrupt = method.invoke(service, attempt.toLong(), true) as RecognitionListener
            interrupt.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            assertFalse(coordinator.isRecognitionActive())
        }
        val retries: Int = field(service, "speechInterruptionRetryCount")
        assertTrue(retries == 3, "Retry budget must be exhausted")
        val schedule = UltraWakeService::class.java.getDeclaredMethod(
            "scheduleSpeechInterruptionListener"
        ).apply { isAccessible = true }
        schedule.invoke(service)
        // Errors 1 and 2 each schedule one backed-off retry. Error 3 and
        // subsequent scheduling calls must not enqueue any new listening attempt.
        Mockito.verify(handler).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.eq(560L)
        )
        Mockito.verify(handler).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.eq(1120L)
        )
        Mockito.verify(handler, Mockito.times(2)).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.anyLong()
        )
    }

    @Test
    fun interruptionOnlyFinalResultCannotQueueAnExecutableProfileCommand() {
        val service = UltraWakeService()
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val queue: UltraWakeCommandQueue = field(service, "commandQueue")
        assertTrue(coordinator.tryBeginCommand())
        val token = generation.begin()
        guard.onPlaybackStarted("Una explicación de procesadores, sin instrucciones X4.")
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())
        UltraWakeService::class.java.getDeclaredField("recognitionGeneration")
            .apply { isAccessible = true }
            .setLong(service, 11L)
        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val interruptListener = method.invoke(service, 11L, true) as RecognitionListener

        interruptListener.onResults(bundle("Ultra, activa X4"))

        assertTrue(generation.isActive(token), "TTS should continue")
        assertTrue(coordinator.isCommandRunning(), "Current answer should not be replaced")
        assertTrue(queue.poll() == null, "Interrupt-only ASR must never queue actions")
        assertFalse(coordinator.isRecognitionActive())
    }
}

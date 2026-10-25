package com.cardenaspiero255.gamehubultra.voice

import android.content.Intent
import android.os.Handler
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito

/** Regression: "Juana, para" failed after OEM ASR gave three transient errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9JuanaStopRecoveryTest {
    private fun <T> field(service: UltraWakeService, name: String): T =
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(service) as T

    private fun setField(service: UltraWakeService, name: String, value: Any?) {
        UltraWakeService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.set(service, value)
    }

    @Test
    fun juanaStopMatchesButJuanaParaQueDoesNot() {
        val invocation = UltraWakeNamePolicy.canonicalizeInvocation("Juana, para", "juana")
        assertEquals("ultra para", invocation)
        assertTrue(UltraWakePartialStopPolicy.shouldStop(true, listOf(invocation)))
        assertFalse(
            UltraWakePartialStopPolicy.shouldStop(
                true, listOf(UltraWakeNamePolicy.canonicalizeInvocation("Juana, para qué sirve X4", "juana"))
            )
        )
        assertFalse(UltraWakePartialStopPolicy.shouldStop(false, listOf(invocation)))
    }

    @Test
    fun stopOnlyListenerContinuesAfterSevenRecognizerErrorsWhileTtsSpeaks() {
        val service = UltraWakeService()
        val handler = Mockito.mock(Handler::class.java)
        setField(service, "mainHandler", handler)
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        assertTrue(coordinator.tryBeginCommand())
        guard.onPlaybackStarted("Respuesta extensa que todavía se está reproduciendo.")
        val retry = UltraWakeService::class.java.getDeclaredMethod("scheduleSpeechInterruptionRetry")
            .apply { isAccessible = true }
        repeat(7) { retry.invoke(service) }
        Mockito.verify(handler, Mockito.times(7)).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.longThat { it in 280L..3000L }
        )
        assertTrue(guard.isPlaybackActive())
        assertTrue(coordinator.isCommandRunning())
    }

    @Test
    fun retryStopsWhenSpeechHasAlreadyFinished() {
        val service = UltraWakeService()
        val handler = Mockito.mock(Handler::class.java)
        setField(service, "mainHandler", handler)
        val retry = UltraWakeService::class.java.getDeclaredMethod("scheduleSpeechInterruptionRetry")
            .apply { isAccessible = true }
        repeat(5) { retry.invoke(service) }
        Mockito.verify(handler, Mockito.never()).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.anyLong()
        )
    }

    @Test
    fun notificationStopSpeechStopsTheAnswerButNotTheForegroundService() {
        val service = UltraWakeService()
        val handler = Mockito.mock(Handler::class.java)
        setField(service, "mainHandler", handler)
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val generation: UltraWakeSpeechGeneration = field(service, "speechGeneration")
        assertTrue(coordinator.tryBeginCommand())
        val activeToken = generation.begin()
        assertNotNull(activeToken)
        guard.onPlaybackStarted("Un discurso largo.")
        service.onStartCommand(Intent(UltraWakeService.ACTION_STOP_SPEECH), 0, 1)
        assertFalse(coordinator.isCommandRunning())
        assertFalse(guard.isPlaybackActive())
        assertFalse(field<Boolean>(service, "stopped"))
    }
    @Test
    fun persistentCaptureHandoffReleasesRecognizerBeforeTheStopOnlyWindow() {
        val service = UltraWakeService()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val recognizer = Mockito.mock(android.speech.SpeechRecognizer::class.java)
        assertTrue(coordinator.tryBeginCommand())
        setField(service, "persistentSessionActive", true)
        setField(service, "recognizer", recognizer)
        setField(service, "recognitionGeneration", 10L)

        val handoff = UltraWakeService::class.java.getDeclaredMethod(
            "retirePersistentCaptureBeforeSpeaking"
        ).apply { isAccessible = true }
        handoff.invoke(service)

        Mockito.verify(recognizer).cancel()
        Mockito.verify(recognizer).destroy()
        assertFalse(field<Boolean>(service, "persistentSessionActive"))
        assertEquals(null, field<android.speech.SpeechRecognizer?>(service, "recognizer"))
        assertEquals(11L, field<Long>(service, "recognitionGeneration"))
        assertTrue(coordinator.isCommandRunning())
        assertFalse(coordinator.isRecognitionActive())
    }

    @Test
    fun irrelevantFinalRecognitionDuringTtsReopensStopListenerWithoutLaunchingGame() {
        val service = UltraWakeService()
        val handler = Mockito.mock(Handler::class.java)
        val recognizer = Mockito.mock(android.speech.SpeechRecognizer::class.java)
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val guard: UltraWakePlaybackGuard = field(service, "playbackGuard")
        val queue: UltraWakeCommandQueue = field(service, "commandQueue")
        setField(service, "mainHandler", handler)
        setField(service, "recognizer", recognizer)
        setField(service, "recognitionGeneration", 20L)
        assertTrue(coordinator.tryBeginCommand())
        guard.onPlaybackStarted("La explicación sigue sonando.")
        assertTrue(coordinator.tryStartSpeechInterruptionRecognition())

        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val listener = method.invoke(service, 20L, true) as android.speech.RecognitionListener
        val result = android.os.Bundle().apply {
            putStringArrayList(
                android.speech.SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("Ultra abre Brawl Stars")
            )
        }
        listener.onResults(result)

        Mockito.verify(recognizer).cancel()
        Mockito.verify(recognizer).destroy()
        Mockito.verify(handler).postDelayed(
            Mockito.any(Runnable::class.java),
            Mockito.eq(280L)
        )
        assertEquals(21L, field<Long>(service, "recognitionGeneration"))
        assertFalse(coordinator.isRecognitionActive())
        assertTrue(coordinator.isCommandRunning())
        assertTrue(guard.isPlaybackActive())
        assertEquals(null, queue.poll())
    }

    @Test
    fun retiredStopListenerDoesNotProcessLateVoiceActions() {
        val service = UltraWakeService()
        val coordinator: UltraWakeCommandCoordinator = field(service, "commandCoordinator")
        val queue: UltraWakeCommandQueue = field(service, "commandQueue")
        assertTrue(coordinator.tryBeginCommand())
        val method = UltraWakeService::class.java.getDeclaredMethod(
            "listenerForGeneration",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        setField(service, "recognitionGeneration", 30L)
        val oldListener = method.invoke(service, 29L, true) as android.speech.RecognitionListener
        val result = android.os.Bundle().apply {
            putStringArrayList(
                android.speech.SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("Juana, abre BS")
            )
        }
        oldListener.onResults(result)
        assertTrue(coordinator.isCommandRunning())
        assertEquals(30L, field<Long>(service, "recognitionGeneration"))
        assertEquals(null, queue.poll())
    }

}

package com.cardenaspiero255.gamehubultra.voice

import android.content.Intent
import android.os.Handler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito

/** Regression: "Juana, para" failed after OEM ASR gave three transient errors. */
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
}

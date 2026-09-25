package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraWakePlaybackGuardTest {
    @Test
    fun recognitionStaysSuppressedWhileTtsIsPlaying() {
        val guard = UltraWakePlaybackGuard(drainWindowMillis = 1_200L)

        guard.onPlaybackStarted()

        assertTrue(guard.shouldSuppressRecognition(nowMillis = 5_000L))
    }

    @Test
    fun recognitionRemainsSuppressedWhileFinalTtsSegmentDrains() {
        val guard = UltraWakePlaybackGuard(drainWindowMillis = 1_200L)

        guard.onPlaybackStarted()
        guard.onPlaybackFinished(nowMillis = 10_000L)

        assertTrue(guard.shouldSuppressRecognition(nowMillis = 11_199L))
        assertFalse(guard.shouldSuppressRecognition(nowMillis = 11_201L))
    }

    @Test
    fun watchdogDoesNotFinishACommandWhileTtsIsStillSpeaking() {
        val guard = UltraWakePlaybackGuard()

        assertEquals(
            UltraWakeSpeechTimeoutAction.WAIT,
            guard.timeoutAction(isSpeaking = true, elapsedMillis = 10_000L)
        )
        assertEquals(
            UltraWakeSpeechTimeoutAction.FINISH,
            guard.timeoutAction(isSpeaking = false, elapsedMillis = 10_000L)
        )
    }

    @Test
    fun watchdogEventuallyStopsAStuckUtteranceBeforeFinishing() {
        val guard = UltraWakePlaybackGuard(hardTimeoutMillis = 90_000L)

        assertEquals(
            UltraWakeSpeechTimeoutAction.STOP_AND_FINISH,
            guard.timeoutAction(isSpeaking = true, elapsedMillis = 90_000L)
        )
    }

    @Test
    fun stoppedServiceRejectsLateRecognitionCallbacks() {
        val gate = UltraWakeLifecycleGate()

        assertTrue(gate.canAcceptRecognition())
        gate.stop()
        assertFalse(gate.canAcceptRecognition())
    }
}

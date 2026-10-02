package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceRecognitionStartGuardTest {
    @Test
    fun recognizerStartupExceptionCleansUpAndIsReportedInsteadOfEscaping() {
        var error: Int? = null
        val listeningStates = mutableListOf<Boolean>()
        var cleanupCalls = 0

        val started = VoiceRecognitionStartGuard.run(
            onListeningChanged = listeningStates::add,
            onError = { error = it },
            cleanup = { cleanupCalls += 1 }
        ) {
            throw IllegalStateException("recognizer service unavailable")
        }

        assertFalse(started)
        assertEquals(1, cleanupCalls)
        assertEquals(listOf(false), listeningStates)
        assertEquals(SpeechRecognizer.ERROR_CLIENT, error)
    }

    @Test
    fun cleanupFailureCannotEscapeRecognizerFailureRecovery() {
        var error: Int? = null
        val listeningStates = mutableListOf<Boolean>()

        val started = VoiceRecognitionStartGuard.run(
            onListeningChanged = listeningStates::add,
            onError = { error = it },
            cleanup = { throw IllegalStateException("vendor cleanup failed") }
        ) {
            throw IllegalStateException("recognizer startup failed")
        }

        assertFalse(started)
        assertEquals(listOf(false), listeningStates)
        assertEquals(SpeechRecognizer.ERROR_CLIENT, error)
    }

    @Test
    fun successfulStartupReturnsTrueWithoutCleanupOrError() {
        var error: Int? = null
        var invoked = false
        var cleanupCalls = 0
        val listeningStates = mutableListOf<Boolean>()

        val started = VoiceRecognitionStartGuard.run(
            onListeningChanged = listeningStates::add,
            onError = { error = it },
            cleanup = { cleanupCalls += 1 }
        ) {
            invoked = true
        }

        assertTrue(started)
        assertTrue(invoked)
        assertEquals(0, cleanupCalls)
        assertTrue(listeningStates.isEmpty())
        assertEquals(null, error)
    }
}

package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VoiceRecognitionStartGuardTest {
    @Test
    fun recognizerStartupExceptionIsReportedInsteadOfEscaping() {
        var error: Int? = null

        val started = VoiceRecognitionStartGuard.run(
            onError = { error = it }
        ) {
            throw IllegalStateException("recognizer service unavailable")
        }

        assertFalse(started)
        assertEquals(SpeechRecognizer.ERROR_CLIENT, error)
    }
}

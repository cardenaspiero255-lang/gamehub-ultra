package com.cardenaspiero255.gamehubultra.voice

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class VoiceAssistantControllerPartialResultTest {
    @Test
    fun partialResultIsForwardedWithoutEndingListeningSession() {
        val listeningStates = mutableListOf<Boolean>()
        val partials = mutableListOf<String>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = listeningStates::add,
            onTranscript = {},
            onError = {},
            onPartialTranscript = partials::add
        )

        val listenerField = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
        val listener = listenerField.get(controller) as RecognitionListener
        listener.onPartialResults(
            Bundle().apply {
                putStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                    arrayListOf("Ultra abre", "Ultra")
                )
            }
        )

        assertEquals(listOf("Ultra abre"), partials)
        assertEquals(emptyList(), listeningStates)
        controller.release()
    }
}

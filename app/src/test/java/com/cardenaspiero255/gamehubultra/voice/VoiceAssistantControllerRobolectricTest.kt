package com.cardenaspiero255.gamehubultra.voice

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class VoiceAssistantControllerRobolectricTest {
    @Test
    fun languageErrorSwitchesControllerToSpanishFallbackWithoutEscaping() {
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )

        val listenerField = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
        val listener = listenerField.get(controller) as RecognitionListener

        listener.onError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)

        val languageField = VoiceAssistantController::class.java
            .getDeclaredField("recognitionLanguageTag")
            .apply { isAccessible = true }

        assertEquals(UltraSpeechLocalePolicy.FALLBACK_TAG, languageField.get(controller))
    }

    @Test
    fun releasedControllerIgnoresLateRecognizerCallbacksAndCannotRestart() {
        val listeningEvents = mutableListOf<Boolean>()
        val errors = mutableListOf<Int>()
        val transcripts = mutableListOf<String>()
        val partials = mutableListOf<String>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = listeningEvents::add,
            onTranscript = transcripts::add,
            onError = errors::add,
            onPartialTranscript = partials::add,
        )
        val listener = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
            .get(controller) as RecognitionListener

        controller.release()
        controller.release() // terminal and idempotent
        listener.onError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        listener.onError(SpeechRecognizer.ERROR_CLIENT)
        listener.onEndOfSpeech()
        listener.onResults(android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("a late transcript that must be ignored")
            )
        })
        listener.onPartialResults(android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("late partial")
            )
        })
        controller.startListening()
        controller.stopListening()
        controller.speak("Do not resurrect TTS after release")

        assertEquals(listOf(false), listeningEvents)
        assertEquals(emptyList(), errors)
        assertEquals(emptyList(), transcripts)
        assertEquals(emptyList(), partials)
        val closedField = VoiceAssistantController::class.java
            .getDeclaredField("released")
            .apply { isAccessible = true }
        assertEquals(true, closedField.get(controller))
    }

}

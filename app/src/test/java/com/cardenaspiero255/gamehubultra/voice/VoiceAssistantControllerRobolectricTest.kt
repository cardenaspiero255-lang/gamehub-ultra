package com.cardenaspiero255.gamehubultra.voice

import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
    fun nonLanguageRecognitionErrorIsDeliveredOnceWithoutRecursion() {
        var calls = 0
        var deliveredError: Int? = null
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = { error ->
                calls += 1
                deliveredError = error
            }
        )

        val listenerField = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
        val listener = listenerField.get(controller) as RecognitionListener

        listener.onError(SpeechRecognizer.ERROR_NO_MATCH)

        assertEquals(1, calls)
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, deliveredError)
        assertFalse(calls > 1)
    }
}

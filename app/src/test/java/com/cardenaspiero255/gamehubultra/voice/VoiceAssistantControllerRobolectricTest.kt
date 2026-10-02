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
}

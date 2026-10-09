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
    fun activeControllerForwardsRecognitionEventsBeforeRelease() {
        val listeningEvents = mutableListOf<Boolean>()
        val transcripts = mutableListOf<String>()
        val partials = mutableListOf<String>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = listeningEvents::add,
            onTranscript = transcripts::add,
            onError = {},
            onPartialTranscript = partials::add,
        )
        val listener = VoiceAssistantController::class.java
            .getDeclaredField("listener")
            .apply { isAccessible = true }
            .get(controller) as RecognitionListener

        // Exercise the active side of the lifecycle guards. The other test
        // exercises these same callbacks after release() has become terminal.
        listener.onEndOfSpeech()
        listener.onResults(android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("que es una estrella")
            )
        })
        listener.onPartialResults(android.os.Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("que es")
            )
        })

        assertEquals(listOf(false, false), listeningEvents)
        assertEquals(listOf("que es una estrella"), transcripts)
        assertEquals(listOf("que es"), partials)
        controller.release()
    }

    @Test
    fun activeControllerCanAttemptListeningStopAndQueuedRetryBeforeRelease() {
        val listeningEvents = mutableListOf<Boolean>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = listeningEvents::add,
            onTranscript = {},
            onError = {},
        )
        // Robolectric may have no installed recognizer or microphone grant.
        // The startup guard must contain either condition in Ultra's error channel.
        // This intentionally tests the pre-release paths, not a hardware microphone.
        controller.startListening()
        controller.stopListening()

        // The retry runnable was captured before release; while the controller
        // is active it still delegates to the guarded recognizer startup.
        val pendingRetry = VoiceAssistantController::class.java
            .getDeclaredField("fallbackRetry")
            .apply { isAccessible = true }
            .get(controller) as Runnable
        pendingRetry.run()

        // Test the live speak() guard independently of Robolectric's TTS engine,
        // retaining the original instance so release() can clean it up.
        val ttsField = VoiceAssistantController::class.java
            .getDeclaredField("tts")
            .apply { isAccessible = true }
        val tts = ttsField.get(controller)
        try {
            ttsField.set(controller, null)
            controller.speak("prueba de voz")
        } finally {
            ttsField.set(controller, tts)
            controller.release()
        }
        kotlin.test.assertTrue(listeningEvents.isNotEmpty())
    }


    @Test
    fun releaseCleansSpeechAndTtsEvenWhenListeningCallbackThrows() {
        val recognizer = org.mockito.Mockito.mock(SpeechRecognizer::class.java)
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = { throw IllegalStateException("bad callback") },
            onTranscript = {},
            onError = {}
        )
        val rField = VoiceAssistantController::class.java
            .getDeclaredField("recognizer").apply { isAccessible = true }
        val tField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (tField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        rField.set(controller, recognizer)
        tField.set(controller, speech)

        controller.release()
        controller.release()
        org.mockito.Mockito.verify(recognizer, org.mockito.Mockito.times(1)).destroy()
        org.mockito.Mockito.verify(speech, org.mockito.Mockito.times(1)).stop()
        org.mockito.Mockito.verify(speech, org.mockito.Mockito.times(1)).shutdown()
        kotlin.test.assertNull(rField.get(controller))
        kotlin.test.assertNull(tField.get(controller))
        val gate = VoiceAssistantController::class.java
            .getDeclaredField("fallbackRetryGate").apply { isAccessible = true }
            .get(controller) as VoiceRecognitionRetryGate
        kotlin.test.assertFalse(gate.trySchedule())
    }

    @Test
    fun releaseStillShutsDownTtsIfRecognizerDestroyThrows() {
        val recognizer = org.mockito.Mockito.mock(SpeechRecognizer::class.java)
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        org.mockito.Mockito.doThrow(IllegalStateException("destroy failure"))
            .`when`(recognizer).destroy()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )
        val rField = VoiceAssistantController::class.java
            .getDeclaredField("recognizer").apply { isAccessible = true }
        val tField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (tField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        rField.set(controller, recognizer)
        tField.set(controller, speech)
        controller.release()
        org.mockito.Mockito.verify(recognizer).destroy()
        org.mockito.Mockito.verify(speech).stop()
        org.mockito.Mockito.verify(speech).shutdown()
        kotlin.test.assertNull(rField.get(controller))
        kotlin.test.assertNull(tField.get(controller))
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
        // Simulate already-queued callbacks that run AFTER release. Both paths
        // must return before starting another recognizer or forwarding errors.
        val lateFallbackRetry = VoiceAssistantController::class.java
            .getDeclaredField("fallbackRetry")
            .apply { isAccessible = true }
            .get(controller) as Runnable
        lateFallbackRetry.run()
        VoiceAssistantController::class.java
            .getDeclaredMethod("startListeningWithCurrentLanguage")
            .apply { isAccessible = true }
            .invoke(controller)
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

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
    private fun markSpeechInitialized(controller: VoiceAssistantController) {
        val gate = VoiceAssistantController::class.java
            .getDeclaredField("speechInitGate")
            .apply { isAccessible = true }
            .get(controller) as UltraTtsInitializationGate
        gate.initialized(true)
    }

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
    fun detailedComparisonQueuesBoundedTtsChunksInTheVisibleAssistant() {
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        val ttsField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (ttsField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        ttsField.set(controller, speech)
        val answer = "La primera fuente informa las características. ".repeat(115)
        try {
            markSpeechInitialized(controller)
            controller.speak(answer)
            val invocations = org.mockito.Mockito.mockingDetails(speech)
                .invocations.filter { it.method.name == "speak" }
            kotlin.test.assertTrue(invocations.size >= 3)
            assertEquals(android.speech.tts.TextToSpeech.QUEUE_FLUSH, invocations.first().arguments[1])
            kotlin.test.assertTrue(invocations.drop(1).all {
                it.arguments[1] == android.speech.tts.TextToSpeech.QUEUE_ADD
            })
            kotlin.test.assertTrue(invocations.all {
                (it.arguments[0] as String).length <= UltraSpeechChunker.DEFAULT_CHUNK_CHARS
            })
            assertEquals(answer.trim(), invocations.joinToString(" ") {
                it.arguments[0] as String
            })
            kotlin.test.assertTrue(invocations.map { it.arguments[3] }.distinct().size ==
                invocations.size)
        } finally {
            controller.release()
        }
    }

    @Test
    fun visibleAssistantStopsPendingSpeechIfAndroidTtsRejectsALongAnswer() {
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        org.mockito.Mockito.doReturn(android.speech.tts.TextToSpeech.ERROR)
            .`when`(speech).speak(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.anyString()
            )
        val ttsField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (ttsField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        ttsField.set(controller, speech)
        try {
            markSpeechInitialized(controller)
            controller.speak("Una comparación extensa con varias fuentes. ".repeat(140))
            val commands = org.mockito.Mockito.mockingDetails(speech)
                .invocations.filter { it.method.name == "speak" }
            assertEquals(1, commands.size)
            org.mockito.Mockito.verify(speech, org.mockito.Mockito.atLeastOnce()).stop()
        } finally {
            controller.release()
        }
    }

    @Test
    fun firstResponseIsSpokenAfterTtsBecomesReadyAndNeverAgainAfterRelease() {
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        val engineField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (engineField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        engineField.set(controller, speech)
        val readinessField = VoiceAssistantController::class.java
            .getDeclaredField("speechInitGate").apply { isAccessible = true }
        readinessField.set(controller, UltraTtsInitializationGate())
        val initialized = VoiceAssistantController::class.java
            .getDeclaredMethod("handleTtsInitialization", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        try {
            controller.speak("Compara ambos teléfonos sin perder esta respuesta.")
            kotlin.test.assertTrue(org.mockito.Mockito.mockingDetails(speech)
                .invocations.none { it.method.name == "speak" })
            initialized.invoke(controller, android.speech.tts.TextToSpeech.SUCCESS)
            assertEquals(1, org.mockito.Mockito.mockingDetails(speech)
                .invocations.count { it.method.name == "speak" })
            controller.release()
            initialized.invoke(controller, android.speech.tts.TextToSpeech.SUCCESS)
            assertEquals(1, org.mockito.Mockito.mockingDetails(speech)
                .invocations.count { it.method.name == "speak" })
        } finally {
            controller.release()
        }
    }

    @Test
    fun failedTtsInitializationDropsPendingSpeechWithoutSpeaking() {
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = {},
            onTranscript = {},
            onError = {}
        )
        val speech = org.mockito.Mockito.mock(android.speech.tts.TextToSpeech::class.java)
        val engineField = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (engineField.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        engineField.set(controller, speech)
        val readinessField = VoiceAssistantController::class.java
            .getDeclaredField("speechInitGate").apply { isAccessible = true }
        readinessField.set(controller, UltraTtsInitializationGate())
        try {
            controller.speak("Consulta que no puede reproducirse.")
            VoiceAssistantController::class.java
                .getDeclaredMethod("handleTtsInitialization", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(controller, android.speech.tts.TextToSpeech.ERROR)
            controller.speak("Otro mensaje después del error")
            kotlin.test.assertTrue(org.mockito.Mockito.mockingDetails(speech)
                .invocations.none { it.method.name == "speak" })
        } finally {
            controller.release()
        }
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
    fun releaseWithoutTtsStillClosesControllerAndNotifiesOnce() {
        val changes = mutableListOf<Boolean>()
        val controller = VoiceAssistantController(
            context = RuntimeEnvironment.getApplication(),
            onListeningChanged = changes::add,
            onTranscript = {},
            onError = {}
        )
        // Cover the absent-TTS path after the engine has already shut down.
        // The other tests exercise stop() and shutdown() on a live TTS.
        val field = VoiceAssistantController::class.java
            .getDeclaredField("tts").apply { isAccessible = true }
        (field.get(controller) as? android.speech.tts.TextToSpeech)?.shutdown()
        field.set(controller, null)
        controller.release()
        controller.release()
        kotlin.test.assertNull(field.get(controller))
        assertEquals(listOf(false), changes)
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

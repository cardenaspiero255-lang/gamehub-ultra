package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

class VoiceAssistantController(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onTranscript: (String) -> Unit,
    private val onError: (Int) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var recognitionLanguageTag = UltraSpeechLocalePolicy.PREFERRED_TAG

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let(UltraSpeechLocalePolicy::applyTo)
            }
        }
    }

    fun startListening() {
        recognitionLanguageTag = UltraSpeechLocalePolicy.PREFERRED_TAG
        VoiceRecognitionStartGuard.run(onListeningChanged, onError, { runCatching { recognizer?.destroy() }; recognizer = null }) { startListeningWithCurrentLanguage() }
    }

    private fun startListeningWithCurrentLanguage() {
        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)

        if (!VoicePermissionGate.canStartRecognition(microphoneGranted, recognitionAvailable)) {
            onError(
                if (!microphoneGranted) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                } else {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                }
            )
            return
        }

        recognizer?.cancel()
        recognizer?.destroy()
        val speech = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speech
        speech.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        onListeningChanged(true)
        speech.startListening(intent)
    }

    fun stopListening() {
        recognizer?.cancel()
        onListeningChanged(false)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gamehub-ultra-voice")
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onListeningChanged(false)

        override fun onResults(results: Bundle?) {
            onListeningChanged(false)
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                ?.let(onTranscript)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            val fallback = UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = error,
                currentTag = recognitionLanguageTag
            )
            if (fallback != null) {
                recognitionLanguageTag = fallback
                VoiceRecognitionStartGuard.run(onListeningChanged, this@VoiceAssistantController.onError, { runCatching { recognizer?.destroy() }; recognizer = null }) { startListeningWithCurrentLanguage() }
                return
            }
            onListeningChanged(false)
            this@VoiceAssistantController.onError(error)
        }
    }
}

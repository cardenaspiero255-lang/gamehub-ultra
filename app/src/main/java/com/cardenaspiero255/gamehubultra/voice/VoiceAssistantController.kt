package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat

internal object VoiceRecognitionErrorForwarder {
    fun forward(
        error: Int,
        onListeningChanged: (Boolean) -> Unit,
        onError: (Int) -> Unit
    ) {
        onListeningChanged(false)
        onError(error)
    }
}

class VoiceAssistantController(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onTranscript: (String) -> Unit,
    private val onError: (Int) -> Unit,
    private val onPartialTranscript: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var recognitionLanguageTag = UltraSpeechLocalePolicy.PREFERRED_TAG
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fallbackRetryGate = VoiceRecognitionRetryGate()
    private val fallbackRetry = Runnable {
        fallbackRetryGate.onRetryDispatched()
        VoiceRecognitionStartGuard.run(
            onListeningChanged = onListeningChanged,
            onError = onError,
            cleanup = {
                runCatching { recognizer?.destroy() }
                recognizer = null
            }
        ) {
            startListeningWithCurrentLanguage()
        }
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let(UltraSpeechLocalePolicy::applyTo)
            }
        }
    }

    fun startListening() {
        mainHandler.removeCallbacks(fallbackRetry)
        fallbackRetryGate.reset()
        recognitionLanguageTag = UltraSpeechLocalePolicy.PREFERRED_TAG
        VoiceRecognitionStartGuard.run(
            onListeningChanged = onListeningChanged,
            onError = onError,
            cleanup = {
                runCatching { recognizer?.destroy() }
                recognizer = null
            }
        ) {
            startListeningWithCurrentLanguage()
        }
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
            }
        }
        onListeningChanged(true)
        speech.startListening(intent)
    }

    fun stopListening() {
        mainHandler.removeCallbacks(fallbackRetry)
        fallbackRetryGate.reset()
        recognizer?.cancel()
        onListeningChanged(false)
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gamehub-ultra-voice")
    }

    fun release() {
        mainHandler.removeCallbacks(fallbackRetry)
        fallbackRetryGate.reset()
        onListeningChanged(false)
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
            val alternatives = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val confidenceScores = results
                ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            UltraSpeechCandidateRanker.select(
                alternatives = alternatives,
                confidenceScores = confidenceScores
            )?.let(onTranscript)
        }

        override fun onPartialResults(partialResults: Bundle?) =
            VoicePartialTranscriptForwarder.forward(
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                onPartialTranscript
            )
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            val fallback = UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = error,
                currentTag = recognitionLanguageTag
            )
            if (fallback != null) {
                recognitionLanguageTag = fallback
                onListeningChanged(false)
                if (fallbackRetryGate.trySchedule()) {
                    if (!mainHandler.post(fallbackRetry)) {
                        fallbackRetryGate.onRetryDispatched()
                        this@VoiceAssistantController.onError(error)
                    }
                }
                return
            }
            VoiceRecognitionErrorForwarder.forward(
                error = error,
                onListeningChanged = onListeningChanged,
                onError = this@VoiceAssistantController.onError
            )
        }
    }
}

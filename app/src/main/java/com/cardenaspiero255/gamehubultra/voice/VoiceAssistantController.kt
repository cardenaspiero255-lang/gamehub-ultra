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
    private val speechInitGate = UltraTtsInitializationGate()
    private val privacyRecognition = UltraRecognitionPrivacyGate()
    @Volatile private var released = false
    private val fallbackRetry = Runnable {
        if (released) return@Runnable
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
            // Dispatch after the constructor assigns tts. A late callback is
            // harmless after the controller has been released.
            mainHandler.post { handleTtsInitialization(status) }
        }
    }

    private fun handleTtsInitialization(status: Int) {
        if (released) return
        val success = status == TextToSpeech.SUCCESS
        if (success) {
            tts?.let(UltraSpeechLocalePolicy::applyTo)
        }
        speechInitGate.initialized(success)?.let(::speak)
    }

    fun startListening() {
        if (released) return
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
        if (released) return
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
        speech.setRecognitionListener(recognitionListener(privacyRecognition.start()))
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
        privacyRecognition.stop()
        if (released) return
        mainHandler.removeCallbacks(fallbackRetry)
        fallbackRetryGate.reset()
        recognizer?.cancel()
        onListeningChanged(false)
    }

    fun speak(text: String) {
        if (released) return
        val readyText = speechInitGate.offer(text) ?: return
        val engine = tts ?: return
        val chunks = UltraSpeechChunker.chunk(readyText)
        val token = System.nanoTime()
        chunks.forEachIndexed { index, segment ->
            val result = engine.speak(
                segment,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "gamehub-ultra-voice-$token-$index"
            )
            if (result == TextToSpeech.ERROR) {
                // Never leave half an explanation queued after a TTS failure.
                engine.stop()
                return
            }
        }
    }

    fun release() {
        privacyRecognition.stop()
        if (released) return
        released = true
        speechInitGate.close()
        // Make release terminal before calling any client or platform callbacks.
        fallbackRetryGate.close()
        runCatching { mainHandler.removeCallbacks(fallbackRetry) }
        // Every cleanup step must execute even if a previous callback throws.
        runCatching { onListeningChanged(false) }
        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.destroy() }
        val oldTts = tts
        tts = null
        runCatching { oldTts?.stop() }
        runCatching { oldTts?.shutdown() }
    }

    /** Test seam: uses the same generation-fenced listener as production recognition. */
    internal fun recognitionListenerForTest(): RecognitionListener =
        recognitionListener(privacyRecognition.start())

    private fun recognitionListener(
        token: UltraRecognitionPrivacyGate.Token
    ): RecognitionListener = object : RecognitionListener {
        private fun valid(): Boolean = !released && privacyRecognition.isCurrent(token)
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (valid()) onListeningChanged(false)
        }

        override fun onResults(results: Bundle?) {
            if (!valid()) return
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

        override fun onPartialResults(partialResults: Bundle?) {
            if (!valid()) return
            VoicePartialTranscriptForwarder.forward(
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
                onPartialTranscript
            )
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (!valid()) return
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

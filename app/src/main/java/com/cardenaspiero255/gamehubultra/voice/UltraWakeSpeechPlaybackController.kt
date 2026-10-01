package com.cardenaspiero255.gamehubultra.voice

import android.os.Handler
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

internal class UltraWakeSpeechPlaybackController(
    private val mainHandler: Handler,
    private val speechGeneration: UltraWakeSpeechGeneration,
    private val playbackGuard: UltraWakePlaybackGuard,
    private val onPlaybackFinished: (Long) -> Unit
) {
    private var tts: TextToSpeech? = null

    fun attach(textToSpeech: TextToSpeech) {
        tts = textToSpeech
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finishFromCallback(utteranceId)

                @Deprecated("Android legacy TextToSpeech callback")
                override fun onError(utteranceId: String?) = finishFromCallback(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) =
                    finishFromCallback(utteranceId)
            }
        )
    }

    fun speak(response: String) {
        val token = speechGeneration.begin()
        val startedAtMillis = System.currentTimeMillis()
        playbackGuard.onPlaybackStarted(response)
        val result = tts?.speak(
            response,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "$COMMAND_UTTERANCE_PREFIX-$token"
        ) ?: TextToSpeech.ERROR

        if (result == TextToSpeech.ERROR) {
            complete(token)
            return
        }
        scheduleWatchdog(token, startedAtMillis, COMMAND_SPEECH_TIMEOUT_MS)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun finishFromCallback(utteranceId: String?) {
        speechToken(utteranceId)?.let { token ->
            mainHandler.post { complete(token) }
        }
    }

    private fun scheduleWatchdog(token: Long, startedAtMillis: Long, delayMillis: Long) {
        mainHandler.postDelayed(
            { checkSpeechWatchdog(token, startedAtMillis) },
            delayMillis
        )
    }

    private fun checkSpeechWatchdog(token: Long, startedAtMillis: Long) {
        if (!speechGeneration.isActive(token)) return
        val now = System.currentTimeMillis()
        when (
            playbackGuard.timeoutAction(
                isSpeaking = tts?.isSpeaking == true,
                elapsedMillis = (now - startedAtMillis).coerceAtLeast(0L)
            )
        ) {
            UltraWakeSpeechTimeoutAction.WAIT ->
                scheduleWatchdog(token, startedAtMillis, COMMAND_SPEECH_RECHECK_MS)
            UltraWakeSpeechTimeoutAction.FINISH -> complete(token)
            UltraWakeSpeechTimeoutAction.STOP_AND_FINISH -> {
                tts?.stop()
                complete(token)
            }
        }
    }

    private fun complete(token: Long) {
        onPlaybackFinished(token)
    }

    private fun speechToken(utteranceId: String?): Long? =
        utteranceId
            ?.takeIf { it.startsWith("$COMMAND_UTTERANCE_PREFIX-") }
            ?.substringAfterLast('-')
            ?.toLongOrNull()

    private companion object {
        const val COMMAND_UTTERANCE_PREFIX = "ultra-command"
        const val COMMAND_SPEECH_TIMEOUT_MS = 10_000L
        const val COMMAND_SPEECH_RECHECK_MS = 5_000L
    }
}

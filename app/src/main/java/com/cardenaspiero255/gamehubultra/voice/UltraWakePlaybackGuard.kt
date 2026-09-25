package com.cardenaspiero255.gamehubultra.voice

internal enum class UltraWakeSpeechTimeoutAction {
    WAIT,
    FINISH,
    STOP_AND_FINISH
}

/**
 * Keeps recognition muted while Ultra speaks and briefly afterwards so
 * SpeechRecognizer cannot turn Ultra's own TTS into a new wake command.
 */
internal class UltraWakePlaybackGuard(
    private val drainWindowMillis: Long = 1_200L,
    private val hardTimeoutMillis: Long = 90_000L
) {
    private val lock = Any()
    private var playbackActive = false
    private var suppressUntilMillis = 0L

    init {
        require(drainWindowMillis >= 0L)
        require(hardTimeoutMillis > 0L)
    }

    fun onPlaybackStarted() {
        synchronized(lock) {
            playbackActive = true
        }
    }

    fun onPlaybackFinished(nowMillis: Long) {
        synchronized(lock) {
            playbackActive = false
            suppressUntilMillis = maxOf(
                suppressUntilMillis,
                nowMillis + drainWindowMillis
            )
        }
    }

    fun shouldSuppressRecognition(nowMillis: Long): Boolean = synchronized(lock) {
        playbackActive || nowMillis <= suppressUntilMillis
    }

    fun timeoutAction(
        isSpeaking: Boolean,
        elapsedMillis: Long
    ): UltraWakeSpeechTimeoutAction =
        when {
            !isSpeaking -> UltraWakeSpeechTimeoutAction.FINISH
            elapsedMillis >= hardTimeoutMillis ->
                UltraWakeSpeechTimeoutAction.STOP_AND_FINISH
            else -> UltraWakeSpeechTimeoutAction.WAIT
        }

    fun clear() {
        synchronized(lock) {
            playbackActive = false
            suppressUntilMillis = 0L
        }
    }
}

/**
 * Rejects recognizer callbacks after the foreground service starts teardown.
 */
internal class UltraWakeLifecycleGate {
    @Volatile
    private var stopped = false

    fun canAcceptRecognition(): Boolean = !stopped

    fun stop() {
        stopped = true
    }

    fun restart() {
        stopped = false
    }
}

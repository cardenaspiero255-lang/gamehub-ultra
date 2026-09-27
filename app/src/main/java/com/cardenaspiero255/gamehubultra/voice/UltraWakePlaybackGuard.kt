package com.cardenaspiero255.gamehubultra.voice

internal enum class UltraWakeSpeechTimeoutAction {
    WAIT,
    FINISH,
    STOP_AND_FINISH
}

internal enum class UltraWakeRecognitionDisposition {
    ACCEPT,
    SUPPRESS,
    INTERRUPT_TTS
}

internal object UltraWakeWordMatcher {
    fun contains(transcript: String): Boolean {
        val normalized = VoiceCommandParser.normalize(transcript)
        return normalized.split(" ").any(::isWakeToken)
    }

    fun isExplicitInvocation(transcript: String): Boolean {
        val tokens = VoiceCommandParser.normalize(transcript)
            .split(" ")
            .filter { it.isNotBlank() }
        val wakeIndex = tokens.indexOfFirst(::isWakeToken)
        return wakeIndex in 0..1 && tokens.size > wakeIndex + 1
    }

    private fun isWakeToken(token: String): Boolean =
        token == "ultra" || (token.length >= 4 && levenshtein(token, "ultra") <= 1)

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}

internal object UltraWakeBargeInPolicy {
    fun decide(
        playbackActive: Boolean,
        transcript: String
    ): UltraWakeRecognitionDisposition =
        when {
            !playbackActive -> UltraWakeRecognitionDisposition.ACCEPT
            UltraWakeWordMatcher.isExplicitInvocation(transcript) ->
                UltraWakeRecognitionDisposition.INTERRUPT_TTS
            else -> UltraWakeRecognitionDisposition.SUPPRESS
        }
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

    fun isPlaybackActive(): Boolean = synchronized(lock) {
        playbackActive
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

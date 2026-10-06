package com.cardenaspiero255.gamehubultra.voice

internal enum class UltraWakeSpeechTimeoutAction {
    WAIT,
    FINISH,
    STOP_AND_FINISH
}

internal enum class UltraWakeRecognitionDisposition {
    ACCEPT,
    SUPPRESS,
    INTERRUPT_TTS,
    STOP_TTS
}

internal enum class UltraWakeSensitivity {
    STRICT,
    BALANCED
}

internal object UltraWakeWordMatcher {
    fun contains(
        transcript: String,
        sensitivity: UltraWakeSensitivity = UltraWakeSensitivity.BALANCED
    ): Boolean {
        val normalized = VoiceCommandParser.normalize(transcript)
        return normalized.split(" ").any { token -> isWakeToken(token, sensitivity) }
    }

    fun isExplicitInvocation(transcript: String): Boolean {
        val tokens = VoiceCommandParser.normalize(transcript)
            .split(" ")
            .filter { it.isNotBlank() }
        val wakeIndex = tokens.indexOfFirst { token ->
            isWakeToken(token, UltraWakeSensitivity.BALANCED)
        }
        return wakeIndex == 0 && tokens.size > 1
    }

    fun isWakeWordOnlyPrefix(transcript: String): Boolean {
        val tokens = VoiceCommandParser.normalize(transcript)
            .split(" ")
            .filter { it.isNotBlank() }
        val wakeIndex = when {
            tokens.getOrNull(0) == "hey" && tokens.getOrNull(1) == "gamehub" -> 2
            tokens.getOrNull(0) == "hey" || tokens.getOrNull(0) == "gamehub" -> 1
            else -> 0
        }
        return tokens.size == wakeIndex + 1 &&
            tokens.getOrNull(wakeIndex)?.let { token ->
                isWakeToken(token, UltraWakeSensitivity.BALANCED)
            } == true
    }

    private fun isWakeToken(
        token: String,
        sensitivity: UltraWakeSensitivity
    ): Boolean =
        token == "ultra" ||
            (
                sensitivity == UltraWakeSensitivity.BALANCED &&
                    token.length >= 4 &&
                    levenshtein(token, "ultra") <= 1
            )

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


/**
 * Suppresses duplicate recognizer deliveries without blocking a genuinely new
 * command. State is local to the wake service and contains no user data beyond
 * the last normalized command.
 */
internal class UltraWakeRepeatGate(
    private val cooldownMillis: Long = 1_500L
) {
    private var lastCommand: String = ""
    private var lastAcceptedAtMillis: Long = Long.MIN_VALUE

    init {
        require(cooldownMillis >= 0L)
    }

    @Synchronized
    fun shouldAccept(transcript: String, nowMillis: Long): Boolean {
        val normalized = VoiceCommandParser.normalize(transcript)
        if (normalized.isBlank()) return false

        val elapsed = if (lastAcceptedAtMillis == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            nowMillis - lastAcceptedAtMillis
        }
        if (normalized == lastCommand && elapsed in 0..cooldownMillis) {
            return false
        }

        lastCommand = normalized
        lastAcceptedAtMillis = nowMillis
        return true
    }

    @Synchronized
    fun clear() {
        lastCommand = ""
        lastAcceptedAtMillis = Long.MIN_VALUE
    }
}

internal object UltraWakeStopSpeakingIntent {
    fun matches(transcript: String): Boolean {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
            .trim()
        return clean in setOf(
            "detente",
            "para",
            "parate",
            "callate",
            "silencio",
            "stop",
            "stop talking",
            "be quiet"
        )
    }
}


internal fun stopActiveUltraSpeech(
    stopPlayback: () -> Unit,
    activeToken: () -> Long?,
    finish: (Long) -> Unit
) {
    stopPlayback()
    activeToken()?.let(finish)
}

internal object UltraWakeBargeInPolicy {
    fun decide(
        playbackActive: Boolean,
        transcript: String,
        playbackEcho: Boolean = false
    ): UltraWakeRecognitionDisposition =
        when {
            !playbackActive -> UltraWakeRecognitionDisposition.ACCEPT
            playbackEcho -> UltraWakeRecognitionDisposition.SUPPRESS
            UltraWakeStopSpeakingIntent.matches(transcript) ->
                UltraWakeRecognitionDisposition.STOP_TTS
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
    private var playbackText: String = ""

    init {
        require(drainWindowMillis >= 0L)
        require(hardTimeoutMillis > 0L)
    }

    fun onPlaybackStarted(spokenText: String? = null) {
        synchronized(lock) {
            playbackActive = true
            playbackText = spokenText
                ?.let(VoiceCommandParser::normalize)
                .orEmpty()
        }
    }

    fun onPlaybackFinished(nowMillis: Long) {
        synchronized(lock) {
            playbackActive = false
            playbackText = ""
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

    fun isLikelyPlaybackEcho(transcript: String): Boolean = synchronized(lock) {
        if (!playbackActive || playbackText.isBlank()) return@synchronized false
        val heard = VoiceCommandParser.normalize(transcript)
        if (heard.isBlank()) return@synchronized false
        if (heard == playbackText) return@synchronized true

        val minimumComparableLength = 16
        heard.length >= minimumComparableLength &&
            (
                playbackText.startsWith(heard) ||
                    heard.startsWith(playbackText) ||
                    playbackText.contains(heard)
            )
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
            playbackText = ""
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

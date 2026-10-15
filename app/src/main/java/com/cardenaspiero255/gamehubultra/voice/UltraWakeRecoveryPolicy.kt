package com.cardenaspiero255.gamehubultra.voice

/**
 * Prevents repeated failures of Android SpeechRecognizer from continuously
 * restarting the microphone and burning CPU/battery while a game is running.
 *
 * Only confirmed healthy recognition resets the failure streak. Delays are
 * bounded and deterministic so they can be covered by local unit tests.
 */
internal class UltraWakeRecoveryPolicy(
    private val initialDelayMillis: Long = 500L,
    private val maximumDelayMillis: Long = 30_000L
) {
    private var nextDelay = initialDelayMillis

    init {
        require(initialDelayMillis > 0L)
        require(maximumDelayMillis >= initialDelayMillis)
    }

    @Synchronized
    fun nextDelayMillis(): Long {
        val delay = nextDelay
        nextDelay = if (nextDelay >= maximumDelayMillis / 2L) {
            maximumDelayMillis
        } else {
            (nextDelay * 2L).coerceAtMost(maximumDelayMillis)
        }
        return delay
    }

    @Synchronized
    fun onRecognitionHealthy() {
        nextDelay = initialDelayMillis
    }
}

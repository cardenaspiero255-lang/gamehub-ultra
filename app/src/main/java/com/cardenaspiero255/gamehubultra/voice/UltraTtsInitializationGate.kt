package com.cardenaspiero255.gamehubultra.voice

/**
 * Preserves the newest spoken answer while Android's TTS engine initializes.
 * The gate is terminal on failure/release: late callbacks cannot resurrect it.
 */
internal class UltraTtsInitializationGate {
    private enum class State { STARTING, READY, FAILED, CLOSED }
    private var state = State.STARTING
    private var pending: String? = null

    @Synchronized
    fun offer(response: String): String? {
        if (response.isBlank() || state == State.CLOSED || state == State.FAILED) return null
        if (state == State.READY) return response
        pending = response
        return null
    }

    @Synchronized
    fun initialized(success: Boolean): String? {
        if (state == State.CLOSED || state == State.FAILED) return null
        if (!success) {
            state = State.FAILED
            pending = null
            return null
        }
        state = State.READY
        return pending.also { pending = null }
    }

    /**
     * Drops an unspoken answer without permanently disabling the TTS engine.
     * A new answer may still be offered before initialization completes.
     */
    @Synchronized
    fun cancelPending(): Boolean {
        if (state != State.STARTING || pending == null) return false
        pending = null
        return true
    }

    @Synchronized
    fun close() {
        state = State.CLOSED
        pending = null
    }
}

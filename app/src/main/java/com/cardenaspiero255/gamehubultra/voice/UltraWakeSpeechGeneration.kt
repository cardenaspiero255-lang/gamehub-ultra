package com.cardenaspiero255.gamehubultra.voice

/**
 * Gives each spoken response a generation token so delayed TTS callbacks from
 * an older command cannot finish a newer command.
 */
internal class UltraWakeSpeechGeneration {
    private val lock = Any()
    private var counter = 0L
    private var active: Long? = null

    fun begin(): Long = synchronized(lock) {
        counter += 1L
        counter.also { active = it }
    }

    fun complete(token: Long): Boolean = synchronized(lock) {
        if (active != token) {
            false
        } else {
            active = null
            true
        }
    }

    fun isActive(): Boolean = synchronized(lock) {
        active != null
    }

    fun clear() {
        synchronized(lock) {
            active = null
        }
    }
}

package com.cardenaspiero255.gamehubultra.voice

internal enum class UltraWakeRecognitionMode {
    PERSISTENT_SEGMENTED,
    LEGACY_RESTARTING
}

internal class UltraWakeSessionPolicy(
    private val sdkInt: Int
) {
    private var persistentDisabled = false

    fun preferredMode(): UltraWakeRecognitionMode =
        if (sdkInt >= 33 && !persistentDisabled) {
            UltraWakeRecognitionMode.PERSISTENT_SEGMENTED
        } else {
            UltraWakeRecognitionMode.LEGACY_RESTARTING
        }

    fun onPersistentSessionFailure() {
        persistentDisabled = true
    }
}

internal class UltraWakeCommandQueue(
    private val capacity: Int = 4
) {
    private val lock = Any()
    private val items = ArrayDeque<String>()

    init {
        require(capacity > 0)
    }

    fun offer(transcript: String) {
        val clean = transcript.trim()
        if (clean.isBlank()) return

        synchronized(lock) {
            if (items.lastOrNull() == clean) return
            while (items.size >= capacity) {
                items.removeFirst()
            }
            items.addLast(clean)
        }
    }

    fun poll(): String? = synchronized(lock) {
        if (items.isEmpty()) null else items.removeFirst()
    }

    fun clear() {
        synchronized(lock) {
            items.clear()
        }
    }
}

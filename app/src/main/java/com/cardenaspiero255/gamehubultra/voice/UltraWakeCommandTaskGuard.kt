package com.cardenaspiero255.gamehubultra.voice

/**
 * Ensures the continuous-listening command slot is released if command work
 * fails before its response is handed off to the main thread.
 */
internal object UltraWakeCommandTaskGuard {
    inline fun run(
        onUnposted: () -> Unit,
        block: () -> Boolean
    ) {
        var posted = false
        try {
            posted = block()
        } finally {
            if (!posted) {
                onUnposted()
            }
        }
    }
}

package com.cardenaspiero255.gamehubultra.voice

/**
 * Android 14+ may deny promotion to a microphone foreground service even
 * after startForegroundService was accepted (e.g. permission revoked or
 * background while-in-use restrictions). Stop gracefully, never crash.
 */
internal object UltraWakeForegroundSafety {
    fun tryStart(ensureForeground: () -> Unit, stop: () -> Unit): Boolean =
        try {
            ensureForeground()
            true
        } catch (_: SecurityException) {
            runCatching { stop() }
            false
        } catch (_: IllegalStateException) {
            runCatching { stop() }
            false
        }
}

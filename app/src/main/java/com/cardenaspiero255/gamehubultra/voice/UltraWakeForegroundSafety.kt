package com.cardenaspiero255.gamehubultra.voice

/**
 * Android 14+ may deny promotion to a microphone foreground service even
 * after startForegroundService was accepted (e.g. permission revoked or
 * background while-in-use restrictions). Stop gracefully, never crash.
 */
internal object UltraWakeForegroundSafety {
    fun tryStart(
        ensureForeground: () -> Unit,
        stop: () -> Unit,
        disableContinuousVoice: () -> Unit = {}
    ): Boolean =
        try {
            ensureForeground()
            true
        } catch (_: SecurityException) {
            reject(disableContinuousVoice, stop)
        } catch (_: IllegalStateException) {
            reject(disableContinuousVoice, stop)
        }
    private fun reject(disableContinuousVoice: () -> Unit, stop: () -> Unit): Boolean {
        // No persistent "enabled" indicator after Android rejects the mic.
        runCatching { disableContinuousVoice() }
        runCatching { stop() }
        return false
    }
}

package com.cardenaspiero255.gamehubultra.voice

internal object UltraWakeFailureGuard {
    private const val FALLBACK =
        "No pude completar el comando de Ultra. Inténtalo de nuevo."

    fun run(block: () -> String): String =
        try {
            block()
        } catch (_: Exception) {
            FALLBACK
        }
}

package com.cardenaspiero255.gamehubultra.ai

/**
 * Explicit, locally persisted opt-in for Ultra's encrypted conversation history.
 *
 * The backing storage must be an app-private, encrypted store. Legacy memory
 * payloads are not proof of consent: only the exact opt-in marker grants it.
 */
class UltraMemoryPrivacyPreferences(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    private val remove: (String) -> Unit
) {
    fun hasConsent(): Boolean = read(CONSENT_KEY) == "granted"

    fun setConsent(granted: Boolean) {
        if (granted) {
            write(CONSENT_KEY, "granted")
        } else {
            remove(CONSENT_KEY)
        }
    }

    fun retentionDays(): Int =
        read(RETENTION_KEY)?.toIntOrNull()
            ?.takeIf { it in ALLOWED_RETENTION_DAYS }
            ?: DEFAULT_RETENTION_DAYS

    fun setRetentionDays(days: Int): Boolean {
        if (days !in ALLOWED_RETENTION_DAYS) return false
        write(RETENTION_KEY, days.toString())
        return true
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        val ALLOWED_RETENTION_DAYS: Set<Int> = setOf(7, 30, 90)
        private const val CONSENT_KEY = "ultra_memory_explicit_consent_v1"
        private const val RETENTION_KEY = "ultra_memory_retention_days_v1"
    }
}

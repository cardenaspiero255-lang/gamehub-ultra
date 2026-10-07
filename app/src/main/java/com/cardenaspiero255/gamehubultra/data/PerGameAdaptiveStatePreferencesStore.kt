package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePersistedState
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveStateStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

class PerGameAdaptiveStatePreferencesStore(
    context: Context
) : PerGameAdaptiveStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(key: AdaptiveGameKey): PerGameAdaptivePersistedState? {
        val raw = preferences.getString(preferenceKey(key), null) ?: return null
        val fields = raw.split("|")
        if (fields.size != 5) return null

        val profile = fields[0].toProfile() ?: return null
        val candidate = fields[1].takeIf(String::isNotBlank)?.toProfile()
            ?: if (fields[1].isBlank()) null else return null
        val confirmations = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val lastChangeMillis = fields[3]
            .takeIf(String::isNotBlank)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: if (fields[3].isBlank()) null else return null
        val recoveryProfile = fields[4].takeIf(String::isNotBlank)?.toProfile()
            ?: if (fields[4].isBlank()) null else return null

        return PerGameAdaptivePersistedState(
            profile = profile,
            candidate = candidate,
            confirmations = confirmations,
            lastChangeMillis = lastChangeMillis,
            recoveryProfile = recoveryProfile
        )
    }

    override fun write(
        key: AdaptiveGameKey,
        state: PerGameAdaptivePersistedState
    ) {
        val encoded = listOf(
            state.profile.name,
            state.candidate?.name.orEmpty(),
            state.confirmations.coerceAtLeast(0).toString(),
            state.lastChangeMillis
                ?.takeIf { it >= 0L }
                ?.toString()
                .orEmpty(),
            state.recoveryProfile?.name.orEmpty()
        ).joinToString("|")
        preferences.edit()
            .putString(preferenceKey(key), encoded)
            .apply()
    }


    fun wasSessionHandled(sessionId: String): Boolean =
        sessionId.isNotBlank() &&
            preferences.getString(LAST_HANDLED_SESSION_ID, null) == sessionId

    fun markSessionHandled(sessionId: String) {
        val clean = sessionId.trim()
        if (clean.isEmpty()) return
        preferences.edit()
            .putString(LAST_HANDLED_SESSION_ID, clean)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "gamehub_ultra_adaptive_optimizer"
        private const val LAST_HANDLED_SESSION_ID = "last_handled_session_id"

        internal fun preferenceKey(key: AdaptiveGameKey): String =
            "state:${key.packageName.trim()}:${key.version.trim()}"

        private fun String.toProfile(): PerformanceProfile? =
            PerformanceProfile.entries.firstOrNull { it.name == this }
    }
}

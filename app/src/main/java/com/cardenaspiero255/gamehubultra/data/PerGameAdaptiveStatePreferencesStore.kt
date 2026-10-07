package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePersistedState
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePendingDecision
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveStateStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import java.util.Base64

class PerGameAdaptiveStatePreferencesStore(
    context: Context
) : PerGameAdaptiveStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun read(key: AdaptiveGameKey): PerGameAdaptivePersistedState? =
        preferences.getString(preferenceKey(key), null)?.let(::decodeState)

    override fun ownedRecoveryProfileForPackage(
        packageName: String,
        excludingVersion: String,
        activeProfile: PerformanceProfile
    ): PerformanceProfile? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty()) return null
        val prefix = "state:$cleanPackage:"
        val excludedKey = preferenceKey(
            AdaptiveGameKey(cleanPackage, excludingVersion.trim())
        )
        return preferences.all.entries
            .asSequence()
            .filter { (key, _) -> key.startsWith(prefix) && key != excludedKey }
            .mapNotNull { (_, raw) -> (raw as? String)?.let(::decodeState) }
            .filter { state ->
                state.profile == activeProfile && state.recoveryProfile != null
            }
            .maxByOrNull { it.lastChangeMillis ?: Long.MIN_VALUE }
            ?.recoveryProfile
    }

    override fun write(
        key: AdaptiveGameKey,
        state: PerGameAdaptivePersistedState
    ) {
        preferences.edit()
            .putString(preferenceKey(key), encodeState(state))
            .commit()
    }

    fun recordExplicitProfileSelection(
        key: AdaptiveGameKey,
        profile: PerformanceProfile
    ) {
        val cleanPackage = key.packageName.trim()
        if (cleanPackage.isEmpty()) return
        val prefix = "state:$cleanPackage:"
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(editor::remove)

        val pending = preferences.getString(PENDING_DECISION_KEY, null)
            ?.let(::decodePendingDecision)
        if (pending?.key?.packageName == cleanPackage) {
            editor.remove(PENDING_DECISION_KEY)
        }

        editor.putString(
            preferenceKey(key),
            encodeState(
                PerGameAdaptivePersistedState(
                    profile = profile,
                    candidate = null,
                    confirmations = 0,
                    lastChangeMillis = null,
                    recoveryProfile = null
                )
            )
        )
        editor.commit()
    }


    override fun delete(key: AdaptiveGameKey) {
        preferences.edit()
            .remove(preferenceKey(key))
            .commit()
    }

    override fun readPendingDecision(
        sessionId: String
    ): PerGameAdaptivePendingDecision? {
        val cleanSession = sessionId.trim()
        if (cleanSession.isEmpty()) return null
        val raw = preferences.getString(PENDING_DECISION_KEY, null) ?: return null
        return decodePendingDecision(raw)
            ?.takeIf { it.sessionId == cleanSession }
    }

    override fun writePendingDecision(decision: PerGameAdaptivePendingDecision) {
        preferences.edit()
            .putString(PENDING_DECISION_KEY, encodePendingDecision(decision))
            .commit()
    }

    override fun clearPendingDecision(sessionId: String) {
        val current = preferences.getString(PENDING_DECISION_KEY, null)
            ?.let(::decodePendingDecision)
        if (current?.sessionId != sessionId.trim()) return
        preferences.edit()
            .remove(PENDING_DECISION_KEY)
            .commit()
    }

    fun wasSessionHandled(sessionId: String): Boolean =
        sessionId.isNotBlank() &&
            preferences.getString(LAST_HANDLED_SESSION_ID, null) == sessionId

    fun markSessionHandled(sessionId: String) {
        val clean = sessionId.trim()
        if (clean.isEmpty()) return
        preferences.edit()
            .putString(LAST_HANDLED_SESSION_ID, clean)
            .commit()
    }

    private fun encodeState(
        state: PerGameAdaptivePersistedState
    ): String =
        listOf(
            state.profile.name,
            state.candidate?.name.orEmpty(),
            state.confirmations.coerceAtLeast(0).toString(),
            state.lastChangeMillis
                ?.takeIf { it >= 0L }
                ?.toString()
                .orEmpty(),
            state.recoveryProfile?.name.orEmpty()
        ).joinToString("|")

    private fun encodePendingDecision(
        decision: PerGameAdaptivePendingDecision
    ): String =
        listOf(
            encodeText(decision.sessionId),
            encodeText(decision.key.packageName),
            encodeText(decision.key.version),
            decision.previousProfile.name,
            decision.targetProfile.name,
            decision.targetState.profile.name,
            decision.targetState.candidate?.name.orEmpty(),
            decision.targetState.confirmations.coerceAtLeast(0).toString(),
            decision.targetState.lastChangeMillis
                ?.takeIf { it >= 0L }
                ?.toString()
                .orEmpty(),
            decision.targetState.recoveryProfile?.name.orEmpty(),
            decision.eventTimestampMillis.coerceAtLeast(0L).toString(),
            encodeText(decision.reason)
        ).joinToString("|")

    private fun decodePendingDecision(raw: String): PerGameAdaptivePendingDecision? {
        val fields = raw.split("|")
        if (fields.size != 12) return null
        val sessionId = decodeText(fields[0])?.takeIf(String::isNotBlank) ?: return null
        val packageName = decodeText(fields[1])?.takeIf(String::isNotBlank) ?: return null
        val version = decodeText(fields[2])?.takeIf(String::isNotBlank) ?: return null
        val previousProfile = fields[3].toProfile() ?: return null
        val targetProfile = fields[4].toProfile() ?: return null
        val stateProfile = fields[5].toProfile() ?: return null
        val candidate = fields[6].takeIf(String::isNotBlank)?.toProfile()
            ?: if (fields[6].isBlank()) null else return null
        val confirmations = fields[7].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val lastChangeMillis = fields[8]
            .takeIf(String::isNotBlank)
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: if (fields[8].isBlank()) null else return null
        val recoveryProfile = fields[9].takeIf(String::isNotBlank)?.toProfile()
            ?: if (fields[9].isBlank()) null else return null
        val eventTimestampMillis = fields[10].toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val reason = decodeText(fields[11]) ?: return null

        return PerGameAdaptivePendingDecision(
            sessionId = sessionId,
            key = AdaptiveGameKey(packageName, version),
            previousProfile = previousProfile,
            targetProfile = targetProfile,
            targetState = PerGameAdaptivePersistedState(
                profile = stateProfile,
                candidate = candidate,
                confirmations = confirmations,
                lastChangeMillis = lastChangeMillis,
                recoveryProfile = recoveryProfile
            ),
            eventTimestampMillis = eventTimestampMillis,
            reason = reason
        )
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String? =
        runCatching {
            String(
                Base64.getUrlDecoder().decode(value),
                Charsets.UTF_8
            )
        }.getOrNull()

    private fun decodeState(raw: String): PerGameAdaptivePersistedState? {
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

    companion object {
        private const val PREFERENCES_NAME = "gamehub_ultra_adaptive_optimizer"
        private const val LAST_HANDLED_SESSION_ID = "last_handled_session_id"
        private const val PENDING_DECISION_KEY = "pending_decision"

        internal fun preferenceKey(key: AdaptiveGameKey): String =
            "state:${key.packageName.trim()}:${key.version.trim()}"

        private fun String.toProfile(): PerformanceProfile? =
            PerformanceProfile.entries.firstOrNull { it.name == this }
    }
}

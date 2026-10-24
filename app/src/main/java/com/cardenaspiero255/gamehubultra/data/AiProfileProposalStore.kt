package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OrientationPreference
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ResolutionTarget
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

data class AppliedAiProfileProposalState(
    val version: Int,
    val previousKnownGoodConfig: GameProfileConfig
)

class AiProfileProposalStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun nextVersion(packageName: String): Int = synchronized(STORE_LOCK) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty()) return@synchronized 1
        (preferences.getInt(versionKey(cleanPackage), 0) + 1).coerceAtLeast(1)
    }

    fun recordApplied(
        packageName: String,
        proposal: AiProfileProposal
    ) {
        val cleanPackage = packageName.trim()
        require(cleanPackage.isNotEmpty()) { "packageName must not be blank" }
        require(proposal.version > 0) { "proposal version must be positive" }

        // Strictly monotonic: repeated callbacks (including the just-cleared
        // version) cannot overwrite or resurrect a previously approved rollback.
        // A companion-level lock serializes writers from different store instances.
        synchronized(STORE_LOCK) {
            if (proposal.version <= preferences.getInt(versionKey(cleanPackage), 0)) {
                return
            }
            check(preferences.edit()
                .putInt(versionKey(cleanPackage), proposal.version)
                .putString(
                    rollbackKey(cleanPackage),
                    encodeConfig(proposal.previousKnownGoodConfig)
                )
                .commit()) { "AI rollback checkpoint was not durably written" }
        }
    }

    fun rollbackState(packageName: String): AppliedAiProfileProposalState? =
        synchronized(STORE_LOCK) {
            val cleanPackage = packageName.trim()
            if (cleanPackage.isEmpty()) return@synchronized null
            val version = preferences.getInt(versionKey(cleanPackage), 0)
            if (version <= 0) return@synchronized null
            val encoded = preferences.getString(rollbackKey(cleanPackage), null)
                ?: return@synchronized null
            val previous = decodeConfig(encoded) ?: return@synchronized null
            AppliedAiProfileProposalState(version, previous)
        }

    // Legacy/diagnostic API. Production UI uses rollbackIfCurrent with the
    // exact snapshot version and a serialized profile write.
    fun clearRollback(packageName: String) = synchronized(STORE_LOCK) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isNotEmpty()) {
            preferences.edit().remove(rollbackKey(cleanPackage)).apply()
        }
    }

    fun clearRollbackIfCurrent(packageName: String, expectedVersion: Int): Boolean =
        synchronized(STORE_LOCK) {
            val cleanPackage = packageName.trim()
            if (cleanPackage.isEmpty() || expectedVersion <= 0 ||
                preferences.getInt(versionKey(cleanPackage), 0) != expectedVersion ||
                !preferences.contains(rollbackKey(cleanPackage))
            ) {
                return@synchronized false
            }
            preferences.edit().remove(rollbackKey(cleanPackage)).commit()
        }

    /**
     * Serializes production approval across store instances. Rejects stale
     * versions BEFORE touching the active DataStore profile. No callback can
     * apply/rollback another version until this persistence step completes.
     */
    suspend fun applyProposal(
        packageName: String,
        proposal: AiProfileProposal,
        persist: suspend (String, GameProfileConfig) -> Unit
    ): Boolean {
        val cleanPackage = packageName.trim()
        require(cleanPackage.isNotEmpty()) { "packageName must not be blank" }
        require(proposal.version > 0) { "proposal version must be positive" }
        MUTATION_MUTEX.lock()
        try {
            val allowed = synchronized(STORE_LOCK) {
                proposal.version > preferences.getInt(versionKey(cleanPackage), 0)
            }
            if (!allowed) return false
            persist(cleanPackage, proposal.proposedConfig)
            // SharedPreferences.apply() alone does not prove a checkpoint
            // survived an OS process restart; commit on the IO dispatcher.
            try {
                withContext(Dispatchers.IO) { recordApplied(cleanPackage, proposal) }
            } catch (checkpointFailure: Exception) {
                // If the checkpoint fails, do not leave an un-rollbackable
                // active profile from this user approval.
                try {
                    withContext(NonCancellable) {
                        persist(cleanPackage, proposal.previousKnownGoodConfig)
                    }
                } catch (restoreFailure: Throwable) {
                    checkpointFailure.addSuppressed(restoreFailure)
                }
                throw checkpointFailure
            }
            return true
        } finally {
            MUTATION_MUTEX.unlock()
        }
    }

    /**
     * Reads the durable rollback checkpoint AFTER acquiring the same mutation
     * mutex; a stale UI snapshot cannot restore or clear a newer version.
     */
    suspend fun rollbackIfCurrent(
        packageName: String,
        expectedVersion: Int,
        persist: suspend (String, GameProfileConfig) -> Unit
    ): Boolean {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty() || expectedVersion <= 0) return false
        MUTATION_MUTEX.lock()
        try {
            val current = rollbackState(cleanPackage)
                ?.takeIf { it.version == expectedVersion }
                ?: return false
            persist(cleanPackage, current.previousKnownGoodConfig)
            return withContext(Dispatchers.IO) {
                clearRollbackIfCurrent(cleanPackage, expectedVersion)
            }
        } finally {
            MUTATION_MUTEX.unlock()
        }
    }

    private fun versionKey(packageName: String): String =
        "version_" + encodeKey(packageName)

    private fun rollbackKey(packageName: String): String =
        "rollback_" + encodeKey(packageName)

    private fun encodeKey(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun encodeConfig(config: GameProfileConfig): String =
        listOf(
            config.performanceProfile.name,
            config.thermalPreference.name,
            config.refreshRateTargetHz?.toString().orEmpty(),
            config.resolutionTarget?.width?.toString().orEmpty(),
            config.resolutionTarget?.height?.toString().orEmpty(),
            config.orientationPreference.name
        ).joinToString("|")

    private fun decodeConfig(raw: String): GameProfileConfig? = runCatching {
        val fields = raw.split("|")
        if (fields.size != 6) return null

        val resolutionWidth = fields[3].toIntOrNull()
        val resolutionHeight = fields[4].toIntOrNull()
        val resolution = if (resolutionWidth != null && resolutionHeight != null) {
            ResolutionTarget(resolutionWidth, resolutionHeight)
        } else {
            null
        }

        GameProfileConfig(
            performanceProfile = PerformanceProfile.valueOf(fields[0]),
            thermalPreference = ThermalPreference.valueOf(fields[1]),
            refreshRateTargetHz = fields[2].toIntOrNull(),
            resolutionTarget = resolution,
            orientationPreference = OrientationPreference.valueOf(fields[5])
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "gamehub_ultra_ai_profile_builder"
        private val STORE_LOCK = Any()
        private val MUTATION_MUTEX = Mutex()
    }
}

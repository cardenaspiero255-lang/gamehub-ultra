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

data class AppliedAiProfileProposalState(
    val version: Int,
    val previousKnownGoodConfig: GameProfileConfig
)

class AiProfileProposalStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun nextVersion(packageName: String): Int {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty()) return 1
        return (preferences.getInt(versionKey(cleanPackage), 0) + 1)
            .coerceAtLeast(1)
    }

    @Synchronized
    fun recordApplied(
        packageName: String,
        proposal: AiProfileProposal
    ) {
        val cleanPackage = packageName.trim()
        require(cleanPackage.isNotEmpty()) { "packageName must not be blank" }
        require(proposal.version > 0) { "proposal version must be positive" }

        preferences.edit()
            .putInt(versionKey(cleanPackage), proposal.version)
            .putString(
                rollbackKey(cleanPackage),
                encodeConfig(proposal.previousKnownGoodConfig)
            )
            .apply()
    }

    @Synchronized
    fun rollbackState(packageName: String): AppliedAiProfileProposalState? {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty()) return null
        val version = preferences.getInt(versionKey(cleanPackage), 0)
        if (version <= 0) return null
        val encoded = preferences.getString(rollbackKey(cleanPackage), null)
            ?: return null
        val previous = decodeConfig(encoded) ?: return null
        return AppliedAiProfileProposalState(
            version = version,
            previousKnownGoodConfig = previous
        )
    }

    @Synchronized
    fun clearRollback(packageName: String) {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isEmpty()) return
        preferences.edit()
            .remove(rollbackKey(cleanPackage))
            .apply()
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
    }
}

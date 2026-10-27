package com.cardenaspiero255.gamehubultra.platform.compat

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily

/** CAR-56: Non-unique, non-sensitive capability categories only.
 * Never include IMEI, serial numbers, Android ID, Build.FINGERPRINT or accounts.
 */
enum class CompatibilityGpuFamily { ADRENO, MALI, POWERVR, OTHER, UNKNOWN }

data class CompatibilityDeviceKey(
    val socFamily: VendorSocFamily,
    val gpuFamily: CompatibilityGpuFamily,
    val androidApiLevel: Int
)

/** Records are not trusted merely because they are present in a local database.
 * Their evidence IDs must be independently authenticated by the caller.
 */
data class DeviceCompatibilityRule(
    val schemaVersion: Int,
    val ruleId: String,
    val evidenceId: String,
    val gamePackage: String,
    val key: CompatibilityDeviceKey,
    val recommendedProfile: PerformanceProfile
)

enum class CompatibilityStatus { KNOWN_GOOD, NO_RECORD, UNVERIFIED, AMBIGUOUS, INVALID_INPUT }

data class CompatibilityDecision(
    val status: CompatibilityStatus,
    val recommendedProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val ruleId: String? = null
) {
    /** This layer recommends metadata; applying an actual performance profile
     * requires a separate, explicit and authorized game-profile action.
     */
    val profileApplied: Boolean get() = false
}

object DeviceCompatibilityDatabase {
    const val SCHEMA_VERSION: Int = 1
    private val packagePattern =
        Regex("[A-Za-z_][A-Za-z_0-9]*(?:\\.[A-Za-z_][A-Za-z_0-9]*)+")
    private val evidencePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{7,63}")

    /**
     * Conservative resolver: exact SoC family, GPU family, API and package.
     * No external data fetch, driver switch, X4 activation or guessed support.
     * A missing/stale/conflicting source falls back to BALANCED as an advice,
     * never as a claimed measured or active configuration.
     */
    fun resolve(
        device: CompatibilityDeviceKey,
        gamePackage: String,
        records: List<DeviceCompatibilityRule>,
        independentlyVerifiedEvidenceIds: Set<String>
    ): CompatibilityDecision {
        if (!packagePattern.matches(gamePackage) ||
            device.androidApiLevel !in 26..100 ||
            device.socFamily == VendorSocFamily.GENERIC ||
            device.gpuFamily == CompatibilityGpuFamily.UNKNOWN
        ) {
            return CompatibilityDecision(CompatibilityStatus.INVALID_INPUT)
        }

        val candidates = records.filter { it.key == device && it.gamePackage == gamePackage }
        if (candidates.isEmpty()) return CompatibilityDecision(CompatibilityStatus.NO_RECORD)
        if (candidates.size != 1) return CompatibilityDecision(CompatibilityStatus.AMBIGUOUS)

        val rule = candidates.single()
        if (rule.schemaVersion != SCHEMA_VERSION ||
            !evidencePattern.matches(rule.ruleId) ||
            !evidencePattern.matches(rule.evidenceId) ||
            rule.evidenceId !in independentlyVerifiedEvidenceIds
        ) {
            return CompatibilityDecision(CompatibilityStatus.UNVERIFIED)
        }
        return CompatibilityDecision(
            status = CompatibilityStatus.KNOWN_GOOD,
            recommendedProfile = rule.recommendedProfile,
            ruleId = rule.ruleId
        )
    }

    /**
     * Local, consent-based export. No unique identifier or raw renderer/model.
     * Package identities require explicit includePackage=true.
     */
    fun exportDiagnostic(
        device: CompatibilityDeviceKey,
        gamePackage: String,
        decision: CompatibilityDecision,
        includePackage: Boolean = false
    ): String {
        val exportedPackage =
            if (includePackage && packagePattern.matches(gamePackage)) gamePackage else "redacted"
        return listOf(
            "schema=$SCHEMA_VERSION",
            "socFamily=" + device.socFamily.name,
            "gpuFamily=" + device.gpuFamily.name,
            "androidApi=" + device.androidApiLevel,
            "status=" + decision.status.name,
            "ruleId=" + (decision.ruleId ?: "none"),
            "gamePackage=" + exportedPackage,
            "profileApplied=false"
        ).joinToString("\n")
    }
}

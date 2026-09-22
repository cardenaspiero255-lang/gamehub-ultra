package com.cardenaspiero255.gamehubultra.domain

/**
 * Pure adaptive policy for GameHub Ultra's own workload/session.
 * It never claims control over another application's renderer or frame generation.
 *
 * Android's getThermalHeadroom value represents thermal-envelope usage:
 * 1.0 corresponds to the SEVERE throttling threshold; higher means closer to throttling.
 */
data class AdaptiveRuntimeSnapshot(
    val thermalStatus: Int?,
    val thermalHeadroom: Float?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean,
    val sessionActive: Boolean,
    val sustainedPerformanceSupported: Boolean,
    val performanceHintsAvailable: Boolean
)

data class AdaptiveDecision(
    val profile: PerformanceProfile,
    val enableSustainedPerformance: Boolean,
    val score: Int,
    val reason: String,
    val changed: Boolean,
    val pendingConfirmations: Int = 0
)

class AdaptivePerformanceEngine(
    private val confirmationsRequired: Int = 2,
    initialProfile: PerformanceProfile = PerformanceProfile.BALANCED
) {
    private var currentProfile = initialProfile
    private var candidateProfile: PerformanceProfile? = null
    private var candidateConfirmations = 0

    init {
        require(confirmationsRequired >= 1)
    }

    fun currentProfile(): PerformanceProfile = currentProfile

    fun evaluate(snapshot: AdaptiveRuntimeSnapshot): AdaptiveDecision {
        val target = targetProfile(snapshot)
        val score = readinessScore(snapshot)

        if (target == currentProfile) {
            candidateProfile = null
            candidateConfirmations = 0
            return decision(snapshot, currentProfile, score, changed = false)
        }

        if (candidateProfile != target) {
            candidateProfile = target
            candidateConfirmations = 1
        } else {
            candidateConfirmations += 1
        }

        if (candidateConfirmations < confirmationsRequired) {
            return decision(
                snapshot = snapshot,
                profile = currentProfile,
                score = score,
                changed = false,
                pendingConfirmations = candidateConfirmations
            )
        }

        currentProfile = target
        candidateProfile = null
        candidateConfirmations = 0
        return decision(snapshot, currentProfile, score, changed = true)
    }

    private fun targetProfile(snapshot: AdaptiveRuntimeSnapshot): PerformanceProfile {
        if (!snapshot.sessionActive || snapshot.powerSaveMode) {
            return PerformanceProfile.BALANCED
        }

        if (thermalBlocksHighPerformance(snapshot)) {
            return PerformanceProfile.BALANCED
        }

        if (currentProfile == PerformanceProfile.BALANCED &&
            snapshot.batteryPercent != null &&
            !snapshot.charging &&
            snapshot.batteryPercent < BATTERY_ENTRY_PERCENT
        ) {
            return PerformanceProfile.BALANCED
        }

        val batteryAllowsHighPerformance = when {
            snapshot.charging -> true
            snapshot.batteryPercent == null -> true
            currentProfile == PerformanceProfile.BALANCED ->
                snapshot.batteryPercent >= BATTERY_ENTRY_PERCENT
            else ->
                snapshot.batteryPercent > BATTERY_EXIT_PERCENT
        }

        if (!batteryAllowsHighPerformance) {
            return PerformanceProfile.BALANCED
        }

        return when {
            snapshot.sustainedPerformanceSupported -> PerformanceProfile.X4
            snapshot.performanceHintsAvailable -> PerformanceProfile.FRAME_INTERPOLATION
            else -> PerformanceProfile.BALANCED
        }
    }

    private fun thermalBlocksHighPerformance(
        snapshot: AdaptiveRuntimeSnapshot
    ): Boolean {
        val status = snapshot.thermalStatus
        if (status != null && status >= THERMAL_STATUS_SEVERE) return true

        val usage = snapshot.thermalHeadroom
        if (usage == null || usage.isNaN()) return false

        return if (currentProfile == PerformanceProfile.BALANCED) {
            usage > THERMAL_RECOVERY_USAGE
        } else {
            usage >= THERMAL_THROTTLE_USAGE
        }
    }

    private fun isThermallyConstrained(snapshot: AdaptiveRuntimeSnapshot): Boolean =
        thermalBlocksHighPerformance(snapshot)

    private fun readinessScore(snapshot: AdaptiveRuntimeSnapshot): Int {
        var score = 50

        when {
            snapshot.thermalStatus == null -> Unit
            snapshot.thermalStatus <= THERMAL_STATUS_LIGHT -> score += 15
            snapshot.thermalStatus == THERMAL_STATUS_MODERATE -> score += 5
            else -> score -= 35
        }

        when {
            snapshot.thermalHeadroom == null || snapshot.thermalHeadroom.isNaN() -> Unit
            snapshot.thermalHeadroom <= 0.30f -> score += 10
            snapshot.thermalHeadroom < THERMAL_EXIT_USAGE -> score += 3
            snapshot.thermalHeadroom < THERMAL_ENTRY_USAGE -> score -= 10
            else -> score -= 25
        }

        when {
            snapshot.batteryPercent == null -> Unit
            snapshot.batteryPercent >= 60 || snapshot.charging -> score += 10
            snapshot.batteryPercent > BATTERY_EXIT_PERCENT -> score += 2
            else -> score -= 20
        }

        if (snapshot.powerSaveMode) score -= 20
        if (snapshot.sessionActive) score += 5

        return score.coerceIn(0, 100)
    }

    private fun decision(
        snapshot: AdaptiveRuntimeSnapshot,
        profile: PerformanceProfile,
        score: Int,
        changed: Boolean,
        pendingConfirmations: Int = 0
    ): AdaptiveDecision {
        val enableSustained = profile == PerformanceProfile.X4 &&
            snapshot.sustainedPerformanceSupported

        val reason = when {
            snapshot.powerSaveMode ->
                "Ahorro de energía activo: se mantiene un perfil conservador."
            isThermallyConstrained(snapshot) ->
                "El uso térmico previsto está cerca del umbral de throttling."
            !snapshot.sessionActive ->
                "No hay una sesión de rendimiento activa."
            else -> when (profile) {
                PerformanceProfile.X4 ->
                    "Condiciones térmicas y de energía aptas para carga sostenida compatible."
                PerformanceProfile.FRAME_INTERPOLATION ->
                    "El dispositivo expone capacidades de rendimiento compatibles."
                PerformanceProfile.BALANCED ->
                    "Se prioriza un equilibrio seguro de consumo y temperatura."
            }
        }

        return AdaptiveDecision(
            profile = profile,
            enableSustainedPerformance = enableSustained,
            score = score,
            reason = reason,
            changed = changed,
            pendingConfirmations = pendingConfirmations
        )
    }

    private companion object {
        const val THERMAL_STATUS_LIGHT = 1
        const val THERMAL_STATUS_MODERATE = 2
        const val THERMAL_STATUS_SEVERE = 3

        const val THERMAL_THROTTLE_USAGE = 0.80f
        const val THERMAL_RECOVERY_USAGE = 0.60f

        const val BATTERY_ENTRY_PERCENT = 65
        const val BATTERY_EXIT_PERCENT = 50
    }
}

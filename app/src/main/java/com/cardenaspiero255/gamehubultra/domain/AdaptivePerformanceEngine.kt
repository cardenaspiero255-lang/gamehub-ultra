package com.cardenaspiero255.gamehubultra.domain

/**
 * Pure adaptive policy. It decides what profile GameHub Ultra should apply to itself;
 * it never claims control over another application's renderer or frame generation.
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

        if (isThermallyConstrained(snapshot)) {
            return PerformanceProfile.BALANCED
        }

        val battery = snapshot.batteryPercent ?: 50
        if (!snapshot.charging && battery <= 15) {
            return PerformanceProfile.BALANCED
        }

        if (battery >= 65 || snapshot.charging) {
            return if (snapshot.sustainedPerformanceSupported) {
                PerformanceProfile.X4
            } else if (snapshot.performanceHintsAvailable) {
                PerformanceProfile.FRAME_INTERPOLATION
            } else {
                PerformanceProfile.BALANCED
            }
        }

        return if (snapshot.performanceHintsAvailable) {
            PerformanceProfile.FRAME_INTERPOLATION
        } else {
            PerformanceProfile.BALANCED
        }
    }

    private fun isThermallyConstrained(snapshot: AdaptiveRuntimeSnapshot): Boolean {
        val status = snapshot.thermalStatus
        if (status != null && status >= THERMAL_STATUS_SEVERE) return true

        val headroom = snapshot.thermalHeadroom
        return headroom != null && !headroom.isNaN() && headroom < 0.20f
    }

    private fun readinessScore(snapshot: AdaptiveRuntimeSnapshot): Int {
        var score = 70
        score += when {
            snapshot.thermalStatus == null -> 0
            snapshot.thermalStatus <= THERMAL_STATUS_LIGHT -> 15
            snapshot.thermalStatus == THERMAL_STATUS_MODERATE -> 5
            snapshot.thermalStatus >= THERMAL_STATUS_SEVERE -> -35
            else -> 0
        }
        score += when {
            snapshot.thermalHeadroom == null || snapshot.thermalHeadroom.isNaN() -> 0
            snapshot.thermalHeadroom >= 0.50f -> 10
            snapshot.thermalHeadroom >= 0.30f -> 5
            snapshot.thermalHeadroom < 0.20f -> -20
            else -> 0
        }
        score += when {
            snapshot.batteryPercent == null -> 0
            snapshot.batteryPercent >= 60 -> 5
            snapshot.batteryPercent <= 15 && !snapshot.charging -> -20
            else -> 0
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
            snapshot.powerSaveMode -> "Ahorro de energía activo: se mantiene un perfil conservador."
            isThermallyConstrained(snapshot) -> "La condición térmica requiere reducir carga sostenida."
            !snapshot.sessionActive -> "No hay una sesión de juego activa."
            else -> when (profile) {
                PerformanceProfile.X4 ->
                    "Térmica y energía disponibles para una carga sostenida compatible."
                PerformanceProfile.FRAME_INTERPOLATION ->
                    "La sesión permite priorizar APIs compatibles de interpolación."
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
    }
}

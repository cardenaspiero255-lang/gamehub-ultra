package com.cardenaspiero255.gamehubultra.domain

import kotlin.math.roundToInt

data class AiProfileCapabilities(
    val supportsSustainedPerformance: Boolean = false,
    val supportsFrameInterpolation: Boolean = false,
    val supportedRefreshRatesHz: Set<Int> = emptySet(),
    val supportedResolutions: Set<ResolutionTarget> = emptySet()
)

data class AiProfileProposal(
    val version: Int,
    val previousKnownGoodConfig: GameProfileConfig,
    val proposedConfig: GameProfileConfig,
    val requiresExplicitApply: Boolean,
    val reasons: List<String>,
    val disabledSettings: List<String>
)

object AiProfileBuilder {
    private const val THERMAL_PRESSURE_EVIDENCE = 3
    private const val REFRESH_EVIDENCE = 3
    private const val MIN_PROFILE_SCORE = 4

    fun propose(
        currentConfig: GameProfileConfig,
        observations: List<OptimizationObservation>,
        sessionSamples: List<SessionCoachSnapshot>,
        capabilities: AiProfileCapabilities,
        version: Int
    ): AiProfileProposal {
        require(version > 0) { "version must be positive" }

        val reasons = mutableListOf<String>()
        val disabledSettings = mutableListOf<String>()
        var proposed = currentConfig

        val thermalPressureCount = observations.count { observation ->
            observation.failed ||
                observation.highTemperature ||
                (observation.thermalStatus ?: Int.MIN_VALUE) >= 4
        }

        if (thermalPressureCount >= THERMAL_PRESSURE_EVIDENCE) {
            proposed = proposed.copy(
                performanceProfile = PerformanceProfile.BALANCED,
                thermalPreference = ThermalPreference.COOLER
            )
            reasons +=
                "La telemetría real mostró presión térmica repetida; se propone un perfil más frío y estable."
        } else {
            preferredObservedProfile(observations)?.let { preferred ->
                if (preferred != proposed.performanceProfile) {
                    proposed = proposed.copy(performanceProfile = preferred)
                    reasons +=
                        "Las observaciones estables y el feedback aceptado favorecen ${preferred.title}."
                }
            }
        }

        proposed = enforcePerformanceCapabilities(
            config = proposed,
            capabilities = capabilities,
            disabledSettings = disabledSettings,
            reasons = reasons
        )

        proposed = enforceDisplayCapabilities(
            config = proposed,
            capabilities = capabilities,
            disabledSettings = disabledSettings
        )

        inferStableRefreshTarget(
            currentConfig = proposed,
            samples = sessionSamples,
            capabilities = capabilities
        )?.let { inferred ->
            if (inferred != proposed.refreshRateTargetHz) {
                proposed = proposed.copy(refreshRateTargetHz = inferred)
                reasons +=
                    "Las muestras reales sostuvieron ${inferred} Hz y el dispositivo declara ese objetivo como compatible."
            }
        }

        return AiProfileProposal(
            version = version,
            previousKnownGoodConfig = currentConfig,
            proposedConfig = proposed,
            requiresExplicitApply = proposed != currentConfig,
            reasons = reasons.distinct(),
            disabledSettings = disabledSettings.distinct()
        )
    }

    private fun preferredObservedProfile(
        observations: List<OptimizationObservation>
    ): PerformanceProfile? {
        val best = observations
            .groupBy(OptimizationObservation::profile)
            .mapValues { (_, profileObservations) ->
                profileObservations.sumOf(::scoreObservation)
            }
            .maxByOrNull { it.value }
            ?: return null

        return best.key.takeIf { best.value >= MIN_PROFILE_SCORE }
    }

    private fun scoreObservation(observation: OptimizationObservation): Int {
        var score = 0
        if (observation.stable) score += 2
        if (observation.failed) score -= 3
        if (observation.highTemperature) score -= 3
        if ((observation.thermalStatus ?: 0) >= 4) score -= 2
        score += when (observation.feedbackDecision) {
            OptimizationFeedbackDecision.ACCEPTED -> 3
            OptimizationFeedbackDecision.REJECTED -> -3
            OptimizationFeedbackDecision.REVERTED -> -4
            OptimizationFeedbackDecision.NONE -> 0
        }
        return score
    }

    private fun enforcePerformanceCapabilities(
        config: GameProfileConfig,
        capabilities: AiProfileCapabilities,
        disabledSettings: MutableList<String>,
        reasons: MutableList<String>
    ): GameProfileConfig =
        when (config.performanceProfile) {
            PerformanceProfile.X4 -> {
                if (capabilities.supportsSustainedPerformance) {
                    config
                } else {
                    disabledSettings +=
                        "X4 desactivado: Sustained Performance Mode no está soportado o verificado en este dispositivo."
                    reasons +=
                        "Se mantiene un perfil compatible en lugar de anunciar una capacidad X4 no verificable."
                    config.copy(performanceProfile = PerformanceProfile.BALANCED)
                }
            }

            PerformanceProfile.FRAME_INTERPOLATION -> {
                if (capabilities.supportsFrameInterpolation) {
                    config
                } else {
                    disabledSettings +=
                        "Interpolación desactivada: no existe una capacidad compatible y verificable para este dispositivo/juego."
                    reasons +=
                        "La propuesta no activa interpolación cuando la capacidad real no está disponible."
                    config.copy(performanceProfile = PerformanceProfile.BALANCED)
                }
            }

            PerformanceProfile.BALANCED -> config
        }

    private fun enforceDisplayCapabilities(
        config: GameProfileConfig,
        capabilities: AiProfileCapabilities,
        disabledSettings: MutableList<String>
    ): GameProfileConfig {
        var result = config

        val refresh = result.refreshRateTargetHz
        if (
            refresh != null &&
            refresh !in capabilities.supportedRefreshRatesHz
        ) {
            disabledSettings +=
                "${refresh} Hz desactivado: el objetivo no está dentro de las frecuencias verificadas del dispositivo."
            result = result.copy(refreshRateTargetHz = null)
        }

        val resolution = result.resolutionTarget
        if (
            resolution != null &&
            resolution !in capabilities.supportedResolutions
        ) {
            disabledSettings +=
                "${resolution.width}x${resolution.height} desactivado: la resolución no está verificada como compatible."
            result = result.copy(resolutionTarget = null)
        }

        return result
    }

    private fun inferStableRefreshTarget(
        currentConfig: GameProfileConfig,
        samples: List<SessionCoachSnapshot>,
        capabilities: AiProfileCapabilities
    ): Int? {
        if (currentConfig.refreshRateTargetHz != null) return null
        if (capabilities.supportedRefreshRatesHz.isEmpty()) return null

        val grouped = samples
            .mapNotNull { sample ->
                sample.refreshRateHz
                    ?.takeIf { it.isFinite() && it >= 30f }
                    ?.roundToInt()
            }
            .groupingBy { it }
            .eachCount()

        val candidate = grouped.maxByOrNull { it.value } ?: return null
        return candidate.key.takeIf {
            candidate.value >= REFRESH_EVIDENCE &&
                it in capabilities.supportedRefreshRatesHz
        }
    }
}

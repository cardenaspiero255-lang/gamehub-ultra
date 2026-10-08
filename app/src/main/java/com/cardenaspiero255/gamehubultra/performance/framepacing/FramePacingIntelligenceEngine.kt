package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CAR-50 — Frame Pacing & Refresh Intelligence.
 *
 * Separa estrictamente el refresco de pantalla de los frame timings medidos. No deriva FPS
 * reales desde Hz, jitter ni intervalos de refresco. Las decisiones de perfil siguen
 * perteneciendo a CAR-47; este motor solo produce evidencia, una recomendación de Hz y una
 * señal tipada.
 */
class FramePacingIntelligenceEngine(
    private val policy: FramePacingPolicy = FramePacingPolicy()
) {
    private data class FastResult(
        val availability: FrameDataAvailability,
        val jitterMs: Float?,
        val sampleCount: Int,
        val confidence: Float
    )

    fun analyze(
        targetHz: Int?,
        refreshSamples: List<RefreshSample>,
        frameTimingSamples: List<FrameTimingSample>?,
        thermal: ThermalPrediction?,
        battery: BatteryGamingAssessment?,
        supportedRefreshRatesHz: List<Int>,
        interpolationState: InterpolationState,
        verifiedGameFps: Float? = null
    ): FramePacingAssessment {
        val validSlow = refreshSamples
            .asSequence()
            .filter(::isValidRefreshSample)
            .sortedBy(RefreshSample::timestampMs)
            .toList()
        val nowSlow = validSlow.lastOrNull()?.timestampMs
        val recentSlow = if (nowSlow == null) {
            emptyList()
        } else {
            val windowStart = (nowSlow - policy.windowSlowMs).coerceAtLeast(0L)
            validSlow.filter { it.timestampMs >= windowStart }
        }

        val fast = fastPath(frameTimingSamples)
        val interpolationVerified =
            interpolationState.requested &&
                interpolationState.capabilityVerified &&
                interpolationState.observedActive
        val observedGameFps = verifiedGameFps
            ?.takeIf { it.isFinite() && it > 0f }

        if (recentSlow.size < policy.minSamplesSlow) {
            val verification = when {
                targetHz != null -> RefreshVerification.REQUESTED
                validSlow.isNotEmpty() -> RefreshVerification.OBSERVED
                else -> RefreshVerification.UNVERIFIABLE
            }
            val confidenceCombined = if (
                fast.availability == FrameDataAvailability.AVAILABLE
            ) {
                (fast.confidence * jitterQuality(fast.jitterMs))
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            return FramePacingAssessment(
                targetHz = targetHz,
                observedRefreshHz = validSlow.lastOrNull()?.refreshHz,
                observedGameFps = observedGameFps,
                ewmaRefreshHz = null,
                pacingAvailability = fast.availability,
                stability = RefreshStability.INSUFFICIENT_DATA,
                varianceHz = 0f,
                stdDevHz = 0f,
                jitterMs = fast.jitterMs,
                sampleCountSlow = recentSlow.size,
                sampleCountFast = fast.sampleCount,
                confidenceSlow = 0f,
                confidenceFast = fast.confidence,
                confidenceCombined = confidenceCombined,
                evidence = buildEarlyEvidence(
                    observedGameFps = observedGameFps,
                    fast = fast
                ),
                explanation = buildString {
                    append("Muestras de refresco insuficientes: ")
                    append(recentSlow.size)
                    append("/")
                    append(policy.minSamplesSlow)
                    append(". ")
                    append(fpsExplanation(observedGameFps))
                },
                recommendedRefreshHz = null,
                verification = verification,
                interpolationVerified = interpolationVerified
            )
        }

        val hz = recentSlow.map(RefreshSample::refreshHz)
        val mean = hz.average().toFloat()
        var ewma = hz.first().toDouble()
        for (value in hz.drop(1)) {
            ewma =
                policy.ewmaAlpha * value +
                    (1f - policy.ewmaAlpha) * ewma
        }

        val variance = hz
            .map { value ->
                val delta = value - mean
                delta * delta
            }
            .average()
            .toFloat()
        val stdDev = sqrt(variance)

        val third = hz.size / 3
        val firstAverage = if (third >= 2) {
            hz.take(third).average()
        } else {
            mean.toDouble()
        }
        val lastAverage = if (third >= 2) {
            hz.takeLast(third).average()
        } else {
            mean.toDouble()
        }
        val descendingCount = hz
            .zipWithNext()
            .count { (before, after) -> after < before }
        val descendingRatio = if (hz.size > 1) {
            descendingCount.toFloat() / (hz.size - 1)
        } else {
            0f
        }
        val isDegrading =
            hz.size >= policy.minSamplesForTrend &&
                lastAverage < firstAverage * policy.degradingRatio &&
                descendingRatio >= policy.degradingDescendingRatio

        val transitionThreshold =
            policy.noiseToleranceHz * policy.transitionMultiplier
        val transitions = recentSlow
            .zipWithNext()
            .count { (before, after) ->
                abs(after.refreshHz - before.refreshHz) > transitionThreshold
            }
        val transitionRatio = if (recentSlow.size > 1) {
            transitions.toFloat() / (recentSlow.size - 1)
        } else {
            0f
        }
        val isUnstable =
            !isDegrading &&
                transitions >= policy.maxTransitionsForUnstable

        val recoverySegmentSize =
            minOf(policy.recoverySamples, recentSlow.size / 2)
        val recoveryHead = recentSlow
            .take(recoverySegmentSize)
            .map(RefreshSample::refreshHz)
        val recoveryTail = recentSlow
            .takeLast(recoverySegmentSize)
            .map(RefreshSample::refreshHz)
        val recoveryHeadMean = recoveryHead.average()
        val recoveryTailMean = recoveryTail.average()
        val recoveryTailStd = sqrt(
            recoveryTail
                .map { value ->
                    val delta = value - recoveryTailMean
                    delta * delta
                }
                .average()
                .toFloat()
        )
        val isRecovering =
            recoverySegmentSize >= 2 &&
                recoveryHeadMean <=
                recoveryTailMean * policy.recoveryHeadRatio &&
                recoveryTailStd <= policy.maxStdForStable &&
                recoveryTailMean >
                recoveryHeadMean * policy.recoveryTailBoost

        val baseStability = when {
            isRecovering -> RefreshStability.RECOVERING
            isDegrading -> RefreshStability.DEGRADING
            isUnstable -> RefreshStability.UNSTABLE
            stdDev > policy.stdDevAllowedHz ->
                RefreshStability.MINOR_VARIANCE
            else -> RefreshStability.STABLE
        }
        val stableRatio =
            hz.count { value ->
                abs(value - mean) <= policy.noiseToleranceHz
            }.toFloat() / hz.size
        val stability = if (
            baseStability == RefreshStability.STABLE &&
            stableRatio < policy.minStabilityRatio
        ) {
            RefreshStability.MINOR_VARIANCE
        } else {
            baseStability
        }

        val targetValue = targetHz?.toFloat()
        val verification = when {
            targetValue == null -> RefreshVerification.OBSERVED
            abs(mean - targetValue) > policy.notAppliedThresholdHz ->
                RefreshVerification.NOT_APPLIED
            abs(mean - targetValue) <= policy.targetObservedToleranceHz &&
                stability == RefreshStability.STABLE ->
                RefreshVerification.VERIFIED
            else -> RefreshVerification.OBSERVED
        }

        val thermalPreventive = thermal?.allowPreventiveSignal == true
        val thermalRisk = thermal?.risk
        val thermalHigh =
            thermalPreventive &&
                (
                    thermalRisk == ThermalRisk.HIGH ||
                        thermalRisk == ThermalRisk.CRITICAL
                    )
        val batteryConstrained =
            battery?.preventAggressiveProfiles == true

        val candidates = supportedRefreshRatesHz
            .asSequence()
            .distinct()
            .filter { candidate ->
                candidate.toFloat() in
                    policy.refreshMinHz..policy.refreshMaxHz
            }
            .sorted()
            .toList()
        val degradingCeiling = minOf(
            targetValue ?: mean,
            mean + policy.meanToleranceHz
        )
        val recommendation = when {
            candidates.isEmpty() -> null
            batteryConstrained -> candidates.minOrNull()
            thermalHigh -> candidates.minOrNull()
            thermalPreventive ->
                candidates
                    .filter { it <= policy.conservativeRefreshCapHz }
                    .maxOrNull()
                    ?: candidates.minOrNull()
            stability == RefreshStability.DEGRADING ->
                candidates
                    .filter { it < degradingCeiling }
                    .maxOrNull()
                    ?: candidates.minOrNull()
            stability == RefreshStability.UNSTABLE ->
                candidates.minOrNull()
            fast.jitterMs != null &&
                fast.jitterMs > policy.jitterThresholdMs ->
                candidates
                    .filter { it <= policy.conservativeRefreshCapHz }
                    .maxOrNull()
                    ?: candidates.minOrNull()
            else ->
                candidates
                    .filter {
                        it.toFloat() <=
                            mean + policy.meanToleranceHz
                    }
                    .maxOrNull()
                    ?: candidates.minOrNull()
        }?.takeIf(candidates::contains)

        val countConfidence = (
            recentSlow.size.toFloat() /
                (
                    policy.minSamplesSlow *
                        policy.confidenceSampleSaturationMultiplier
                    )
            ).coerceIn(0f, 1f)
        val degradingDropStrength = if (firstAverage > 0.0) {
            ((firstAverage - lastAverage) / firstAverage)
                .toFloat()
                .coerceIn(0f, 1f)
        } else {
            0f
        }
        val recoveryLiftStrength = if (recoveryTailMean > 0.0) {
            ((recoveryTailMean - recoveryHeadMean) / recoveryTailMean)
                .toFloat()
                .coerceIn(0f, 1f)
        } else {
            0f
        }
        val recoveryStabilityStrength =
            (
                1f -
                    recoveryTailStd /
                    policy.maxStdForStable
                ).coerceIn(0f, 1f)

        val confidenceSlow = when (stability) {
            RefreshStability.STABLE -> {
                val stdPenalty = (
                    stdDev /
                        (
                            policy.maxStdForStable *
                                policy.confidenceSlowStdDevDivisor
                            )
                    ).coerceIn(
                    0f,
                    policy.confidenceStableStdCap
                )
                val stableEvidence =
                    stableRatio * policy.confidenceStableRatioWeight +
                        (1f - policy.confidenceStableRatioWeight)
                countConfidence * (1f - stdPenalty) * stableEvidence
            }
            RefreshStability.MINOR_VARIANCE ->
                countConfidence * (
                    policy.confidenceMinorBase +
                        policy.confidenceMinorVarWeight *
                        (
                            stdDev /
                                policy.stdDevAllowedHz
                            ).coerceIn(0f, 1f)
                    )
            RefreshStability.UNSTABLE ->
                countConfidence * (
                    policy.confidenceUnstableBase +
                        policy.confidenceUnstableTransWeight *
                        transitionRatio.coerceIn(0f, 1f)
                    )
            RefreshStability.DEGRADING ->
                countConfidence * (
                    policy.confidenceDegradingBase +
                        policy.confidenceDegradingDescWeight *
                        descendingRatio.coerceIn(0f, 1f) +
                        policy.confidenceDegradingDiffWeight *
                        degradingDropStrength
                    )
            RefreshStability.RECOVERING ->
                countConfidence * (
                    policy.confidenceRecoveringBase +
                        policy.confidenceRecoveringDiffWeight *
                        recoveryLiftStrength +
                        policy.confidenceRecoveringStabWeight *
                        recoveryStabilityStrength
                    )
            RefreshStability.INSUFFICIENT_DATA -> 0f
        }.coerceIn(0f, 1f)

        val fastQuality = jitterQuality(fast.jitterMs)
        val confidenceCombined = when {
            confidenceSlow > 0f &&
                fast.availability == FrameDataAvailability.AVAILABLE -> {
                val weightSum =
                    policy.confidenceCombinedSlowWeight +
                        policy.confidenceCombinedFastWeight
                (
                    confidenceSlow *
                        policy.confidenceCombinedSlowWeight +
                        fast.confidence *
                        policy.confidenceCombinedFastWeight *
                        fastQuality
                    ) / weightSum
            }
            confidenceSlow > 0f -> confidenceSlow
            fast.availability == FrameDataAvailability.AVAILABLE ->
                fast.confidence * fastQuality
            else -> 0f
        }.coerceIn(0f, 1f)

        val evidence = buildList {
            add(
                "Refresco medido reciente: " +
                    recentSlow
                        .takeLast(5)
                        .joinToString { sample ->
                            sample.refreshHz.toString() + " Hz"
                        } +
                    "."
            )
            if (fast.availability == FrameDataAvailability.AVAILABLE) {
                add(
                    "Frame timings medidos disponibles (" +
                        fast.sampleCount +
                        " muestras); jitter " +
                        (fast.jitterMs ?: 0f) +
                        " ms."
                )
            } else {
                add(
                    "Frame pacing no verificable: faltan frame timings recientes suficientes."
                )
            }
            add(fpsExplanation(observedGameFps))
            thermal?.let { prediction ->
                add("CAR-48: " + prediction.reason)
            }
            battery?.let { assessment ->
                add("CAR-49: " + assessment.reason)
            }
            if (interpolationVerified) {
                add(
                    "Interpolación verificada por solicitud, capacidad y estado activo observados."
                )
            } else if (interpolationState.requested) {
                add(
                    "Interpolación no verificada; CAR-50 permanece fail-closed."
                )
            }
        }

        val explanation = buildString {
            append(stabilityLabel(stability))
            append(": refresco medio ")
            append(mean.toInt())
            append(" Hz, desviación ")
            append(stdDev)
            append(" Hz, ")
            append(recentSlow.size)
            append(" muestras lentas y ")
            append(fast.sampleCount)
            append(" muestras rápidas. ")
            append(fpsExplanation(observedGameFps))
        }

        return FramePacingAssessment(
            targetHz = targetHz,
            observedRefreshHz = mean,
            observedGameFps = observedGameFps,
            ewmaRefreshHz = ewma.toFloat(),
            pacingAvailability = fast.availability,
            stability = stability,
            varianceHz = variance,
            stdDevHz = stdDev,
            jitterMs = fast.jitterMs,
            sampleCountSlow = recentSlow.size,
            sampleCountFast = fast.sampleCount,
            confidenceSlow = confidenceSlow,
            confidenceFast = fast.confidence,
            confidenceCombined = confidenceCombined,
            evidence = evidence,
            explanation = explanation,
            recommendedRefreshHz = recommendation,
            verification = verification,
            interpolationVerified = interpolationVerified
        )
    }

    fun toAdaptiveSignal(
        assessment: FramePacingAssessment
    ): FramePacingAdaptiveSignal {
        val actionableState = when (assessment.stability) {
            RefreshStability.UNSTABLE,
            RefreshStability.DEGRADING,
            RefreshStability.RECOVERING -> true
            else ->
                assessment.verification ==
                    RefreshVerification.NOT_APPLIED
        }
        val shouldNotify =
            actionableState &&
                assessment.confidenceCombined >= policy.minConfidence
        return FramePacingAdaptiveSignal(
            stability = assessment.stability,
            verification = assessment.verification,
            recommendedHz = assessment.recommendedRefreshHz,
            confidence = assessment.confidenceCombined,
            shouldNotify = shouldNotify
        )
    }

    private fun fastPath(
        frameTimingSamples: List<FrameTimingSample>?
    ): FastResult {
        val valid = frameTimingSamples
            .orEmpty()
            .asSequence()
            .filter(::isValidFrameTimingSample)
            .sortedBy(FrameTimingSample::timestampMs)
            .toList()
        val nowFast = valid.lastOrNull()?.timestampMs
            ?: return FastResult(
                availability = FrameDataAvailability.PACING_UNAVAILABLE,
                jitterMs = null,
                sampleCount = 0,
                confidence = 0f
            )
        val windowStart =
            (nowFast - policy.windowFastMs).coerceAtLeast(0L)
        val recent = valid.filter { sample ->
            sample.timestampMs >= windowStart
        }
        val available =
            recent.size >= policy.minSamplesFast
        val jitter = if (available) {
            val values = recent.map(FrameTimingSample::frameTimeMs)
            val mean = values.average().toFloat()
            values
                .map { value -> abs(value - mean) }
                .average()
                .toFloat()
        } else {
            null
        }
        val confidence = if (available) {
            (
                recent.size.toFloat() /
                    (
                        policy.minSamplesFast *
                            policy.confidenceSampleSaturationMultiplier
                        )
                ).coerceIn(0f, 1f)
        } else {
            0f
        }
        return FastResult(
            availability = if (available) {
                FrameDataAvailability.AVAILABLE
            } else {
                FrameDataAvailability.PACING_UNAVAILABLE
            },
            jitterMs = jitter,
            sampleCount = recent.size,
            confidence = confidence
        )
    }

    private fun jitterQuality(jitterMs: Float?): Float =
        jitterMs
            ?.let { jitter ->
                (
                    1f -
                        jitter /
                        (
                            policy.jitterThresholdMs *
                                policy.confidenceJitterDivisor
                            )
                    ).coerceIn(0f, 1f)
            }
            ?: policy.confidenceJitterBase

    private fun isValidRefreshSample(
        sample: RefreshSample
    ): Boolean =
        sample.timestampMs >= 0L &&
            sample.refreshHz.isFinite() &&
            sample.refreshHz in
            policy.refreshMinHz..policy.refreshMaxHz

    private fun isValidFrameTimingSample(
        sample: FrameTimingSample
    ): Boolean =
        sample.timestampMs >= 0L &&
            sample.frameTimeMs.isFinite() &&
            sample.frameTimeMs > 0f &&
            sample.frameTimeMs < policy.frameTimeMaxMs

    private fun buildEarlyEvidence(
        observedGameFps: Float?,
        fast: FastResult
    ): List<String> = buildList {
        if (fast.availability == FrameDataAvailability.AVAILABLE) {
            add(
                "Frame timings medidos disponibles (" +
                    fast.sampleCount +
                    " muestras)."
            )
        } else {
            add(
                "Frame pacing no verificable: faltan frame timings recientes suficientes."
            )
        }
        add(fpsExplanation(observedGameFps))
    }

    private fun fpsExplanation(
        observedGameFps: Float?
    ): String =
        observedGameFps?.let { fps ->
            "FPS real verificado externamente: " + fps + "."
        } ?: (
            "FPS real no observable con las señales actuales; " +
                "no se infiere desde el refresco ni desde el jitter."
            )

    private fun stabilityLabel(
        stability: RefreshStability
    ): String = when (stability) {
        RefreshStability.STABLE -> "Refresco estable"
        RefreshStability.MINOR_VARIANCE -> "Variación de refresco"
        RefreshStability.UNSTABLE -> "Refresco inestable"
        RefreshStability.INSUFFICIENT_DATA -> "Datos insuficientes"
        RefreshStability.RECOVERING -> "Refresco recuperándose"
        RefreshStability.DEGRADING -> "Refresco degradándose"
    }
}

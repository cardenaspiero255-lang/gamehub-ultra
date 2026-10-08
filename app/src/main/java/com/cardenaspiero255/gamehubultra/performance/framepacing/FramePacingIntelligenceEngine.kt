package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.domain.ThermalRisk
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class FramePacingIntelligenceEngine(
    val policy: FramePacingPolicy = FramePacingPolicy()
) {
    fun analyze(
        targetHz: Int?,
        refreshSamples: List<RefreshSample>,
        frameTimingSamples: List<FrameTimingSample>?,
        thermal: ThermalPrediction?,
        battery: BatteryGamingAssessment?,
        supportedRefreshRatesHz: List<Int>,
        interpolationState: InterpolationState
    ): FramePacingAssessment {
        val interpolationVerified =
            interpolationState.requested &&
                interpolationState.capabilityVerified &&
                interpolationState.observedActive

        val orderedSlow = refreshSamples
            .asSequence()
            .filter {
                it.timestampMs >= 0L &&
                    it.refreshHz.isFinite() &&
                    it.refreshHz in policy.minRefreshHz..policy.maxRefreshHz
            }
            .sortedBy(RefreshSample::timestampMs)
            .toList()

        val slowNow = orderedSlow.lastOrNull()?.timestampMs
        val recentSlow = if (slowNow == null) {
            emptyList()
        } else {
            orderedSlow.filter {
                slowNow - it.timestampMs <= policy.windowSlowMs
            }
        }

        if (recentSlow.size < policy.minSamplesSlow) {
            val observed = recentSlow.lastOrNull()?.refreshHz
                ?: orderedSlow.lastOrNull()?.refreshHz
            val verification = when {
                targetHz != null -> RefreshVerification.REQUESTED
                observed != null -> RefreshVerification.OBSERVED
                else -> RefreshVerification.UNVERIFIABLE
            }
            return FramePacingAssessment(
                targetHz = targetHz,
                observedRefreshHz = observed,
                ewmaRefreshHz = null,
                pacingAvailability = FrameDataAvailability.PACING_UNAVAILABLE,
                stability = RefreshStability.INSUFFICIENT_DATA,
                varianceHz = 0f,
                stdDevHz = 0f,
                jitterMs = null,
                sampleCountSlow = recentSlow.size,
                sampleCountFast = 0,
                confidenceSlow = 0f,
                confidenceCombined = 0f,
                evidence = listOf(
                    "Muestras lentas insuficientes: ${recentSlow.size}/${policy.minSamplesSlow}."
                ),
                explanation =
                    "INSUFFICIENT_DATA: la ventana lenta no contiene evidencia suficiente; Hz no se interpreta como FPS ni frame pacing.",
                recommendedRefreshHz = null,
                verification = verification,
                interpolationVerified = interpolationVerified
            )
        }

        val hz = recentSlow.map(RefreshSample::refreshHz)
        val mean = hz.average().toFloat()
        val ewma = ewma(hz)
        val variance = variance(hz, mean)
        val stdDev = sqrt(variance)
        val stableRatio =
            hz.count { abs(it - mean) <= policy.noiseToleranceHz }.toFloat() /
                hz.size.toFloat()

        val transitionThreshold =
            policy.noiseToleranceHz * policy.transitionMultiplier
        val transitions = recentSlow.zipWithNext().count { (before, after) ->
            abs(after.refreshHz - before.refreshHz) > transitionThreshold
        }

        val recovering = isRecovering(recentSlow)
        val degrading = !recovering && isDegrading(hz)
        val unstable =
            !recovering &&
                !degrading &&
                transitions >= policy.maxTransitionsForUnstable

        val baseStability = when {
            recovering -> RefreshStability.RECOVERING
            degrading -> RefreshStability.DEGRADING
            unstable -> RefreshStability.UNSTABLE
            stdDev > policy.stdDevAllowedHz -> RefreshStability.MINOR_VARIANCE
            else -> RefreshStability.STABLE
        }
        val stability =
            if (
                baseStability == RefreshStability.STABLE &&
                stableRatio < policy.minStabilityRatio
            ) {
                RefreshStability.MINOR_VARIANCE
            } else {
                baseStability
            }

        val orderedFast = frameTimingSamples
            .orEmpty()
            .asSequence()
            .filter {
                it.timestampMs >= 0L &&
                    it.frameTimeMs.isFinite() &&
                    it.frameTimeMs > 0f &&
                    it.frameTimeMs <= policy.maxFrameTimeMs
            }
            .sortedBy(FrameTimingSample::timestampMs)
            .toList()

        val fastNow = orderedFast.lastOrNull()?.timestampMs
        val recentFast = if (fastNow == null) {
            emptyList()
        } else {
            orderedFast.filter {
                fastNow - it.timestampMs <= policy.windowFastMs
            }
        }
        val pacingAvailable =
            recentFast.size >= policy.minSamplesFast
        val jitter = if (pacingAvailable) {
            meanAbsoluteDeviation(
                recentFast.map(FrameTimingSample::frameTimeMs)
            )
        } else {
            null
        }
        val pacingAvailability =
            if (pacingAvailable) {
                FrameDataAvailability.AVAILABLE
            } else {
                FrameDataAvailability.PACING_UNAVAILABLE
            }

        val confidenceSlow = slowConfidence(
            sampleCount = recentSlow.size,
            stdDev = stdDev,
            stabilityRatio = stableRatio
        )
        val confidenceCombined = combinedConfidence(
            confidenceSlow = confidenceSlow,
            jitter = jitter
        )

        val verification = when {
            targetHz == null -> RefreshVerification.OBSERVED
            abs(mean - targetHz.toFloat()) > policy.notAppliedThresholdHz ->
                RefreshVerification.NOT_APPLIED
            abs(mean - targetHz.toFloat()) <= policy.targetObservedToleranceHz &&
                stability == RefreshStability.STABLE &&
                confidenceSlow >= policy.minConfidence ->
                RefreshVerification.VERIFIED
            else -> RefreshVerification.OBSERVED
        }

        val thermalHigh =
            thermal?.risk == ThermalRisk.HIGH ||
                thermal?.risk == ThermalRisk.CRITICAL
        val thermalPreventive =
            thermal?.allowPreventiveSignal == true
        val batteryConstrained =
            battery?.preventAggressiveProfiles == true

        val candidates = supportedRefreshRatesHz
            .asSequence()
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()

        val recommended = recommendRefresh(
            targetHz = targetHz,
            observedMeanHz = mean,
            candidates = candidates,
            stability = stability,
            jitter = jitter,
            thermalHigh = thermalHigh,
            thermalPreventive = thermalPreventive,
            batteryConstrained = batteryConstrained
        )

        val evidence = buildList {
            add("Refresco observado medio: ${format(mean)} Hz.")
            add("EWMA de refresco: ${format(ewma)} Hz.")
            add("Desviación estándar: ${format(stdDev)} Hz.")
            add("Ratio estable: ${format(stableRatio)}.")
            add("Transiciones relevantes: $transitions.")
            when (stability) {
                RefreshStability.RECOVERING ->
                    add("La cola reciente recuperó un nivel estable frente al periodo anterior.")
                RefreshStability.DEGRADING ->
                    add("Se detectó una degradación descendente sostenida del refresco.")
                RefreshStability.UNSTABLE ->
                    add("Se detectaron oscilaciones repetidas de refresco.")
                else -> Unit
            }
            if (pacingAvailability == FrameDataAvailability.AVAILABLE) {
                add("Frame pacing disponible con ${recentFast.size} muestras válidas.")
                jitter?.let { add("Jitter medio absoluto: ${format(it)} ms.") }
            } else {
                add("Frame pacing no disponible: no hay suficientes frame timings legítimos en la ventana rápida.")
            }
            thermal?.let {
                add(
                    "Señal CAR-48: riesgo ${it.risk}, confianza ${format(it.confidence)}, " +
                        "preventiva=${it.allowPreventiveSignal}; correlación, no causalidad."
                )
            }
            battery?.let {
                add(
                    "Señal CAR-49: ${it.recommendation}, " +
                        "restricción=${it.preventAggressiveProfiles}; correlación, no causalidad."
                )
            }
            if (interpolationState.requested && !interpolationVerified) {
                add("Interpolación solicitada pero no verificada como activa por una fuente compatible.")
            }
        }

        val explanation = buildString {
            append(stability.name)
            append(" · refresh=")
            append(format(mean))
            append(" Hz · EWMA=")
            append(format(ewma))
            append(" Hz · std=")
            append(format(stdDev))
            append(" Hz · slow=")
            append(recentSlow.size)
            append(" · fast=")
            append(recentFast.size)
            append(" · pacing=")
            append(pacingAvailability.name)
            append(" · verification=")
            append(verification.name)
            append(". Los Hz de pantalla no se interpretan como FPS del juego.")
        }

        return FramePacingAssessment(
            targetHz = targetHz,
            observedRefreshHz = mean,
            ewmaRefreshHz = ewma,
            pacingAvailability = pacingAvailability,
            stability = stability,
            varianceHz = variance,
            stdDevHz = stdDev,
            jitterMs = jitter,
            sampleCountSlow = recentSlow.size,
            sampleCountFast = recentFast.size,
            confidenceSlow = confidenceSlow,
            confidenceCombined = confidenceCombined,
            evidence = evidence,
            explanation = explanation,
            recommendedRefreshHz = recommended,
            verification = verification,
            interpolationVerified = interpolationVerified
        )
    }

    fun toAdaptiveSignal(
        assessment: FramePacingAssessment
    ): FramePacingAdaptiveSignal {
        val actionableState =
            assessment.stability == RefreshStability.UNSTABLE ||
                assessment.stability == RefreshStability.DEGRADING ||
                assessment.stability == RefreshStability.RECOVERING ||
                assessment.verification == RefreshVerification.NOT_APPLIED
        val shouldNotify =
            actionableState &&
                assessment.confidenceSlow >= policy.minConfidence

        return FramePacingAdaptiveSignal(
            stability = assessment.stability,
            verification = assessment.verification,
            recommendedHz = assessment.recommendedRefreshHz,
            confidence = assessment.confidenceCombined,
            shouldNotify = shouldNotify
        )
    }

    private fun recommendRefresh(
        targetHz: Int?,
        observedMeanHz: Float,
        candidates: List<Int>,
        stability: RefreshStability,
        jitter: Float?,
        thermalHigh: Boolean,
        thermalPreventive: Boolean,
        batteryConstrained: Boolean
    ): Int? {
        if (candidates.isEmpty()) return null
        if (batteryConstrained || thermalHigh) {
            return candidates.minOrNull()
        }
        if (thermalPreventive) {
            return candidates
                .filter { it <= policy.thermalSafeHz }
                .maxOrNull()
                ?: candidates.minOrNull()
        }
        if (stability == RefreshStability.UNSTABLE) {
            return candidates.minOrNull()
        }
        if (stability == RefreshStability.DEGRADING) {
            val reference = targetHz?.toFloat() ?: observedMeanHz
            return candidates
                .filter { it.toFloat() < reference }
                .maxOrNull()
                ?: candidates.minOrNull()
        }
        if (jitter != null && jitter > policy.jitterThresholdMs) {
            return candidates
                .filter { it <= policy.thermalSafeHz }
                .maxOrNull()
                ?: candidates.minOrNull()
        }
        if (targetHz != null && targetHz in candidates) {
            return targetHz
        }
        return candidates
            .filter { it.toFloat() <= observedMeanHz + policy.meanToleranceHz }
            .maxOrNull()
            ?: candidates.minOrNull()
    }

    private fun isRecovering(
        samples: List<RefreshSample>
    ): Boolean {
        if (samples.size < policy.minRecoveryStableSamples + 2) return false

        val maxTail = minOf(policy.recoverySamples, samples.size - 2)
        for (tailSize in maxTail downTo policy.minRecoveryStableSamples) {
            val tail = samples.takeLast(tailSize).map(RefreshSample::refreshHz)
            val head = samples.dropLast(tailSize).map(RefreshSample::refreshHz)
            if (head.size < 2) continue

            val tailMean = tail.average().toFloat()
            val headMean = head.average().toFloat()
            val tailStd = sqrt(variance(tail, tailMean))

            if (
                headMean <= tailMean * policy.recoveryHeadRatio &&
                tailMean >= headMean * policy.recoveryTailBoost &&
                tailStd <= policy.maxStdForStable
            ) {
                return true
            }
        }
        return false
    }

    private fun isDegrading(
        values: List<Float>
    ): Boolean {
        if (values.size < policy.degradingMinSamples) return false

        val segmentSize = maxOf(2, values.size / 3)
        val firstMean = values.take(segmentSize).average().toFloat()
        val lastMean = values.takeLast(segmentSize).average().toFloat()
        val descendingRatio =
            values.zipWithNext().count { (before, after) ->
                after < before - policy.noiseToleranceHz
            }.toFloat() / (values.size - 1).toFloat()

        return lastMean <= firstMean * policy.degradingRatio &&
            descendingRatio >= policy.degradingDescendingRatio
    }

    private fun slowConfidence(
        sampleCount: Int,
        stdDev: Float,
        stabilityRatio: Float
    ): Float {
        val sampleFactor =
            (sampleCount.toFloat() / (policy.minSamplesSlow * 2f))
                .coerceIn(0f, 1f)
        val spreadDenominator =
            policy.maxStdForStable * policy.confidenceSpreadMultiplier
        val spreadFactor =
            (1f - stdDev / spreadDenominator)
                .coerceIn(0f, 1f)
        return (
            sampleFactor * policy.confidenceSampleWeight +
                stabilityRatio.coerceIn(0f, 1f) * policy.confidenceStabilityWeight +
                spreadFactor * policy.confidenceSpreadWeight
            ).coerceIn(0f, 1f)
    }

    private fun combinedConfidence(
        confidenceSlow: Float,
        jitter: Float?
    ): Float {
        if (jitter == null) return confidenceSlow
        val fastQuality =
            (1f - jitter / (policy.jitterThresholdMs * 2f))
                .coerceIn(0f, 1f)
        return (
            confidenceSlow * (1f - policy.fastConfidenceWeight) +
                fastQuality * policy.fastConfidenceWeight
            ).coerceIn(0f, 1f)
    }

    private fun ewma(values: List<Float>): Float {
        var current = values.first().toDouble()
        values.drop(1).forEach { value ->
            current =
                policy.ewmaAlpha * value.toDouble() +
                    (1.0 - policy.ewmaAlpha.toDouble()) * current
        }
        return current.toFloat()
    }

    private fun variance(
        values: List<Float>,
        mean: Float
    ): Float {
        if (values.isEmpty()) return 0f
        return values
            .map { value ->
                val delta = value - mean
                delta * delta
            }
            .average()
            .toFloat()
    }

    private fun meanAbsoluteDeviation(
        values: List<Float>
    ): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        return values
            .map { abs(it - mean) }
            .average()
            .toFloat()
    }

    private fun format(value: Float): String =
        String.format(Locale.ROOT, "%.2f", value)
}

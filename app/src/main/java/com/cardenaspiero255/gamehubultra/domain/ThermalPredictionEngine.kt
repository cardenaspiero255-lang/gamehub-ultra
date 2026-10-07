package com.cardenaspiero255.gamehubultra.domain

import kotlin.math.abs
import kotlin.math.max

enum class ThermalTrend {
    UNKNOWN,
    STABLE,
    COOLING,
    RISING,
    RISING_FAST
}

enum class ThermalRisk {
    UNKNOWN,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

enum class ThermalSignalMode {
    HEADROOM_AND_STATUS,
    HEADROOM_ONLY,
    STATUS_ONLY,
    UNAVAILABLE
}

enum class ThermalEvidenceKind {
    MEASURED_HEADROOM,
    MEASURED_STATUS,
    PREDICTED_TREND,
    PREDICTED_RISK,
    FALLBACK_LIMITATION
}

data class ThermalEvidence(
    val kind: ThermalEvidenceKind,
    val detail: String,
    val measured: Boolean,
    val predicted: Boolean
)

/**
 * Calibratable CAR-48 policy.
 *
 * Every default below is an intentionally conservative initial heuristic for GameHub Ultra,
 * not a universal thermal specification for Android devices. Future device/game calibration
 * can replace these values without rewriting [ThermalPredictionEngine].
 */
data class ThermalPredictionPolicy(
    val minimumSamples: Int = 5,
    val minimumWindowMillis: Long = 40_000L,
    val maximumWindowMillis: Long = 120_000L,
    val risingSlopePerMinute: Float = 0.06f,
    val fastRisingSlopePerMinute: Float = 0.14f,
    val highRiskHeadroom: Float = 0.72f,
    val criticalRiskHeadroom: Float = 0.82f,
    val minimumPreventiveConfidence: Float = 0.78f,
    val noiseTolerance: Float = 0.04f,
    val minimumRisingFraction: Float = 0.70f,
    val recoveryHeadroom: Float = 0.58f,
    val recoverySlopePerMinute: Float = -0.04f,
    val accelerationThresholdPerMinuteSquared: Float = 0.04f,
    val missingHeadroomConfidencePenalty: Float = 0.25f,
    val predictionHorizonMillis: Long = 60_000L,
    val severeThermalStatus: Int = 3,
) {
    init {
        require(minimumSamples >= 3)
        require(minimumWindowMillis > 0L)
        require(maximumWindowMillis >= minimumWindowMillis)
        require(risingSlopePerMinute > 0f)
        require(fastRisingSlopePerMinute >= risingSlopePerMinute)
        require(highRiskHeadroom >= 0f)
        require(criticalRiskHeadroom > highRiskHeadroom)
        require(minimumPreventiveConfidence in 0f..1f)
        require(noiseTolerance >= 0f)
        require(minimumRisingFraction in 0.5f..1f)
        require(recoveryHeadroom >= 0f && recoveryHeadroom < highRiskHeadroom)
        require(recoverySlopePerMinute < 0f)
        require(accelerationThresholdPerMinuteSquared >= 0f)
        require(missingHeadroomConfidencePenalty in 0f..1f)
        require(predictionHorizonMillis > 0L)
        require(severeThermalStatus >= 0)
    }
}

data class ThermalPrediction(
    val trend: ThermalTrend,
    val risk: ThermalRisk,
    val confidence: Float,
    val signalMode: ThermalSignalMode,
    val slopePerMinute: Float?,
    val accelerationPerMinuteSquared: Float?,
    val latestMeasuredHeadroom: Float?,
    val projectedHeadroom: Float?,
    val allowPreventiveSignal: Boolean,
    val recovering: Boolean,
    val evidence: List<ThermalEvidence>,
    val reason: String
)

class ThermalPredictionEngine(
    val policy: ThermalPredictionPolicy = ThermalPredictionPolicy()
) {
    fun predict(samples: List<SessionCoachSnapshot>): ThermalPrediction {
        val ordered = samples
            .asSequence()
            .filter { it.timestampMillis >= 0L }
            .sortedBy { it.timestampMillis }
            .toList()

        if (ordered.isEmpty()) {
            return unknown(ThermalSignalMode.UNAVAILABLE, "No hay muestras térmicas.")
        }

        val latestTimestamp = ordered.last().timestampMillis
        val recent = ordered.filter {
            it.timestampMillis >= latestTimestamp - policy.maximumWindowMillis
        }
        val spanMillis = (recent.last().timestampMillis - recent.first().timestampMillis)
            .coerceAtLeast(0L)
        val signalMode = signalMode(recent)

        if (recent.size < policy.minimumSamples || spanMillis < policy.minimumWindowMillis) {
            return unknown(
                signalMode,
                "Muestras insuficientes para una predicción térmica fiable."
            )
        }

        val latestStatus = recent.asReversed()
            .firstNotNullOfOrNull { it.thermalStatus }
        val headroomSamples = recent.mapNotNull { sample ->
            sample.thermalHeadroom
                ?.takeIf { it.isFinite() && it >= 0f }
                ?.let { value -> sample.timestampMillis to value }
        }
        val latestHeadroom = headroomSamples.lastOrNull()?.second
        val evidence = mutableListOf<ThermalEvidence>()

        latestStatus?.let { status ->
            evidence += ThermalEvidence(
                kind = ThermalEvidenceKind.MEASURED_STATUS,
                detail = "Android informó estado térmico " + status + ".",
                measured = true,
                predicted = false
            )
        }
        latestHeadroom?.let { headroom ->
            evidence += ThermalEvidence(
                kind = ThermalEvidenceKind.MEASURED_HEADROOM,
                detail = "Thermal headroom medido: " + headroom + ".",
                measured = true,
                predicted = false
            )
        }

        if (headroomSamples.size < policy.minimumSamples) {
            val risk = when {
                latestStatus == null -> ThermalRisk.UNKNOWN
                latestStatus >= policy.severeThermalStatus -> ThermalRisk.CRITICAL
                latestStatus >= max(1, policy.severeThermalStatus - 1) -> ThermalRisk.MODERATE
                else -> ThermalRisk.LOW
            }
            val confidence = statusOnlyConfidence(recent, spanMillis, latestStatus)
            evidence += ThermalEvidence(
                kind = ThermalEvidenceKind.FALLBACK_LIMITATION,
                detail = "El dispositivo no aportó thermal headroom suficiente; la confianza fue reducida.",
                measured = false,
                predicted = false
            )
            if (risk != ThermalRisk.UNKNOWN) {
                evidence += ThermalEvidence(
                    kind = ThermalEvidenceKind.PREDICTED_RISK,
                    detail = "Riesgo estimado con fallback de estado térmico: " + risk + ".",
                    measured = false,
                    predicted = true
                )
            }
            return ThermalPrediction(
                trend = ThermalTrend.UNKNOWN,
                risk = risk,
                confidence = confidence,
                signalMode = signalMode,
                slopePerMinute = null,
                accelerationPerMinuteSquared = null,
                latestMeasuredHeadroom = null,
                projectedHeadroom = null,
                allowPreventiveSignal = false,
                recovering = false,
                evidence = evidence,
                reason = "Predicción limitada: thermal headroom no disponible o insuficiente."
            )
        }

        val slope = slopePerMinute(headroomSamples)
        val deltas = headroomSamples.zipWithNext { before, after -> after.second - before.second }
        val risingFraction = fractionMatching(deltas) { it > policy.noiseTolerance }
        val coolingFraction = fractionMatching(deltas) { it < -policy.noiseTolerance }

        val trend = when {
            slope <= policy.recoverySlopePerMinute &&
                coolingFraction >= policy.minimumRisingFraction -> ThermalTrend.COOLING
            slope >= policy.fastRisingSlopePerMinute &&
                risingFraction >= policy.minimumRisingFraction -> ThermalTrend.RISING_FAST
            slope >= policy.risingSlopePerMinute &&
                risingFraction >= policy.minimumRisingFraction -> ThermalTrend.RISING
            else -> ThermalTrend.STABLE
        }

        val acceleration = accelerationPerMinuteSquared(headroomSamples)
        val confidence = headroomConfidence(
            recent = recent,
            spanMillis = spanMillis,
            headroomCount = headroomSamples.size,
            consistency = when (trend) {
                ThermalTrend.RISING,
                ThermalTrend.RISING_FAST -> risingFraction
                ThermalTrend.COOLING -> coolingFraction
                ThermalTrend.STABLE -> 1f - max(risingFraction, coolingFraction)
                ThermalTrend.UNKNOWN -> 0f
            }
        )
        val horizonMinutes = policy.predictionHorizonMillis / 60_000f
        val projected = (
            checkNotNull(latestHeadroom) +
                max(0f, slope) * horizonMinutes +
                0.5f * max(0f, acceleration) * horizonMinutes * horizonMinutes
            ).coerceAtLeast(0f)

        val risk = when {
            latestStatus != null && latestStatus >= policy.severeThermalStatus ->
                ThermalRisk.CRITICAL
            latestHeadroom >= policy.criticalRiskHeadroom ->
                ThermalRisk.CRITICAL
            trend in RISING_TRENDS &&
                (latestHeadroom >= policy.highRiskHeadroom ||
                    projected >= policy.highRiskHeadroom) ->
                ThermalRisk.HIGH
            trend in RISING_TRENDS || latestHeadroom >= policy.recoveryHeadroom ->
                ThermalRisk.MODERATE
            else -> ThermalRisk.LOW
        }

        val recovering =
            trend == ThermalTrend.COOLING &&
                latestHeadroom <= policy.recoveryHeadroom

        if (trend != ThermalTrend.UNKNOWN) {
            evidence += ThermalEvidence(
                kind = ThermalEvidenceKind.PREDICTED_TREND,
                detail = "Tendencia estimada " + trend + " con pendiente " + slope + " por minuto.",
                measured = false,
                predicted = true
            )
        }
        evidence += ThermalEvidence(
            kind = ThermalEvidenceKind.PREDICTED_RISK,
            detail = "Riesgo térmico estimado: " + risk +
                "; confianza " + (confidence * 100).toInt() + "%.",
            measured = false,
            predicted = true
        )

        val allowPreventiveSignal =
            risk >= ThermalRisk.HIGH &&
                confidence >= policy.minimumPreventiveConfidence &&
                trend in RISING_TRENDS

        return ThermalPrediction(
            trend = trend,
            risk = risk,
            confidence = confidence,
            signalMode = signalMode,
            slopePerMinute = slope,
            accelerationPerMinuteSquared = acceleration,
            latestMeasuredHeadroom = latestHeadroom,
            projectedHeadroom = projected,
            allowPreventiveSignal = allowPreventiveSignal,
            recovering = recovering,
            evidence = evidence,
            reason = when {
                recovering -> "La presión térmica reciente muestra recuperación sostenida."
                allowPreventiveSignal ->
                    "La tendencia térmica sostenida justifica una señal preventiva hacia CAR 47."
                risk >= ThermalRisk.HIGH ->
                    "Hay riesgo térmico alto, pero aún no se cumplen todas las garantías preventivas."
                else -> "No hay evidencia suficiente para una bajada preventiva."
            }
        )
    }

    private fun signalMode(samples: List<SessionCoachSnapshot>): ThermalSignalMode {
        val hasHeadroom = samples.any {
            it.thermalHeadroom?.let { value -> value.isFinite() && value >= 0f } == true
        }
        val hasStatus = samples.any { it.thermalStatus != null }
        return when {
            hasHeadroom && hasStatus -> ThermalSignalMode.HEADROOM_AND_STATUS
            hasHeadroom -> ThermalSignalMode.HEADROOM_ONLY
            hasStatus -> ThermalSignalMode.STATUS_ONLY
            else -> ThermalSignalMode.UNAVAILABLE
        }
    }

    private fun headroomConfidence(
        recent: List<SessionCoachSnapshot>,
        spanMillis: Long,
        headroomCount: Int,
        consistency: Float
    ): Float {
        val sampleScore = (recent.size.toFloat() / policy.minimumSamples).coerceIn(0f, 1f)
        val spanScore = (spanMillis.toFloat() / policy.minimumWindowMillis).coerceIn(0f, 1f)
        val coverage = (headroomCount.toFloat() / recent.size).coerceIn(0f, 1f)
        return (
            0.35f * sampleScore +
                0.25f * spanScore +
                0.25f * consistency.coerceIn(0f, 1f) +
                0.15f * coverage
            ).coerceIn(0f, 1f)
    }

    private fun statusOnlyConfidence(
        recent: List<SessionCoachSnapshot>,
        spanMillis: Long,
        latestStatus: Int?
    ): Float {
        val sampleScore = (recent.size.toFloat() / policy.minimumSamples).coerceIn(0f, 1f)
        val spanScore = (spanMillis.toFloat() / policy.minimumWindowMillis).coerceIn(0f, 1f)
        val statusScore = if (latestStatus == null) 0f else 1f
        return (
            0.45f * sampleScore +
                0.30f * spanScore +
                0.25f * statusScore -
                policy.missingHeadroomConfidencePenalty
            ).coerceIn(0f, 1f)
    }

    private fun slopePerMinute(points: List<Pair<Long, Float>>): Float {
        if (points.size < 2) return 0f
        val origin = points.first().first
        val xs = points.map { (timestamp, _) -> (timestamp - origin) / 60_000f }
        val ys = points.map { it.second }
        val meanX = xs.average().toFloat()
        val meanY = ys.average().toFloat()
        var numerator = 0f
        var denominator = 0f
        for (index in xs.indices) {
            val dx = xs[index] - meanX
            numerator += dx * (ys[index] - meanY)
            denominator += dx * dx
        }
        return if (denominator <= 0f) 0f else numerator / denominator
    }

    private fun accelerationPerMinuteSquared(points: List<Pair<Long, Float>>): Float {
        if (points.size < 4) return 0f
        val split = points.size / 2
        val first = points.subList(0, split + 1)
        val second = points.subList(split, points.size)
        val firstSlope = slopePerMinute(first)
        val secondSlope = slopePerMinute(second)
        val durationMinutes = (
            (points.last().first - points.first().first).coerceAtLeast(1L) / 60_000f
            ).coerceAtLeast(1f / 60f)
        val raw = (secondSlope - firstSlope) / durationMinutes
        return if (abs(raw) < policy.accelerationThresholdPerMinuteSquared) 0f else raw
    }

    private fun fractionMatching(
        values: List<Float>,
        predicate: (Float) -> Boolean
    ): Float {
        if (values.isEmpty()) return 0f
        return values.count(predicate).toFloat() / values.size
    }

    private fun unknown(
        signalMode: ThermalSignalMode,
        reason: String
    ): ThermalPrediction =
        ThermalPrediction(
            trend = ThermalTrend.UNKNOWN,
            risk = ThermalRisk.UNKNOWN,
            confidence = 0f,
            signalMode = signalMode,
            slopePerMinute = null,
            accelerationPerMinuteSquared = null,
            latestMeasuredHeadroom = null,
            projectedHeadroom = null,
            allowPreventiveSignal = false,
            recovering = false,
            evidence = emptyList(),
            reason = reason
        )

    private companion object {
        val RISING_TRENDS = setOf(ThermalTrend.RISING, ThermalTrend.RISING_FAST)
    }
}

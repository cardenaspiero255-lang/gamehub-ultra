package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.BatteryGamingAssessment
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalPrediction
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry

/**
 * Thin integration layer over existing GameHub telemetry and CAR-48/49 outputs.
 * CAR-50 analyzes and emits a typed signal; CAR-47 remains responsible for decisions.
 */
class FramePacingIntegration(
    private val engine: FramePacingIntelligenceEngine = FramePacingIntelligenceEngine()
) {
    fun assess(
        targetHz: Int?,
        sessionSnapshots: List<SessionCoachSnapshot>,
        frameTimingSamples: List<FrameTimingSample>?,
        thermal: ThermalPrediction?,
        battery: BatteryGamingAssessment?,
        refreshTelemetry: RefreshTelemetry,
        interpolationState: InterpolationState
    ): FramePacingAssessment =
        engine.analyze(
            targetHz = targetHz,
            refreshSamples =
                FramePacingTelemetryAdapter.fromSessionSnapshots(sessionSnapshots),
            frameTimingSamples = frameTimingSamples,
            thermal = thermal,
            battery = battery,
            supportedRefreshRatesHz =
                FramePacingTelemetryAdapter.supportedRefreshRates(refreshTelemetry),
            interpolationState = interpolationState
        )

    fun adaptiveSignal(
        assessment: FramePacingAssessment
    ): FramePacingAdaptiveSignal =
        engine.toAdaptiveSignal(assessment)

    fun shouldNotifyAdaptiveOptimizer(
        signal: FramePacingAdaptiveSignal
    ): Boolean =
        signal.shouldNotify
}

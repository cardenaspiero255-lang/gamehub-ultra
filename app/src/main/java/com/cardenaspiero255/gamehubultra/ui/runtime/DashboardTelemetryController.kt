package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.AiSessionCoach
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.AdaptiveRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineBuilder
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineSample
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

internal data class DashboardTelemetryUpdate(
    val diagnostics: RuntimeDiagnostics,
    val telemetryTrend: List<RuntimeDiagnostics>,
    val timelineSamples: List<PerformanceTimelineSample>,
    val adaptiveDecision: AdaptiveDecision,
    val coachSamples: List<SessionCoachSnapshot>,
    val coachObservations: List<SessionCoachMessage>,
    val lastCompletedCoachReport: SessionCoachPostSessionReport?
)

internal class DashboardTelemetryController(
    private val adaptiveEvaluator: (AdaptiveRuntimeSnapshot) -> AdaptiveDecision,
    private val latencyProbe: suspend (networkHandle: Long) -> Long?,
    private val recordPerformanceEvent: (PerformanceEvent) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var latencyMs: Long? = null
    private var lastLatencyCheckAt = 0L
    private var lastLatencyNetworkHandle: Long? = null
    private var lastThermalStatus: Int? = null
    private var initializedThermalStatus = false
    private var activeSessionId: String? = null
    private var telemetryTrend = emptyList<RuntimeDiagnostics>()
    private var timelineSamples = emptyList<PerformanceTimelineSample>()
    private var coachSamples = emptyList<SessionCoachSnapshot>()
    private var coachObservations = emptyList<SessionCoachMessage>()
    private var lastCompletedCoachReport: SessionCoachPostSessionReport? = null

    suspend fun sample(
        diagnostics: RuntimeDiagnostics,
        activeGamePackage: String?,
        sessionId: String?,
        sustainedPerformanceSupported: Boolean,
        performanceHintsAvailable: Boolean
    ): DashboardTelemetryUpdate {
        val now = nowMillis()

        if (sessionId != activeSessionId) {
            if (activeSessionId != null && coachSamples.isNotEmpty()) {
                lastCompletedCoachReport = AiSessionCoach.postSession(coachSamples)
            }
            activeSessionId = sessionId
            timelineSamples = emptyList()
            coachSamples = emptyList()
            coachObservations = emptyList()
            lastThermalStatus = null
            initializedThermalStatus = false
        }

        val network = diagnostics.connectivity

        if (
            !network.connected ||
            !network.validated ||
            network.networkHandle == null ||
            network.metered
        ) {
            latencyMs = null
            lastLatencyNetworkHandle = null
            lastLatencyCheckAt = 0L
        } else if (
            network.networkHandle != lastLatencyNetworkHandle ||
            now - lastLatencyCheckAt >= LATENCY_RECHECK_INTERVAL_MILLIS
        ) {
            latencyMs = latencyProbe(network.networkHandle)
            lastLatencyNetworkHandle = network.networkHandle
            lastLatencyCheckAt = now
        }

        val enrichedDiagnostics = diagnostics.copy(
            connectivity = network.copy(latencyMs = latencyMs)
        )
        telemetryTrend = (telemetryTrend + enrichedDiagnostics)
            .takeLast(MAX_TELEMETRY_TREND_SAMPLES)

        if (sessionId != null) {
            timelineSamples = (
                timelineSamples + PerformanceTimelineBuilder.sample(
                    timestampMillis = now,
                    batteryPercent = enrichedDiagnostics.battery.percent,
                    thermalStatus = enrichedDiagnostics.thermal.status,
                    thermalHeadroom = enrichedDiagnostics.thermal.headroom,
                    refreshRateHz = enrichedDiagnostics.refresh.currentRefreshRateHz,
                    ramUsedPercent = enrichedDiagnostics.memory.usedPercent
                )
            ).takeLast(MAX_TIMELINE_SAMPLES)

            val coachSnapshot = SessionCoachSnapshot(
                timestampMillis = now,
                batteryPercent = enrichedDiagnostics.battery.percent,
                thermalStatus = enrichedDiagnostics.thermal.status,
                thermalHeadroom = enrichedDiagnostics.thermal.headroom,
                refreshRateHz = enrichedDiagnostics.refresh.currentRefreshRateHz,
                latencyMs = enrichedDiagnostics.connectivity.latencyMs,
                memoryUsedPercent = enrichedDiagnostics.memory.usedPercent,
                batteryCharging = enrichedDiagnostics.battery.charging,
                powerSaveMode = enrichedDiagnostics.battery.powerSaveMode
            )
            coachSamples.lastOrNull()?.let { previous ->
                coachObservations = (
                    coachObservations + AiSessionCoach.midSession(previous, coachSnapshot)
                ).takeLast(MAX_COACH_OBSERVATIONS)
            }
            coachSamples = (coachSamples + coachSnapshot).takeLast(MAX_COACH_SAMPLES)

            if (
                initializedThermalStatus &&
                diagnostics.thermal.status != lastThermalStatus
            ) {
                recordPerformanceEvent(
                    PerformanceEvent(
                        timestampMillis = now,
                        type = PerformanceEventType.THERMAL_CHANGED,
                        sessionId = sessionId,
                        detail = diagnostics.thermal.status?.toString() ?: "unavailable"
                    )
                )
            }
        } else {
            timelineSamples = emptyList()
            coachSamples = emptyList()
            coachObservations = emptyList()
        }

        lastThermalStatus = diagnostics.thermal.status
        initializedThermalStatus = true

        val decision = adaptiveEvaluator(
            AdaptiveRuntimeSnapshot(
                thermalStatus = diagnostics.thermal.status,
                thermalHeadroom = diagnostics.thermal.headroom,
                batteryPercent = diagnostics.battery.percent,
                charging = diagnostics.battery.charging,
                powerSaveMode = diagnostics.battery.powerSaveMode,
                sessionActive = activeGamePackage != null,
                sustainedPerformanceSupported = sustainedPerformanceSupported,
                performanceHintsAvailable = performanceHintsAvailable
            )
        )

        if (decision.changed && sessionId != null) {
            recordPerformanceEvent(
                PerformanceEvent(
                    timestampMillis = now,
                    type = PerformanceEventType.POLICY_CHANGED,
                    sessionId = sessionId,
                    profile = decision.profile,
                    score = decision.score,
                    detail = decision.reason
                )
            )
        }

        return DashboardTelemetryUpdate(
            diagnostics = enrichedDiagnostics,
            telemetryTrend = telemetryTrend,
            timelineSamples = timelineSamples,
            adaptiveDecision = decision,
            coachSamples = coachSamples,
            coachObservations = coachObservations,
            lastCompletedCoachReport = lastCompletedCoachReport
        )
    }

    private companion object {
        const val LATENCY_RECHECK_INTERVAL_MILLIS = 30_000L
        const val MAX_TELEMETRY_TREND_SAMPLES = 12
        const val MAX_TIMELINE_SAMPLES = 24
        const val MAX_COACH_SAMPLES = 180
        const val MAX_COACH_OBSERVATIONS = 12
    }
}

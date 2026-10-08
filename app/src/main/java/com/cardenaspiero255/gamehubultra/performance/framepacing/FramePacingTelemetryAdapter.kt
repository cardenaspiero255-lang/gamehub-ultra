package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry

/**
 * Adapta telemetría existente de GameHub Ultra. No vuelve a leer DisplayManager ni inventa FPS.
 */
class FramePacingTelemetryAdapter(
    private val policy: FramePacingPolicy = FramePacingPolicy()
) {
    fun fromSessionSnapshot(snapshot: SessionCoachSnapshot): List<RefreshSample> {
        if (snapshot.timestampMillis < 0L) return emptyList()
        val hz = snapshot.refreshRateHz
            ?.takeIf(::isValidRefresh)
            ?: return emptyList()
        return listOf(
            RefreshSample(
                timestampMs = snapshot.timestampMillis,
                refreshHz = hz
            )
        )
    }

    fun fromRefreshTelemetry(
        telemetry: RefreshTelemetry,
        timestampMs: Long
    ): List<RefreshSample> {
        require(timestampMs >= 0L) { "timestampMs debe ser >= 0." }
        val hz = telemetry.currentRefreshRateHz
            ?.takeIf(::isValidRefresh)
            ?: return emptyList()
        return listOf(
            RefreshSample(
                timestampMs = timestampMs,
                refreshHz = hz
            )
        )
    }

    fun aggregateSnapshots(
        snapshots: List<SessionCoachSnapshot>
    ): List<RefreshSample> =
        snapshots
            .asSequence()
            .filter { it.timestampMillis >= 0L }
            .mapNotNull { snapshot ->
                snapshot.refreshRateHz
                    ?.takeIf(::isValidRefresh)
                    ?.let { hz ->
                        RefreshSample(
                            timestampMs = snapshot.timestampMillis,
                            refreshHz = hz
                        )
                    }
            }
            .sortedBy(RefreshSample::timestampMs)
            .toList()

    // Alias explícito para compatibilidad con el primer corte recuperado de CAR-50.
    fun fromSessionSnapshots(
        snapshots: List<SessionCoachSnapshot>
    ): List<RefreshSample> = aggregateSnapshots(snapshots)

    fun getSupportedFromTelemetry(
        telemetry: RefreshTelemetry
    ): List<Int> =
        telemetry.supportedRefreshRatesHz
            .asSequence()
            .filter { hz -> hz.toFloat() in policy.refreshMinHz..policy.refreshMaxHz }
            .distinct()
            .sorted()
            .toList()

    fun fromFrameTimings(
        frameTimesMs: List<Float>,
        timestamps: List<Long>
    ): List<FrameTimingSample> {
        require(frameTimesMs.size == timestamps.size) {
            "frameTimesMs y timestamps deben tener el mismo tamaño."
        }
        return frameTimesMs
            .zip(timestamps) { frameTime, timestamp ->
                FrameTimingSample(
                    timestampMs = timestamp,
                    frameTimeMs = frameTime
                )
            }
            .filter(::isValidFrameTiming)
    }

    private fun isValidRefresh(hz: Float): Boolean =
        hz.isFinite() && hz in policy.refreshMinHz..policy.refreshMaxHz

    private fun isValidFrameTiming(sample: FrameTimingSample): Boolean =
        sample.timestampMs >= 0L &&
            sample.frameTimeMs.isFinite() &&
            sample.frameTimeMs > 0f &&
            sample.frameTimeMs < policy.frameTimeMaxMs
}

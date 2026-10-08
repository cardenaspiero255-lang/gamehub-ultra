package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry

/**
 * Adapter only transforms telemetry GameHub already owns.
 * It does not read DisplayManager, Choreographer, or external-game FPS itself.
 */
object FramePacingTelemetryAdapter {
    fun fromSessionSnapshot(snapshot: SessionCoachSnapshot): RefreshSample? =
        snapshot.refreshRateHz
            ?.takeIf {
                it.isFinite() &&
                    it > 0f &&
                    snapshot.timestampMillis >= 0L
            }
            ?.let {
                RefreshSample(
                    timestampMs = snapshot.timestampMillis,
                    refreshHz = it
                )
            }

    fun fromSessionSnapshots(
        snapshots: List<SessionCoachSnapshot>
    ): List<RefreshSample> =
        snapshots
            .mapNotNull(::fromSessionSnapshot)
            .sortedBy(RefreshSample::timestampMs)

    fun fromRefreshTelemetry(
        telemetry: RefreshTelemetry,
        timestampMs: Long
    ): RefreshSample? =
        telemetry.currentRefreshRateHz
            ?.takeIf { it.isFinite() && it > 0f && timestampMs >= 0L }
            ?.let { RefreshSample(timestampMs, it) }

    fun supportedRefreshRates(
        telemetry: RefreshTelemetry
    ): List<Int> =
        telemetry.supportedRefreshRatesHz
            .filter { it > 0 }
            .distinct()
            .sorted()

    fun fromFrameTimings(
        frameTimesMs: List<Float>,
        timestampsMs: List<Long>
    ): List<FrameTimingSample> =
        frameTimesMs.zip(timestampsMs)
            .mapNotNull { (frameTime, timestamp) ->
                frameTime
                    .takeIf {
                        it.isFinite() &&
                            it > 0f &&
                            timestamp >= 0L
                    }
                    ?.let { FrameTimingSample(timestamp, it) }
            }
            .sortedBy(FrameTimingSample::timestampMs)
}

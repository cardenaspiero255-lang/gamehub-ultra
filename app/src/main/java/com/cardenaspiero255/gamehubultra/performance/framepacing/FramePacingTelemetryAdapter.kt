package com.cardenaspiero255.gamehubultra.performance.framepacing

import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry

/**
 * Reuses existing GameHub telemetry instead of reading DisplayManager a second time.
 */
object FramePacingTelemetryAdapter {
    fun fromRefreshTelemetry(
        telemetry: RefreshTelemetry,
        timestampMs: Long
    ): RefreshSample? =
        telemetry.currentRefreshRateHz
            ?.takeIf { it.isFinite() && it > 0f && timestampMs >= 0L }
            ?.let { RefreshSample(timestampMs = timestampMs, refreshHz = it) }

    fun fromSessionSnapshots(
        snapshots: List<SessionCoachSnapshot>
    ): List<RefreshSample> =
        snapshots
            .asSequence()
            .mapNotNull { snapshot ->
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
            }
            .sortedBy { it.timestampMs }
            .toList()
}

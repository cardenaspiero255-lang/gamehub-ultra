package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticSample
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticsAdapter
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticsEngine
import com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import kotlinx.coroutines.runBlocking

/**
 * Existing voice/UI network entry point, now backed by the CAR-51 diagnostics
 * engine. Only probes that really ran are marked as measured.
 */
object VoiceNetworkSnapshotFactory {
    private const val MAX_HISTORY = 6
    private val adapter = NetworkGamingDiagnosticsAdapter()
    private val engine = NetworkGamingDiagnosticsEngine()
    private val historyLock = Any()
    private var history: List<NetworkGamingDiagnosticSample> = emptyList()

    /**
     * Production voice callers invoke this on an existing background worker.
     * Do not reuse old latency samples after a disconnect or network change.
     */
    fun current(context: Context): VoiceNetworkSnapshot? {
        val telemetry = RuntimeDiagnosticsProvider.get(context).connectivity
        val networkHandle = telemetry.networkHandle

        if (!telemetry.connected || !telemetry.validated || networkHandle == null) {
            clearHistory()
            return fromMeasurements(
                listOf(
                    adapter.fromTelemetry(
                        telemetry = telemetry,
                        timestampMs = System.currentTimeMillis(),
                        latencyMeasured = false
                    )
                )
            )
        }

        val measuredLatency = runBlocking {
            ConnectivityLatencyProbe.measure(
                context = context,
                expectedNetworkHandle = networkHandle
            )
        }
        val sample = adapter.fromTelemetry(
            telemetry = telemetry.copy(latencyMs = measuredLatency),
            timestampMs = System.currentTimeMillis(),
            latencyMeasured = measuredLatency != null
        )

        val samples = synchronized(historyLock) {
            // Retain previous transport/handle metadata to detect handoffs.
            // The engine uses latency only from the newest network handle.
            history = appendHistory(history, sample)
            history
        }
        return fromMeasurements(samples)
    }

    /**
     * Legacy/test entry point. A numeric latency alone is NOT proof that a
     * validated latency probe ran: callers must opt in to measurement provenance.
     */
    fun from(
        samples: List<ConnectivityTelemetry>,
        latencyMeasured: Boolean = false
    ): VoiceNetworkSnapshot? =
        fromMeasurements(
            samples.mapIndexed { index, telemetry ->
                adapter.fromTelemetry(
                    telemetry = telemetry,
                    timestampMs = index.toLong() * 1_000L,
                    latencyMeasured = latencyMeasured
                )
            }
        )

    internal fun fromMeasurements(
        samples: List<NetworkGamingDiagnosticSample>
    ): VoiceNetworkSnapshot? {
        val latest = samples.maxByOrNull(NetworkGamingDiagnosticSample::timestampMs)
            ?: return null
        val diagnostic = engine.analyze(samples)
        return VoiceNetworkSnapshot(
            metrics = diagnostic.metrics,
            recommendedProfile = diagnostic.recommendedProfile,
            metered = latest.metered,
            diagnostics = diagnostic
        )
    }

    internal fun appendHistory(
        previous: List<NetworkGamingDiagnosticSample>,
        sample: NetworkGamingDiagnosticSample
    ): List<NetworkGamingDiagnosticSample> =
        (previous + sample).takeLast(MAX_HISTORY)

    private fun clearHistory() = synchronized(historyLock) {
        history = emptyList()
    }
}

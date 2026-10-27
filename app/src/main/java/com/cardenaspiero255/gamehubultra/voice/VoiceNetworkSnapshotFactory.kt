package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticSample
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticsAdapter
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticsEngine
import com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Existing voice/UI network entry point, now backed by the CAR-51 diagnostics
 * engine. Only probes that really ran are marked as measured.
 */
object VoiceNetworkSnapshotFactory {
    private const val MAX_HISTORY = 6
    private val adapter = NetworkGamingDiagnosticsAdapter()
    private val engine = NetworkGamingDiagnosticsEngine()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val asyncSession = VoiceNetworkAsyncProbeCoordinator()
    
    /**
     * Never block the caller on an HTTP probe. The first status may be
     * UNMEASURED; a later request uses genuine completed measurements.
     * This preserves the foreground voice/chat responsiveness on OEM devices.
     */
    fun current(context: Context): VoiceNetworkSnapshot? {
        val appContext = context.applicationContext
        return asyncSession.capture(
            telemetry = RuntimeDiagnosticsProvider.connectivity(appContext),
            timestampMs = System.currentTimeMillis()
        ) { handle, completed ->
            probeScope.launch {
                val measuredLatency = try {
                    ConnectivityLatencyProbe.measure(
                        context = appContext,
                        expectedNetworkHandle = handle
                    )
                } catch (_: Exception) {
                    null
                }
                completed(
                    measuredLatency,
                    RuntimeDiagnosticsProvider.connectivity(appContext),
                    System.currentTimeMillis()
                )
            }
        }
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

}

/**
 * Tracks observations for one voice diagnostics session.
 *
 * The probe runs outside the lock, but connectivity is re-read under the lock
 * immediately before committing a sample. Concurrent or delayed probes therefore
 * cannot reintroduce an old network as the current connection.
 */
internal class VoiceNetworkSnapshotSession(
    private val adapter: NetworkGamingDiagnosticsAdapter =
        NetworkGamingDiagnosticsAdapter()
) {
    private val historyLock = Any()
    private var history: List<NetworkGamingDiagnosticSample> = emptyList()

    fun capture(
        readConnectivity: () -> ConnectivityTelemetry,
        measureLatency: (Long) -> Long?,
        timestampMs: () -> Long
    ): VoiceNetworkSnapshot? {
        val beforeProbe = readConnectivity()
        val originalHandle = beforeProbe.networkHandle
        val latency = if (
            beforeProbe.connected && beforeProbe.validated &&
            originalHandle != null
        ) {
            measureLatency(originalHandle)
        } else {
            null
        }

        val samples = synchronized(historyLock) {
            // A handoff can occur during the probe, or another capture may
            // complete first. Observe the live connection before appending.
            val current = readConnectivity()
            val currentValid = current.connected &&
                current.validated && current.networkHandle != null
            val probeMatchesCurrentNetwork = currentValid &&
                beforeProbe.connected && beforeProbe.validated &&
                originalHandle != null &&
                current.networkHandle == originalHandle &&
                current.transport == beforeProbe.transport

            val measuredLatency = latency.takeIf { probeMatchesCurrentNetwork }
            val sample = adapter.fromTelemetry(
                telemetry = current.copy(latencyMs = measuredLatency),
                timestampMs = timestampMs(),
                latencyMeasured = measuredLatency != null
            )

            if (!currentValid) {
                history = emptyList()
                listOf(sample)
            } else {
                history = VoiceNetworkSnapshotFactory.appendHistory(history, sample)
                history
            }
        }
        return VoiceNetworkSnapshotFactory.fromMeasurements(samples)
    }
}

package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticSample
import com.cardenaspiero255.gamehubultra.network.diagnostics.NetworkGamingDiagnosticsAdapter
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry

/**
 * Serializes session identity and asynchronous ping completion. No I/O occurs
 * inside capture(). A probe callback is accepted only for the current,
 * independently revalidated network; old Wi-Fi probes cannot become cellular
 * telemetry and missing measurements never masquerade as packet-loss data.
 */
internal class VoiceNetworkAsyncProbeCoordinator(
    private val adapter: NetworkGamingDiagnosticsAdapter = NetworkGamingDiagnosticsAdapter(),
    private val minProbeIntervalMs: Long = 3_000L
) {
    init {
        require(minProbeIntervalMs >= 0L)
    }

    private val lock = Any()
    private var activeNetworkHandle: Long? = null
    private var activeTransport: String? = null
    private var inFlightNetworkHandle: Long? = null
    private var lastProbeStartedAtMs = Long.MIN_VALUE
    private var history: List<NetworkGamingDiagnosticSample> = emptyList()

    fun capture(
        telemetry: ConnectivityTelemetry,
        timestampMs: Long,
        scheduleProbe: (Long, (Long?, ConnectivityTelemetry, Long) -> Unit) -> Unit
    ): VoiceNetworkSnapshot? {
        require(timestampMs >= 0L)
        var scheduledHandle: Long? = null
        val samples = synchronized(lock) {
            val currentHandle = telemetry.networkHandle
            val connected = telemetry.connected && telemetry.validated && currentHandle != null
            if (!connected) {
                activeNetworkHandle = null
                activeTransport = null
                inFlightNetworkHandle = null
                lastProbeStartedAtMs = Long.MIN_VALUE
                history = emptyList()
            } else if (
                activeNetworkHandle != currentHandle ||
                activeTransport != telemetry.transport
            ) {
                // Keep old observations for truthful transport-change counts;
                // the analyzer isolates measured latency by the latest handle.
                activeNetworkHandle = currentHandle
                activeTransport = telemetry.transport
                inFlightNetworkHandle = null
                lastProbeStartedAtMs = Long.MIN_VALUE
            }

            val observation = adapter.fromTelemetry(
                telemetry = telemetry.copy(latencyMs = null),
                timestampMs = timestampMs,
                latencyMeasured = false
            )
            history = VoiceNetworkSnapshotFactory.appendHistory(history, observation)

            val elapsed = if (lastProbeStartedAtMs == Long.MIN_VALUE) {
                Long.MAX_VALUE
            } else timestampMs - lastProbeStartedAtMs
            if (connected && currentHandle != null &&
                inFlightNetworkHandle != currentHandle &&
                elapsed >= minProbeIntervalMs
            ) {
                inFlightNetworkHandle = currentHandle
                lastProbeStartedAtMs = timestampMs
                scheduledHandle = currentHandle
            }
            history.toList()
        }
        scheduledHandle?.let { handle ->
            try {
                scheduleProbe(handle) { measured, rechecked, completedAt ->
                    finishProbe(handle, measured, rechecked, completedAt)
                }
            } catch (_: Exception) {
                synchronized(lock) {
                    if (inFlightNetworkHandle == handle) inFlightNetworkHandle = null
                }
            }
        }
        return VoiceNetworkSnapshotFactory.fromMeasurements(samples)
    }

    private fun finishProbe(
        handle: Long,
        latency: Long?,
        rechecked: ConnectivityTelemetry,
        timestampMs: Long
    ) {
        synchronized(lock) {
            if (inFlightNetworkHandle != handle) return@synchronized
            inFlightNetworkHandle = null
            if (
                activeNetworkHandle != handle ||
                rechecked.networkHandle != handle ||
                !rechecked.connected || !rechecked.validated ||
                rechecked.transport != activeTransport ||
                timestampMs < 0L
            ) return@synchronized

            val measured = adapter.fromTelemetry(
                telemetry = rechecked.copy(latencyMs = latency),
                timestampMs = timestampMs,
                latencyMeasured = latency != null
            )
            history = VoiceNetworkSnapshotFactory.appendHistory(history, measured)
        }
    }
}

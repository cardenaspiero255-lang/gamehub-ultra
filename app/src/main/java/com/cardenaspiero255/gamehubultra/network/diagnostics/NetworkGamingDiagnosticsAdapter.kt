package com.cardenaspiero255.gamehubultra.network.diagnostics

import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry

/**
 * Adapta ConnectivityTelemetry sin convertir telemetría observada en una medición
 * que no ocurrió. La procedencia de latencia/pérdida debe declararse explícitamente.
 */
class NetworkGamingDiagnosticsAdapter(
    private val policy: NetworkGamingDiagnosticsPolicy =
        NetworkGamingDiagnosticsPolicy()
) {
    fun fromTelemetry(
        telemetry: ConnectivityTelemetry,
        timestampMs: Long,
        latencyMeasured: Boolean,
        packetLossPercent: Double? = null,
        packetLossMeasured: Boolean = false
    ): NetworkGamingDiagnosticSample {
        require(timestampMs >= 0L) { "timestampMs debe ser >= 0." }

        val measuredLatency = telemetry.latencyMs
            ?.takeIf {
                latencyMeasured &&
                    it > 0L &&
                    it <= policy.maxLatencyMs
            }
        val measuredLoss = packetLossPercent
            ?.takeIf {
                packetLossMeasured &&
                    it.isFinite() &&
                    it in 0.0..100.0
            }

        return NetworkGamingDiagnosticSample(
            timestampMs = timestampMs,
            networkHandle = telemetry.networkHandle,
            transport = telemetry.transport,
            connected = telemetry.connected,
            validated = telemetry.validated,
            metered = telemetry.metered,
            latencyMs = measuredLatency,
            latencyMeasured = measuredLatency != null,
            packetLossPercent = measuredLoss,
            packetLossMeasured = measuredLoss != null
        )
    }
}

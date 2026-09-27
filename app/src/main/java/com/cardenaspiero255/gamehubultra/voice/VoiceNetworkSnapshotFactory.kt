package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetricsCalculator
import com.cardenaspiero255.gamehubultra.network.NetworkProfilePolicy
import com.cardenaspiero255.gamehubultra.network.NetworkSample
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider

object VoiceNetworkSnapshotFactory {
    fun current(context: Context): VoiceNetworkSnapshot? =
        from(listOf(RuntimeDiagnosticsProvider.get(context).connectivity))

    fun from(samples: List<ConnectivityTelemetry>): VoiceNetworkSnapshot? {
        val latest = samples.lastOrNull() ?: return null
        val connected = samples.filter { it.connected && it.validated }
        if (connected.isEmpty()) return null
        val metrics = NetworkMetricsCalculator.calculate(
            connected.map {
                NetworkSample(
                    latencyMs = it.latencyMs,
                    transport = it.transport,
                    connected = it.connected,
                    validated = it.validated,
                    metered = it.metered
                )
            }
        )
        val normalizedTransport = latest.transport
            ?.lowercase()
            ?.replace('‑', '-')
            ?.replace('–', '-')
            ?.replace('—', '-')
        val isWifi = normalizedTransport == "wi-fi" || normalizedTransport == "wifi"
        val profile = when {
            latest.metered -> NetworkGameProfile.DATA_SAVER
            metrics.stability == NetworkStability.UNMEASURED && isWifi ->
                NetworkGameProfile.COMPETITIVE
            else -> NetworkProfilePolicy.recommend(metrics, latest.metered)
        }
        return VoiceNetworkSnapshot(metrics, profile, latest.metered)
    }
}

package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkMetricsCalculator
import com.cardenaspiero255.gamehubultra.network.NetworkProfilePolicy
import com.cardenaspiero255.gamehubultra.network.NetworkSample
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry

object VoiceNetworkSnapshotFactory {
    fun from(samples: List<ConnectivityTelemetry>): VoiceNetworkSnapshot? {
        val latest = samples.lastOrNull() ?: return null
        val measured = samples.filter { telemetry ->
            telemetry.connected &&
                telemetry.validated &&
                telemetry.latencyMs != null &&
                telemetry.latencyMs > 0L
        }
        if (measured.isEmpty()) return null

        val metrics = NetworkMetricsCalculator.calculate(
            measured.map { telemetry ->
                NetworkSample(
                    latencyMs = telemetry.latencyMs,
                    packetLossPercent = null,
                    transport = telemetry.transport,
                    connected = telemetry.connected,
                    validated = telemetry.validated,
                    metered = telemetry.metered
                )
            }
        )
        val metered = latest.metered
        return VoiceNetworkSnapshot(
            metrics = metrics,
            recommendedProfile = NetworkProfilePolicy.recommend(
                metrics = metrics,
                metered = metered
            ),
            metered = metered
        )
    }
}

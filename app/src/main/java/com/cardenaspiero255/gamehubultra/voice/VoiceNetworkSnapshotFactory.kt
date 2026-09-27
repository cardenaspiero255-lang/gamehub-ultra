package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.network.NetworkMetricsCalculator
import com.cardenaspiero255.gamehubultra.network.NetworkProfilePolicy
import com.cardenaspiero255.gamehubultra.network.NetworkSample
import com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import kotlinx.coroutines.runBlocking

object VoiceNetworkSnapshotFactory {
    private const val MAX_HISTORY = 6
    private val historyLock = Any()
    private var historyNetworkHandle: Long? = null
    private var history: List<ConnectivityTelemetry> = emptyList()

    /**
     * Production voice callers invoke this on their existing background worker.
     * A real validated-network latency probe is added to a short per-network
     * history before metrics are calculated.
     */
    fun current(context: Context): VoiceNetworkSnapshot? {
        val telemetry = RuntimeDiagnosticsProvider.get(context).connectivity
        val networkHandle = telemetry.networkHandle

        if (
            !telemetry.connected ||
            !telemetry.validated ||
            networkHandle == null
        ) {
            clearHistory()
            return from(listOf(telemetry))
        }

        val measuredLatency = runBlocking {
            ConnectivityLatencyProbe.measure(
                context = context,
                expectedNetworkHandle = networkHandle
            )
        }
        val measured = telemetry.copy(latencyMs = measuredLatency)

        val samples = synchronized(historyLock) {
            if (historyNetworkHandle != networkHandle) {
                historyNetworkHandle = networkHandle
                history = emptyList()
            }
            history = (history + measured).takeLast(MAX_HISTORY)
            history
        }
        return from(samples)
    }

    fun from(samples: List<ConnectivityTelemetry>): VoiceNetworkSnapshot? {
        val latest = samples.lastOrNull() ?: return null
        val connected = samples.filter { it.connected && it.validated }
        if (connected.isEmpty()) return null

        val metrics = NetworkMetricsCalculator.calculate(
            connected.map {
                NetworkSample(
                    latencyMs = it.latencyMs,
                    packetLossPercent = null,
                    transport = it.transport,
                    connected = it.connected,
                    validated = it.validated,
                    metered = it.metered
                )
            }
        )
        return VoiceNetworkSnapshot(
            metrics = metrics,
            recommendedProfile = NetworkProfilePolicy.recommend(
                metrics = metrics,
                metered = latest.metered
            ),
            metered = latest.metered
        )
    }

    private fun clearHistory() = synchronized(historyLock) {
        historyNetworkHandle = null
        history = emptyList()
    }
}

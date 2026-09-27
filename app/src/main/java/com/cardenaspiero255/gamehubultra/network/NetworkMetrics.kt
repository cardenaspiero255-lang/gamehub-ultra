package com.cardenaspiero255.gamehubultra.network

import kotlin.math.max

enum class NetworkStability { OFFLINE, UNMEASURED, EXCELLENT, GOOD, FAIR, POOR }
enum class NetworkGameProfile { COMPETITIVE, BALANCED, DATA_SAVER }

data class NetworkSample(
    val latencyMs: Long?,
    val packetLossPercent: Double? = null,
    val transport: String? = null,
    val connected: Boolean = true,
    val validated: Boolean = true,
    val metered: Boolean = false
)

data class NetworkMetrics(
    val averageLatencyMs: Double?,
    val jitterMs: Double?,
    val packetLossPercent: Double?,
    val spikeCount: Int,
    val stability: NetworkStability
)

object NetworkMetricsCalculator {
    fun calculate(samples: List<NetworkSample>): NetworkMetrics {
        val connected = samples.filter { it.connected && it.validated }
        if (connected.isEmpty()) return NetworkMetrics(null, null, null, 0, NetworkStability.OFFLINE)
        val usable = connected.filter { it.latencyMs != null && it.latencyMs > 0L }
        if (usable.isEmpty()) return NetworkMetrics(null, null, null, 0, NetworkStability.UNMEASURED)

        val latencies = usable.map { it.latencyMs!!.toDouble() }
        val average = latencies.average()
        val jitter = latencies.zipWithNext { a, b -> kotlin.math.abs(b - a) }
            .takeIf { it.isNotEmpty() }?.average()
        val losses = usable.mapNotNull { it.packetLossPercent }
            .filter { it.isFinite() && it in 0.0..100.0 }
        val loss = losses.takeIf { it.isNotEmpty() }?.average()
        val baseline = median(latencies)
        val spikeThreshold = baseline + max(50.0, baseline * 0.5)
        val spikes = latencies.count { it > spikeThreshold }

        return NetworkMetrics(
            averageLatencyMs = average,
            jitterMs = jitter,
            packetLossPercent = loss,
            spikeCount = spikes,
            stability = classify(average, jitter, loss, spikes, latencies.size)
        )
    }

    private fun classify(
        average: Double,
        jitter: Double?,
        loss: Double?,
        spikes: Int,
        sampleCount: Int
    ): NetworkStability {
        val j = jitter ?: 0.0
        val l = loss ?: 0.0
        val spikeRatio = if (sampleCount == 0) 0.0 else spikes.toDouble() / sampleCount
        return when {
            l >= 5.0 || j >= 40.0 || average >= 180.0 || spikes >= 2 || spikeRatio >= 0.5 ->
                NetworkStability.POOR
            l >= 2.0 || j >= 20.0 || average >= 120.0 || spikes >= 1 ->
                NetworkStability.FAIR
            l >= 0.5 || j >= 10.0 || average >= 80.0 -> NetworkStability.GOOD
            else -> NetworkStability.EXCELLENT
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else sorted[middle]
    }
}

object NetworkProfilePolicy {
    fun recommend(metrics: NetworkMetrics, metered: Boolean): NetworkGameProfile {
        if (metered) return NetworkGameProfile.DATA_SAVER
        return when (metrics.stability) {
            NetworkStability.EXCELLENT, NetworkStability.GOOD -> {
                val latency = metrics.averageLatencyMs
                val loss = metrics.packetLossPercent ?: 0.0
                if (latency != null && latency <= 80.0 && loss <= 1.0) {
                    NetworkGameProfile.COMPETITIVE
                } else NetworkGameProfile.BALANCED
            }
            else -> NetworkGameProfile.BALANCED
        }
    }
}

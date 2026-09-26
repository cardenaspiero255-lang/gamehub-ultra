package com.cardenaspiero255.gamehubultra.network

import kotlin.math.max

enum class NetworkStability {
    OFFLINE,
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

enum class NetworkGameProfile {
    COMPETITIVE,
    BALANCED,
    DATA_SAVER
}

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
        val usable = samples.filter {
            it.connected && it.validated && it.latencyMs != null && it.latencyMs > 0L
        }

        if (usable.isEmpty()) {
            return NetworkMetrics(null, null, null, 0, NetworkStability.OFFLINE)
        }

        val latencies = usable.map { it.latencyMs!!.toDouble() }
        val average = latencies.average()
        val jitter = latencies
            .zipWithNext { previous, current -> kotlin.math.abs(current - previous) }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val measuredLoss = usable
            .mapNotNull { it.packetLossPercent }
            .filter { it.isFinite() && it in 0.0..100.0 }
        val packetLoss = measuredLoss.takeIf { it.isNotEmpty() }?.average()

        val baseline = median(latencies)
        val spikeThreshold = baseline + max(50.0, baseline * 0.5)
        val spikeCount = latencies.count { it > spikeThreshold }

        return NetworkMetrics(
            averageLatencyMs = average,
            jitterMs = jitter,
            packetLossPercent = packetLoss,
            spikeCount = spikeCount,
            stability = classify(average, jitter, packetLoss, spikeCount, latencies.size)
        )
    }

    private fun classify(
        averageLatencyMs: Double,
        jitterMs: Double?,
        packetLossPercent: Double?,
        spikeCount: Int,
        sampleCount: Int
    ): NetworkStability {
        val jitter = jitterMs ?: 0.0
        val loss = packetLossPercent ?: 0.0
        val spikeRatio = if (sampleCount == 0) 0.0 else spikeCount.toDouble() / sampleCount

        return when {
            loss >= 5.0 || jitter >= 40.0 || averageLatencyMs >= 180.0 ||
                spikeCount >= 2 || spikeRatio >= 0.5 -> NetworkStability.POOR
            loss >= 2.0 || jitter >= 20.0 || averageLatencyMs >= 120.0 ||
                spikeCount >= 1 -> NetworkStability.FAIR
            loss >= 0.5 || jitter >= 10.0 || averageLatencyMs >= 80.0 ->
                NetworkStability.GOOD
            else -> NetworkStability.EXCELLENT
        }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}

object NetworkProfilePolicy {
    fun recommend(metrics: NetworkMetrics, metered: Boolean): NetworkGameProfile {
        if (metered) return NetworkGameProfile.DATA_SAVER

        return when (metrics.stability) {
            NetworkStability.EXCELLENT,
            NetworkStability.GOOD -> {
                val latency = metrics.averageLatencyMs
                val loss = metrics.packetLossPercent ?: 0.0
                if (latency != null && latency <= 80.0 && loss <= 1.0) {
                    NetworkGameProfile.COMPETITIVE
                } else {
                    NetworkGameProfile.BALANCED
                }
            }
            NetworkStability.FAIR,
            NetworkStability.POOR,
            NetworkStability.OFFLINE -> NetworkGameProfile.BALANCED
        }
    }
}

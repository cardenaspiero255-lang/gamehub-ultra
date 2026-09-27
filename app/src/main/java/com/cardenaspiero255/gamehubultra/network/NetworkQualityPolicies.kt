package com.cardenaspiero255.gamehubultra.network

data class DnsProbeResult(
    val resolver: String,
    val latenciesMs: List<Long>,
    val attempts: Int
)

object DnsBenchmarkPolicy {
    fun selectBest(results: List<DnsProbeResult>): DnsProbeResult? =
        results
            .filter(::isReliable)
            .minWithOrNull(compareBy<DnsProbeResult>({ score(it) }, { it.resolver }))

    private fun isReliable(result: DnsProbeResult): Boolean {
        if (result.attempts <= 0) return false
        val valid = result.latenciesMs.filter { it > 0L }
        if (valid.size < 2) return false
        return valid.size.toDouble() / result.attempts.toDouble() >= 0.75
    }

    private fun score(result: DnsProbeResult): Double {
        val values = result.latenciesMs.filter { it > 0L }.map(Long::toDouble)
        if (values.isEmpty()) return Double.POSITIVE_INFINITY

        val median = median(values)
        val jitter = values
            .zipWithNext { previous, current -> kotlin.math.abs(current - previous) }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?: 0.0

        return median + jitter
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

enum class BufferbloatRating {
    UNKNOWN,
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

object BufferbloatCalculator {
    fun classify(idleLatencyMs: Long?, loadedLatencyMs: Long?): BufferbloatRating {
        if (
            idleLatencyMs == null ||
            loadedLatencyMs == null ||
            idleLatencyMs <= 0L ||
            loadedLatencyMs <= 0L
        ) {
            return BufferbloatRating.UNKNOWN
        }

        val increase = (loadedLatencyMs - idleLatencyMs).coerceAtLeast(0L)
        return when {
            increase <= 15L -> BufferbloatRating.EXCELLENT
            increase <= 40L -> BufferbloatRating.GOOD
            increase <= 80L -> BufferbloatRating.FAIR
            else -> BufferbloatRating.POOR
        }
    }
}

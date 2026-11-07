package com.cardenaspiero255.gamehubultra.domain

/**
 * CAR-62. These short workloads measure only an in-app synthetic CPU scenario.
 * They never measure game FPS, GPU throughput, network latency or real game performance.
 */
internal enum class SyntheticBenchmarkScenario { CPU_INTEGER_V1 }

internal data class SyntheticBenchmarkContext(
    val deviceModel: String,
    val sdkInt: Int,
    val cpuCores: Int,
    val appVersion: String,
    val scenario: SyntheticBenchmarkScenario,
    val workloadIterations: Int
)

internal data class SyntheticBenchmarkRun(
    val benchmarkContext: SyntheticBenchmarkContext,
    val profileName: String,
    val sampleNanos: List<Long>,
    val capturedAtMillis: Long
)

internal enum class SyntheticSampleQuality { RELIABLE, INSUFFICIENT, UNSTABLE, INVALID }

internal enum class SyntheticComparisonStatus {
    FASTER, SLOWER, INCONCLUSIVE, INCOMPARABLE
}

internal data class SyntheticBenchmarkComparison(
    val status: SyntheticComparisonStatus,
    val relativePercent: Double?,
    val disclaimer: String = "Resultados sintéticos locales: no son FPS ni una garantía de rendimiento en juegos."
)

/**
 * Quality gates deliberately reject short, noisy, invalid or incompatible runs.
 * Even a statistically separated synthetic run cannot establish a causal link
 * between an app's profile label and Android/system-level optimizations.
 */
internal object UltraBenchmarkStatistics {
    fun medianNanos(run: SyntheticBenchmarkRun): Long? {
        val valid = run.sampleNanos.takeIf { it.isNotEmpty() && it.all { value -> value > 0 } }
            ?: return null
        return valid.sorted()[valid.size / 2]
    }

    fun quality(run: SyntheticBenchmarkRun): SyntheticSampleQuality {
        if (run.sampleNanos.any { it <= 0L }) return SyntheticSampleQuality.INVALID
        if (run.sampleNanos.size < 5) return SyntheticSampleQuality.INSUFFICIENT
        val sorted = run.sampleNanos.sorted()
        val median = sorted[sorted.size / 2]
        if (median <= 0L) return SyntheticSampleQuality.INVALID
        return if ((sorted.last().toDouble() - sorted.first().toDouble()) / median > 0.30) {
            SyntheticSampleQuality.UNSTABLE
        } else SyntheticSampleQuality.RELIABLE
    }

    fun compare(
        before: SyntheticBenchmarkRun,
        after: SyntheticBenchmarkRun
    ): SyntheticBenchmarkComparison {
        if (before.benchmarkContext != after.benchmarkContext) {
            return SyntheticBenchmarkComparison(SyntheticComparisonStatus.INCOMPARABLE, null)
        }
        if (quality(before) != SyntheticSampleQuality.RELIABLE ||
            quality(after) != SyntheticSampleQuality.RELIABLE
        ) {
            return SyntheticBenchmarkComparison(SyntheticComparisonStatus.INCONCLUSIVE, null)
        }
        val beforeMedian = medianNanos(before) ?: return SyntheticBenchmarkComparison(
            SyntheticComparisonStatus.INCONCLUSIVE, null
        )
        val afterMedian = medianNanos(after) ?: return SyntheticBenchmarkComparison(
            SyntheticComparisonStatus.INCONCLUSIVE, null
        )
        val relativePercent = 100.0 * (beforeMedian.toDouble() - afterMedian.toDouble()) /
            beforeMedian.toDouble()
        val status = when {
            after.sampleNanos.max() < before.sampleNanos.min() ->
                SyntheticComparisonStatus.FASTER
            after.sampleNanos.min() > before.sampleNanos.max() ->
                SyntheticComparisonStatus.SLOWER
            else -> SyntheticComparisonStatus.INCONCLUSIVE
        }
        return SyntheticBenchmarkComparison(
            status = status,
            relativePercent = if (status == SyntheticComparisonStatus.INCONCLUSIVE) {
                null
            } else relativePercent
        )
    }
}

/**
 * Clock/workload injection makes the runner testable without an actual device
 * or a wall-clock assertion that can be flaky on GitHub emulators.
 */
internal class SyntheticBenchmarkRunner(
    private val clockNanos: () -> Long = System::nanoTime,
    private val workload: (Int) -> Unit = ::runIntegerWorkload
) {
    fun measure(
        context: SyntheticBenchmarkContext,
        profileName: String,
        warmups: Int = 2,
        samples: Int = 7
    ): SyntheticBenchmarkRun {
        require(context.deviceModel.isNotBlank() && context.deviceModel.length <= 80)
        require(context.appVersion.isNotBlank() && context.appVersion.length <= 80)
        require(context.sdkInt in 21..100 && context.cpuCores in 1..128)
        require(context.workloadIterations in 1_000..1_000_000)
        require(profileName in PerformanceProfile.entries.map { it.name })
        require(warmups in 1..3 && samples in 5..12)

        repeat(warmups) { workload(context.workloadIterations) }
        val times = List(samples) {
            val started = clockNanos()
            workload(context.workloadIterations)
            val elapsed = clockNanos() - started
            check(elapsed > 0L) { "Non-positive monotonic interval" }
            elapsed
        }
        return SyntheticBenchmarkRun(context, profileName, times, System.currentTimeMillis())
    }

    private companion object {
        @Volatile private var sink: Int = 0

        fun runIntegerWorkload(iterations: Int) {
            var result = 0x13579BDF
            repeat(iterations) { index ->
                result = (result xor index) * 1664525 + 1013904223
                result = result xor (result ushr 13)
            }
            sink = result
        }
    }
}

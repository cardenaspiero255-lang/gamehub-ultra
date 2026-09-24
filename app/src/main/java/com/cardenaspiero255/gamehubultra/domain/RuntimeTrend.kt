package com.cardenaspiero255.gamehubultra.domain

import kotlin.math.abs
import kotlin.math.round

data class RuntimeTrendSample(
    val batteryPercent: Int?,
    val thermalHeadroomPercent: Int?,
    val ramUsedPercent: Int?,
    val refreshRateHz: Int?
)

enum class RuntimeTrendDirection {
    UP,
    DOWN,
    STABLE,
    UNAVAILABLE
}

object RuntimeTrend {
    fun from(
        batteryPercent: Int?,
        thermalHeadroom: Float?,
        ramUsedPercent: Int?,
        refreshRateHz: Float?
    ): RuntimeTrendSample = RuntimeTrendSample(
        batteryPercent = batteryPercent,
        thermalHeadroomPercent = thermalHeadroom?.let { round(it.coerceIn(0f, 1f) * 100f).toInt() },
        ramUsedPercent = ramUsedPercent,
        refreshRateHz = refreshRateHz?.let(::round)?.toInt()
    )

    fun direction(previous: Int?, current: Int?, stableThreshold: Int = 1): RuntimeTrendDirection {
        if (previous == null || current == null) return RuntimeTrendDirection.UNAVAILABLE
        val delta = current - previous
        return when {
            delta > stableThreshold -> RuntimeTrendDirection.UP
            delta < -stableThreshold -> RuntimeTrendDirection.DOWN
            else -> RuntimeTrendDirection.STABLE
        }
    }

    fun absoluteDelta(previous: Int?, current: Int?): Int? =
        if (previous == null || current == null) null else abs(current - previous)
}

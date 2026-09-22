package com.cardenaspiero255.gamehubultra

object BatteryTelemetry {
    fun sanitizePercentage(value: Int?): Int? = value?.takeIf { it in 0..100 }

    fun fromBroadcast(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return sanitizePercentage(((level.toLong() * 100L) / scale).toInt().coerceIn(0, 100))
    }
}

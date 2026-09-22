package com.cardenaspiero255.gamehubultra.domain

enum class ThermalPreference(val title: String) {
    ADAPTIVE("Adaptativo"),
    COOLER("Más frío"),
    BALANCED("Equilibrado"),
    PERFORMANCE("Máximo rendimiento")
}

data class GameProfileConfig(
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val thermalPreference: ThermalPreference = ThermalPreference.ADAPTIVE,
    val refreshRateTargetHz: Int? = null
) {
    fun resolveRefreshRateTarget(supportedRefreshRatesHz: Set<Int>): Int? =
        refreshRateTargetHz?.takeIf { it in supportedRefreshRatesHz }
}

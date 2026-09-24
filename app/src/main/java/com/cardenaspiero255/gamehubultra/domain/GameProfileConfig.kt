package com.cardenaspiero255.gamehubultra.domain

enum class ThermalPreference(val title: String) {
    ADAPTIVE("Adaptativo"),
    COOLER("Más frío"),
    BALANCED("Equilibrado"),
    PERFORMANCE("Máximo rendimiento")
}

enum class OrientationPreference(val title: String) {
    AUTO("Automática"),
    PORTRAIT("Vertical"),
    LANDSCAPE("Horizontal")
}

data class ResolutionTarget(
    val width: Int,
    val height: Int
) {
    fun isSupported(supported: Set<ResolutionTarget>): Boolean = this in supported
}

data class GameProfileConfig(
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val thermalPreference: ThermalPreference = ThermalPreference.ADAPTIVE,
    val refreshRateTargetHz: Int? = null,
    val resolutionTarget: ResolutionTarget? = null,
    val orientationPreference: OrientationPreference = OrientationPreference.AUTO
) {
    fun resolveRefreshRateTarget(supportedRefreshRatesHz: Set<Int>): Int? =
        refreshRateTargetHz?.takeIf { it in supportedRefreshRatesHz }

    fun resolveResolutionTarget(supportedResolutions: Set<ResolutionTarget>): ResolutionTarget? =
        resolutionTarget?.takeIf { it.isSupported(supportedResolutions) }
}

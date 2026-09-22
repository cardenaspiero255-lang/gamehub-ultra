package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities

data class PerformanceState(
    val selectedProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val capabilities: DeviceCapabilities? = null
)

class PerformanceController(
    private val capabilities: DeviceCapabilities
) {
    fun select(profile: PerformanceProfile): PerformanceState =
        PerformanceState(selectedProfile = profile, capabilities = capabilities)
}

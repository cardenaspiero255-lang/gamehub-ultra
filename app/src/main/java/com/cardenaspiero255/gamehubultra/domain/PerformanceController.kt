package com.cardenaspiero255.gamehubultra.domain

import android.os.Build
import android.view.Window
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities

data class PerformanceState(
    val selectedProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val capabilities: DeviceCapabilities? = null,
    val sustainedModeApplied: Boolean = false
)

class PerformanceController(
    private val capabilities: DeviceCapabilities
) {
    companion object {
        internal fun shouldEnableSustainedMode(
            profile: PerformanceProfile,
            platformSupportsSustainedMode: Boolean,
            deviceSupportsSustainedMode: Boolean
        ): Boolean =
            profile.sustainedPerformanceIntent &&
                platformSupportsSustainedMode &&
                deviceSupportsSustainedMode
    }

    fun select(profile: PerformanceProfile): PerformanceState =
        PerformanceState(
            selectedProfile = profile,
            capabilities = capabilities,
            sustainedModeApplied = false
        )

    fun apply(profile: PerformanceProfile, window: Window): PerformanceState {
        val platformSupportsSustainedMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        val enableSustained = shouldEnableSustainedMode(
            profile = profile,
            platformSupportsSustainedMode = platformSupportsSustainedMode,
            deviceSupportsSustainedMode = capabilities.sustainedPerformanceSupported
        )

        if (platformSupportsSustainedMode) {
            window.setSustainedPerformanceMode(enableSustained)
        }

        return PerformanceState(
            selectedProfile = profile,
            capabilities = capabilities,
            sustainedModeApplied = enableSustained
        )
    }
}

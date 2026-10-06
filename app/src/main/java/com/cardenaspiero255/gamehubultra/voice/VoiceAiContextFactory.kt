package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

internal object VoiceAiContextFactory {
    fun create(
        selectedGamePackage: String?,
        selectedProfile: PerformanceProfile,
        device: DeviceInfo,
        diagnostics: RuntimeDiagnostics,
        capabilities: DeviceCapabilities
    ): GameHubAiContext =
        GameHubAiContext(
            selectedGamePackage = selectedGamePackage,
            sustainedPerformanceSupported = capabilities.sustainedPerformanceSupported,
            cpuCores = device.cpuCores,
            totalRamMb = device.totalRamMb.toInt(),
            gpuAvailable =
                !device.gpuRenderer.isNullOrBlank() ||
                    !device.gpuVendor.isNullOrBlank(),
            thermalStatus = diagnostics.thermal.status,
            thermalHeadroom = diagnostics.thermal.headroom,
            batteryPercent = diagnostics.battery.percent,
            charging = diagnostics.battery.charging,
            refreshRateHz = diagnostics.refresh.currentRefreshRateHz,
            networkValidated = diagnostics.connectivity.validated,
            networkLatencyMs = diagnostics.connectivity.latencyMs,
            downstreamBandwidthKbps =
                diagnostics.connectivity.downstreamBandwidthKbps?.toLong(),
            storageFreePercent = diagnostics.storage.freePercent,
            inputDeviceCount = diagnostics.inputDeviceCount,
            selectedProfile = selectedProfile,
            sessionActive = selectedGamePackage != null
        )
}

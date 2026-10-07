package com.cardenaspiero255.gamehubultra.session

import com.cardenaspiero255.gamehubultra.domain.GamingReadiness
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessCalculator
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessInput
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics

internal object SessionCoachTelemetryMapper {
    fun snapshot(
        timestampMillis: Long,
        diagnostics: RuntimeDiagnostics
    ): SessionCoachSnapshot =
        SessionCoachSnapshot(
            timestampMillis = timestampMillis,
            batteryPercent = diagnostics.battery.percent,
            thermalStatus = diagnostics.thermal.status,
            thermalHeadroom = diagnostics.thermal.headroom,
            refreshRateHz = diagnostics.refresh.currentRefreshRateHz,
            latencyMs = diagnostics.connectivity.latencyMs,
            memoryUsedPercent = diagnostics.memory.usedPercent,
            batteryCharging = diagnostics.battery.charging,
            powerSaveMode = diagnostics.battery.powerSaveMode
        )

    fun readiness(
        device: DeviceInfo,
        diagnostics: RuntimeDiagnostics
    ): GamingReadiness =
        GamingReadinessCalculator.calculate(
            GamingReadinessInput(
                cpuCores = device.cpuCores,
                totalRamMb = device.totalRamMb,
                gpuAvailable = !device.gpuRenderer.isNullOrBlank() ||
                    !device.gpuVendor.isNullOrBlank(),
                thermalStatus = diagnostics.thermal.status,
                thermalHeadroom = diagnostics.thermal.headroom,
                batteryPercent = diagnostics.battery.percent,
                charging = diagnostics.battery.charging,
                refreshRateHz = diagnostics.refresh.currentRefreshRateHz,
                networkValidated = diagnostics.connectivity.validated,
                networkLatencyMs = diagnostics.connectivity.latencyMs,
                downstreamBandwidthKbps = diagnostics.connectivity.downstreamBandwidthKbps,
                storageFreePercent = diagnostics.storage.freePercent,
                inputDeviceCount = diagnostics.inputDeviceCount
            )
        )
}

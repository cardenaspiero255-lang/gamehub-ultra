package com.cardenaspiero255.gamehubultra.session

import com.cardenaspiero255.gamehubultra.platform.BatteryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.MemoryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.StorageTelemetry
import com.cardenaspiero255.gamehubultra.platform.ThermalTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionCoachTelemetryMapperTest {
    @Test
    fun snapshotUsesOnlyMeasuredSessionSignals() {
        val diagnostics = diagnostics()

        val snapshot = SessionCoachTelemetryMapper.snapshot(
            timestampMillis = 1234L,
            diagnostics = diagnostics
        )

        assertEquals(1234L, snapshot.timestampMillis)
        assertEquals(72, snapshot.batteryPercent)
        assertEquals(3, snapshot.thermalStatus)
        assertEquals(0.82f, snapshot.thermalHeadroom)
        assertEquals(120f, snapshot.refreshRateHz)
        assertEquals(45L, snapshot.latencyMs)
    }

    @Test
    fun readinessUsesRealDeviceAndRuntimeEvidence() {
        val device = DeviceInfo(
            manufacturer = "Vivo",
            model = "V25 Pro",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = listOf("arm64-v8a"),
            cpuModel = "Dimensity",
            cpuCores = 8,
            totalRamMb = 12_288,
            gpuVendor = "ARM",
            gpuRenderer = "Mali"
        )

        val readiness = SessionCoachTelemetryMapper.readiness(
            device = device,
            diagnostics = diagnostics()
        )

        assertTrue(readiness.score in 0..100)
        assertTrue(readiness.reasons.any { it.startsWith("Térmica:") })
        assertTrue(readiness.reasons.any { it.startsWith("Latencia:") })
        assertTrue(readiness.reasons.any { it.startsWith("Refresco:") })
    }

    private fun diagnostics() = RuntimeDiagnostics(
        thermal = ThermalTelemetry(
            status = 3,
            headroom = 0.82f
        ),
        battery = BatteryRuntimeTelemetry(
            percent = 72,
            charging = false,
            powerSaveMode = false
        ),
        refresh = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60, 120),
            currentRefreshRateHz = 120f
        ),
        connectivity = ConnectivityTelemetry(
            networkHandle = 1L,
            connected = true,
            validated = true,
            metered = false,
            transport = "Wi-Fi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = 45L
        ),
        storage = StorageTelemetry(
            freeBytes = 75L,
            totalBytes = 100L
        ),
        memory = MemoryRuntimeTelemetry(
            totalRamMb = 12_288L,
            availableRamMb = 6_000L,
            usedRamMb = 6_288L
        ),
        inputDeviceCount = 1,
        peripherals = PeripheralDiagnostics(
            gamepadCount = 1,
            keyboardCount = 0,
            mouseCount = 0,
            externalAudioCount = 1
        )
    )
}

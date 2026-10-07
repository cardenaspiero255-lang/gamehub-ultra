package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.UltraFrontierWorldStateRegistry
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.BatteryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.MemoryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.StorageTelemetry
import com.cardenaspiero255.gamehubultra.platform.ThermalTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceAiContextFactoryTest {
    @Test
    fun `maps runtime telemetry into the shared voice AI context`() {
        val device = DeviceInfo(
            manufacturer = "Vivo",
            model = "V25 Pro",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = listOf("arm64-v8a"),
            cpuModel = "Dimensity 1300",
            cpuCores = 8,
            totalRamMb = 12_288,
            gpuVendor = "ARM",
            gpuRenderer = "Mali-G77"
        )
        val diagnostics = RuntimeDiagnostics(
            thermal = ThermalTelemetry(status = 3, headroom = 0.85f),
            battery = BatteryRuntimeTelemetry(
                percent = 17,
                charging = true,
                powerSaveMode = false
            ),
            refresh = RefreshTelemetry(
                supportedRefreshRatesHz = setOf(60, 120),
                currentRefreshRateHz = 120f
            ),
            connectivity = ConnectivityTelemetry(
                networkHandle = 7L,
                connected = true,
                validated = true,
                metered = false,
                transport = "Wi-Fi",
                downstreamBandwidthKbps = 250_000,
                latencyMs = 42L
            ),
            storage = StorageTelemetry(
                freeBytes = 35L,
                totalBytes = 100L
            ),
            memory = MemoryRuntimeTelemetry(
                totalRamMb = 12_288,
                availableRamMb = 6_144,
                usedRamMb = 6_144
            ),
            inputDeviceCount = 2,
            peripherals = PeripheralDiagnostics(
                gamepadCount = 1,
                keyboardCount = 1,
                mouseCount = 0,
                externalAudioCount = 0
            )
        )
        val capabilities = DeviceCapabilities(
            sustainedPerformanceSupported = true,
            thermalStatusAvailable = true,
            performanceHintsAvailable = true
        )

        val context = VoiceAiContextFactory.create(
            selectedGamePackage = "com.example.game",
            selectedProfile = PerformanceProfile.X4,
            device = device,
            diagnostics = diagnostics,
            capabilities = capabilities
        )

        assertEquals("com.example.game", context.selectedGamePackage)
        assertTrue(context.sustainedPerformanceSupported)
        assertEquals(8, context.cpuCores)
        assertEquals(12_288, context.totalRamMb)
        assertTrue(context.gpuAvailable)
        assertEquals(3, context.thermalStatus)
        assertEquals(0.85f, context.thermalHeadroom)
        assertEquals(17, context.batteryPercent)
        assertTrue(context.charging)
        assertEquals(120f, context.refreshRateHz)
        assertTrue(context.networkValidated)
        assertEquals(42L, context.networkLatencyMs)
        assertEquals(250_000L, context.downstreamBandwidthKbps)
        assertEquals(35, context.storageFreePercent)
        assertEquals(2, context.inputDeviceCount)
        assertEquals(PerformanceProfile.X4, context.selectedProfile)
        assertTrue(context.sessionActive)
        val world = requireNotNull(UltraFrontierWorldStateRegistry.snapshot())
        assertEquals("com.example.game", world.selectedGamePackage)
        assertEquals(17, world.batteryPercent)
        assertEquals(42L, world.networkLatencyMs)
        UltraFrontierWorldStateRegistry.clear()
    }

    @Test
    fun `reports no gpu and inactive session when runtime has neither`() {
        val device = DeviceInfo(
            manufacturer = "Test",
            model = "Device",
            androidVersion = "14",
            sdkInt = 34,
            supportedAbis = emptyList(),
            cpuModel = "cpu",
            cpuCores = 4,
            totalRamMb = 4096,
            gpuVendor = null,
            gpuRenderer = null
        )
        val diagnostics = RuntimeDiagnostics(
            thermal = ThermalTelemetry(status = null, headroom = null),
            battery = BatteryRuntimeTelemetry(
                percent = null,
                charging = false,
                powerSaveMode = false
            ),
            refresh = RefreshTelemetry(
                supportedRefreshRatesHz = emptySet(),
                currentRefreshRateHz = null
            ),
            connectivity = ConnectivityTelemetry(
                networkHandle = null,
                connected = false,
                validated = false,
                metered = true,
                transport = null,
                downstreamBandwidthKbps = null,
                latencyMs = null
            ),
            storage = StorageTelemetry(freeBytes = 0L, totalBytes = 0L),
            memory = MemoryRuntimeTelemetry(
                totalRamMb = 4096,
                availableRamMb = 2048,
                usedRamMb = 2048
            ),
            inputDeviceCount = 0,
            peripherals = PeripheralDiagnostics(
                gamepadCount = 0,
                keyboardCount = 0,
                mouseCount = 0,
                externalAudioCount = 0
            )
        )
        val capabilities = DeviceCapabilities(
            sustainedPerformanceSupported = false,
            thermalStatusAvailable = false,
            performanceHintsAvailable = false
        )

        val context = VoiceAiContextFactory.create(
            selectedGamePackage = null,
            selectedProfile = PerformanceProfile.BALANCED,
            device = device,
            diagnostics = diagnostics,
            capabilities = capabilities
        )

        assertFalse(context.gpuAvailable)
        assertFalse(context.sessionActive)
        assertFalse(context.networkValidated)
        assertEquals(0, context.storageFreePercent)
    }
}

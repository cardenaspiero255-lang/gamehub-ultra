package com.cardenaspiero255.gamehubultra.domain

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SmartPerformanceAdvisorTest {
    private val device = DeviceInfo(
        manufacturer = "Qualcomm",
        model = "Test",
        androidVersion = "15",
        sdkInt = 35,
        supportedAbis = listOf("arm64-v8a"),
        cpuModel = "Snapdragon test",
        cpuCores = 8,
        totalRamMb = 8192,
        gpuVendor = "Qualcomm",
        gpuRenderer = "Adreno (TM) 750"
    )

    private fun runtime(
        thermalStatus: Int? = 0,
        thermalHeadroom: Float? = 0.25f,
        batteryPercent: Int? = 90,
        ramUsedPercent: Int = 55,
        freeStoragePercent: Int = 40
    ) = RuntimeDiagnostics(
        thermal = ThermalTelemetry(thermalStatus, thermalHeadroom),
        battery = BatteryRuntimeTelemetry(batteryPercent, true, false),
        refresh = RefreshTelemetry(setOf(60, 120), 120f),
        connectivity = ConnectivityTelemetry(1L, true, true, false, "Wi‑Fi", 100_000, 35L),
        storage = StorageTelemetry(40L, 100L),
        memory = MemoryRuntimeTelemetry(8192L, (100 - ramUsedPercent).toLong(), 8192L * ramUsedPercent / 100L),
        inputDeviceCount = 1,
        peripherals = PeripheralDiagnostics(1, 0, 0, 1)
    )

    @Test
    fun thermalPressureForcesBalancedFallback() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(device, runtime(thermalStatus = 4, thermalHeadroom = 0.9f), "game", "1", null, PerformanceProfile.X4)
        )
        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertEquals(PerformanceProfile.BALANCED, result.safeFallback)
    }

    @Test
    fun failedProfileIsPenalizedAndNotRepeatedTwice() {
        val observations = listOf(
            OptimizationObservation(
                contextKey = "same",
                profile = PerformanceProfile.X4,
                failed = true,
                timestampMillis = 1L
            ),
            OptimizationObservation(
                contextKey = "same",
                profile = PerformanceProfile.X4,
                failed = true,
                timestampMillis = 2L
            )
        )
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(device, runtime(), "game", "1", null, PerformanceProfile.BALANCED, observations)
        )
        assertNotEquals(PerformanceProfile.X4, result.profile)
    }

    @Test
    fun gpuFamilyAndDriverSelectionRespectCompatibility() {
        assertEquals(
            GpuFamily.MALI,
            SmartPerformanceAdvisor.detectGpuFamily("ARM", "Mali-G720")
        )
        val candidates = listOf(
            DriverCandidate("first", "First", GpuFamily.ADRENO, "Vulkan", true, compatibilityScore = 50, benchmarkScore = 30),
            DriverCandidate("best", "Best", GpuFamily.ADRENO, "Vulkan", true, knownGood = true, compatibilityScore = 90, benchmarkScore = 80),
            DriverCandidate("turnip-mali", "Turnip", GpuFamily.MALI, "Vulkan", true, isTurnip = true)
        )
        assertEquals(
            "best",
            SmartPerformanceAdvisor.selectDriver(GpuFamily.ADRENO, "Vulkan", candidates)?.id
        )
        assertEquals(
            null,
            SmartPerformanceAdvisor.selectDriver(GpuFamily.MALI, "Vulkan", candidates.filter { it.gpuFamily == GpuFamily.MALI }) 
        )
    }

    @Test
    fun unknownGpuFallsBackToSystemOnly() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device.copy(gpuVendor = null, gpuRenderer = null),
                runtime = runtime(),
                gamePackage = "game",
                gameVersion = "1",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED
            )
        )
        assertEquals(DriverStrategy.SYSTEM_ONLY, result.driverStrategy)
        assertTrue(result.evidence.isNotEmpty())
    }
}

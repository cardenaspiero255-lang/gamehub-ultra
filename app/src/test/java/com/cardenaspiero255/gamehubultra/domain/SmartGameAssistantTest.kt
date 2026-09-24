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
import kotlin.test.assertTrue

class SmartGameAssistantTest {
    private val device = DeviceInfo(
        manufacturer = "Test",
        model = "Ultra",
        androidVersion = "16",
        sdkInt = 36,
        supportedAbis = listOf("arm64-v8a"),
        cpuModel = "Test CPU",
        cpuCores = 8,
        totalRamMb = 8192,
        gpuVendor = "Qualcomm",
        gpuRenderer = "Adreno 830"
    )

    private fun runtime(
        thermalStatus: Int = 0,
        thermalHeadroom: Float = 0.2f,
        battery: Int = 90,
        ramUsed: Int = 45
    ) = RuntimeDiagnostics(
        thermal = ThermalTelemetry(thermalStatus, thermalHeadroom),
        battery = BatteryRuntimeTelemetry(battery, true, false),
        refresh = RefreshTelemetry(setOf(60, 90, 120, 165), 120f),
        connectivity = ConnectivityTelemetry(1L, true, true, false, "Wi-Fi", 100_000, 30L),
        storage = StorageTelemetry(60L, 100L),
        memory = MemoryRuntimeTelemetry(8192L, 8192L - (8192L * ramUsed / 100L), 8192L * ramUsed / 100L),
        inputDeviceCount = 1,
        peripherals = PeripheralDiagnostics(1, 0, 0, 0)
    )

    private fun input(
        runtime: RuntimeDiagnostics? = runtime(),
        packageName: String? = "com.example.game"
    ) = SmartGameAssistantInput(
        device = device,
        runtime = runtime,
        gamePackage = packageName,
        currentProfile = PerformanceProfile.BALANCED,
        historicalObservations = emptyList(),
        existingRecommendation = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = runtime,
                gamePackage = packageName,
                gameVersion = "1",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED
            )
        )
    )

    @Test
    fun returnsAllThreePresets() {
        assertEquals(
            AssistantPreset.entries,
            SmartGameAssistant.suggestAll(input()).map { it.preset }
        )
    }

    @Test
    fun batteryPresetUsesConservativeSettings() {
        val suggestion = SmartGameAssistant.suggest(input(), AssistantPreset.BATTERY)
        assertEquals(PerformanceProfile.BALANCED, suggestion.profile)
        assertEquals(ThermalPreference.COOLER, suggestion.thermalPreference)
        assertEquals(60, suggestion.refreshRateTargetHz)
        assertEquals(ResolutionAdvice.REDUCE_ONE_STEP, suggestion.resolutionAdvice)
    }

    @Test
    fun thermalPressureReducesLoadAndExplainsWhy() {
        val suggestion = SmartGameAssistant.suggest(
            input(runtime = runtime(thermalStatus = 4, thermalHeadroom = 0.9f)),
            AssistantPreset.RECOMMENDED
        )
        assertEquals(ResolutionAdvice.REDUCE_ONE_STEP, suggestion.resolutionAdvice)
        assertTrue(suggestion.reason.contains("térmica", ignoreCase = true))
        assertTrue(suggestion.refreshRateTargetHz == 60 || suggestion.refreshRateTargetHz == 90)
    }

    @Test
    fun noGameSelectedProducesActionableExplanation() {
        val suggestion = SmartGameAssistant.suggest(input(packageName = null), AssistantPreset.RECOMMENDED)
        assertTrue(suggestion.reason.contains("Selecciona un juego", ignoreCase = true))
        assertTrue(suggestion.evidence.first().contains("ninguno", ignoreCase = true))
    }
}

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartPerformanceAdvisor44Test {
    private val device = DeviceInfo(
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

    private fun runtime(
        thermalStatus: Int? = 0,
        thermalHeadroom: Float? = 0.30f,
        batteryPercent: Int? = 80,
        refreshRateHz: Float? = 120f,
        latencyMs: Long? = 35L
    ) = RuntimeDiagnostics(
        thermal = ThermalTelemetry(thermalStatus, thermalHeadroom),
        battery = BatteryRuntimeTelemetry(batteryPercent, false, false),
        refresh = RefreshTelemetry(setOf(60, 90, 120), refreshRateHz),
        connectivity = ConnectivityTelemetry(
            networkHandle = 1L,
            connected = true,
            validated = true,
            metered = false,
            transport = "Wi-Fi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = latencyMs
        ),
        storage = StorageTelemetry(40L, 100L),
        memory = MemoryRuntimeTelemetry(12_288L, 6_144L, 6_144L),
        inputDeviceCount = 1,
        peripherals = PeripheralDiagnostics(1, 0, 0, 0)
    )

    @Test
    fun `recommendation explanation separates measured inferred and remembered evidence`() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = runtime(),
                gamePackage = "game.example",
                gameVersion = "1.0",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED,
                historicalObservations = listOf(
                    OptimizationObservation(
                        contextKey = "ctx",
                        profile = PerformanceProfile.X4,
                        measuredFps = 90f,
                        stable = true,
                        timestampMillis = 1L
                    )
                )
            )
        )

        val provenances = result.explanation.evidence.map { it.provenance }.toSet()
        assertTrue(RecommendationEvidenceProvenance.MEASURED in provenances)
        assertTrue(RecommendationEvidenceProvenance.INFERRED in provenances)
        assertTrue(RecommendationEvidenceProvenance.REMEMBERED in provenances)
        assertTrue(result.explanation.conciseSummary.isNotBlank())
        assertEquals(result.reason, result.explanation.reason)
    }

    @Test
    fun `missing telemetry is explicit and lowers confidence`() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = null,
                gamePackage = "game.example",
                gameVersion = "1.0",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED
            )
        )

        assertEquals(RecommendationConfidenceBand.LOW, result.explanation.confidence)
        assertTrue(result.explanation.unavailableData.any { it.contains("telemet", ignoreCase = true) })
        assertTrue(result.explanation.unavailableData.any { it.contains("bater", ignoreCase = true) })
        assertTrue(result.explanation.unavailableData.any { it.contains("térm", ignoreCase = true) })
    }

    @Test
    fun `contradictory history is surfaced and confidence is not high`() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = runtime(),
                gamePackage = "game.example",
                gameVersion = "1.0",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED,
                historicalObservations = listOf(
                    OptimizationObservation(
                        contextKey = "ctx",
                        profile = PerformanceProfile.X4,
                        stable = true,
                        measuredFps = 120f,
                        timestampMillis = 1L
                    ),
                    OptimizationObservation(
                        contextKey = "ctx",
                        profile = PerformanceProfile.X4,
                        feedbackDecision = OptimizationFeedbackDecision.REJECTED,
                        timestampMillis = 2L
                    )
                )
            )
        )

        assertTrue(result.explanation.contradictions.isNotEmpty())
        assertFalse(result.explanation.confidence == RecommendationConfidenceBand.HIGH)
    }

    @Test
    fun `outcome comparison is qualitative and never presented as a guarantee`() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = runtime(),
                gamePackage = "game.example",
                gameVersion = "1.0",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.BALANCED
            )
        )

        assertEquals(
            setOf(
                RecommendationOutcomeObjective.RECOMMENDED,
                RecommendationOutcomeObjective.BALANCED,
                RecommendationOutcomeObjective.BATTERY
            ),
            result.explanation.outcomes.map { it.objective }.toSet()
        )
        assertTrue(result.explanation.outcomes.all { !it.guaranteed })
        assertTrue(result.explanation.outcomes.all { it.summary.isNotBlank() })
    }

    @Test
    fun `safety constraint explains why aggressive profile was not recommended`() {
        val result = SmartPerformanceAdvisor.recommend(
            SmartPerformanceInput(
                device = device,
                runtime = runtime(thermalStatus = 4, thermalHeadroom = 0.90f),
                gamePackage = "game.example",
                gameVersion = "1.0",
                emulatorBackend = null,
                currentProfile = PerformanceProfile.X4
            )
        )

        assertEquals(PerformanceProfile.BALANCED, result.profile)
        assertTrue(
            result.explanation.withheldReasons.any {
                it.contains("X4", ignoreCase = true) ||
                    it.contains("agres", ignoreCase = true) ||
                    it.contains("térm", ignoreCase = true)
            }
        )
        assertTrue(result.explanation.conciseSummary.length <= 180)
    }
}

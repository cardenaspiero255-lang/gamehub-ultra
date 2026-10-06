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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartRecommendationExplanationFactoryTest {
    private val fullDevice = DeviceInfo(
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
        thermalHeadroom: Float? = 0.25f,
        batteryPercent: Int? = 80,
        refreshRateHz: Float? = 120f,
        latencyMs: Long? = 30L
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

    private fun input(
        device: DeviceInfo = fullDevice,
        runtime: RuntimeDiagnostics? = runtime(),
        currentProfile: PerformanceProfile = PerformanceProfile.BALANCED,
        history: List<OptimizationObservation> = emptyList(),
        external: List<RecommendationExternalEvidence> = emptyList(),
        gameVersion: String? = "1.0"
    ) = SmartPerformanceInput(
        device = device,
        runtime = runtime,
        gamePackage = "game.example",
        gameVersion = gameVersion,
        emulatorBackend = null,
        currentProfile = currentProfile,
        historicalObservations = history,
        externalEvidence = external
    )

    private fun build(
        input: SmartPerformanceInput = input(),
        recommended: PerformanceProfile = PerformanceProfile.X4,
        reason: String = "Razón verificable.",
        measuredGood: Map<PerformanceProfile, Int> = emptyMap(),
        accepted: Map<PerformanceProfile, Int> = emptyMap(),
        knownBad: Map<PerformanceProfile, Int> = emptyMap(),
        thermalHot: Boolean = false,
        lowBattery: Boolean = false,
        memoryPressure: Boolean = false,
        storagePressure: Boolean = false
    ) = SmartRecommendationExplanationFactory.build(
        input = input,
        recommendedProfile = recommended,
        reason = reason,
        measuredGood = measuredGood,
        acceptedFeedback = accepted,
        knownBad = knownBad,
        thermalHot = thermalHot,
        lowBattery = lowBattery,
        memoryPressure = memoryPressure,
        storagePressure = storagePressure
    )

    @Test
    fun `legacy explanation remains explicit inferred evidence`() {
        val result = SmartRecommendationExplanation.legacy(
            reason = "Razón",
            evidence = listOf("dato")
        )

        assertEquals(RecommendationConfidenceBand.MEDIUM, result.confidence)
        assertEquals(RecommendationEvidenceProvenance.INFERRED, result.evidence.single().provenance)
        assertEquals("Razón", result.conciseSummary)
        assertTrue(result.outcomes.isEmpty())
    }

    @Test
    fun `full context carries every evidence provenance and high confidence`() {
        val history = listOf(
            OptimizationObservation(
                contextKey = "ctx",
                profile = PerformanceProfile.X4,
                stable = true,
                timestampMillis = 1L
            )
        )
        val result = build(
            input = input(
                history = history,
                external = listOf(
                    RecommendationExternalEvidence(
                        text = "Fuente de fabricante vigente.",
                        sourceLabel = "Vendor docs",
                        fresh = true,
                        authoritative = true,
                        supportsRecommendation = true
                    )
                )
            ),
            measuredGood = mapOf(PerformanceProfile.X4 to 1),
            accepted = mapOf(PerformanceProfile.BALANCED to 1)
        )

        val provenance = result.evidence.map { it.provenance }.toSet()
        assertTrue(RecommendationEvidenceProvenance.MEASURED in provenance)
        assertTrue(RecommendationEvidenceProvenance.INFERRED in provenance)
        assertTrue(RecommendationEvidenceProvenance.REMEMBERED in provenance)
        assertTrue(RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED in provenance)
        assertTrue(result.evidence.any { it.text.contains("GPU:") })
        assertTrue(result.evidence.any { it.text.contains("Renderer:") })
        assertTrue(result.evidence.any { it.text.contains("Refresco") })
        assertTrue(result.evidence.any { it.text.contains("térmico") })
        assertTrue(result.evidence.any { it.text.contains("Batería") })
        assertEquals(RecommendationConfidenceBand.HIGH, result.confidence)
        assertTrue(result.unavailableData.isEmpty())
    }

    @Test
    fun `missing runtime gpu and version expose unavailable data with low confidence`() {
        val result = build(
            input = input(
                device = fullDevice.copy(gpuVendor = null, gpuRenderer = null),
                runtime = null,
                gameVersion = null
            ),
            recommended = PerformanceProfile.BALANCED
        )

        assertEquals(RecommendationConfidenceBand.LOW, result.confidence)
        listOf(
            "Telemetría en tiempo real",
            "Estado térmico",
            "Batería",
            "Frecuencia de refresco",
            "Latencia de red",
            "Identidad de GPU",
            "Versión del juego"
        ).forEach { missing ->
            assertTrue(missing in result.unavailableData)
        }
    }

    @Test
    fun `partial runtime marks each unavailable live signal`() {
        val result = build(
            input = input(
                runtime = runtime(
                    thermalStatus = null,
                    thermalHeadroom = null,
                    batteryPercent = null,
                    refreshRateHz = null,
                    latencyMs = null
                )
            ),
            recommended = PerformanceProfile.BALANCED
        )

        assertEquals(RecommendationConfidenceBand.LOW, result.confidence)
        assertTrue(result.unavailableData.containsAll(
            listOf("Estado térmico", "Batería", "Frecuencia de refresco", "Latencia de red")
        ))
    }

    @Test
    fun `weak external evidence lowers otherwise complete context to medium`() {
        val result = build(
            input = input(
                external = listOf(
                    RecommendationExternalEvidence(
                        text = "Documento antiguo.",
                        sourceLabel = "Old source",
                        fresh = false,
                        authoritative = false
                    )
                )
            )
        )

        assertEquals(RecommendationConfidenceBand.MEDIUM, result.confidence)
    }

    @Test
    fun `conflicting external evidence is surfaced and confidence becomes low`() {
        val result = build(
            input = input(
                external = listOf(
                    RecommendationExternalEvidence("A favor", "A", true, true, true),
                    RecommendationExternalEvidence("En contra", "B", true, true, false)
                )
            )
        )

        assertEquals(RecommendationConfidenceBand.LOW, result.confidence)
        assertTrue(result.contradictions.any { it.contains("fuentes externas") })
    }

    @Test
    fun `local stable and negative evidence for same profile is a contradiction`() {
        val result = build(
            measuredGood = mapOf(PerformanceProfile.X4 to 1),
            knownBad = mapOf(PerformanceProfile.X4 to 1)
        )

        assertEquals(RecommendationConfidenceBand.LOW, result.confidence)
        assertTrue(result.contradictions.any { it.contains("X4") })
        assertTrue(result.evidence.any { it.text.contains("fallos") })
    }

    @Test
    fun `safety restriction can contradict positive aggressive history`() {
        val result = build(
            recommended = PerformanceProfile.BALANCED,
            measuredGood = mapOf(PerformanceProfile.X4 to 2),
            thermalHot = true
        )

        assertTrue(result.contradictions.any { it.contains("restricciones actuales") })
        assertTrue(result.withheldReasons.any { it.contains("térmica") })
        assertTrue(result.changeExplanation?.contains("seguridad") == true)
    }

    @Test
    fun `all X4 withholding reasons are explainable`() {
        assertTrue(build(recommended = PerformanceProfile.BALANCED, thermalHot = true)
            .withheldReasons.any { it.contains("térmica") })
        assertTrue(build(recommended = PerformanceProfile.BALANCED, lowBattery = true)
            .withheldReasons.any { it.contains("batería") })
        assertTrue(build(recommended = PerformanceProfile.BALANCED, memoryPressure = true)
            .withheldReasons.any { it.contains("memoria") })
        assertTrue(build(recommended = PerformanceProfile.BALANCED, storagePressure = true)
            .withheldReasons.any { it.contains("almacenamiento") })
        assertTrue(build(
            recommended = PerformanceProfile.BALANCED,
            knownBad = mapOf(PerformanceProfile.X4 to 2)
        ).withheldReasons.any { it.contains("negativos") })
        assertTrue(build(
            input = input(device = fullDevice.copy(cpuCores = 2, totalRamMb = 2048)),
            recommended = PerformanceProfile.BALANCED
        ).withheldReasons.any { it.contains("capacidad mínima") })
    }

    @Test
    fun `low refresh explains why interpolation is not prioritized`() {
        val result = build(
            input = input(runtime = runtime(refreshRateHz = 60f)),
            recommended = PerformanceProfile.BALANCED
        )
        assertTrue(result.withheldReasons.any { it.contains("interpolación", ignoreCase = true) })
    }

    @Test
    fun `change explanation distinguishes same profile history and normal change`() {
        assertNull(build(
            input = input(currentProfile = PerformanceProfile.X4),
            recommended = PerformanceProfile.X4
        ).changeExplanation)

        assertTrue(build(
            input = input(currentProfile = PerformanceProfile.FRAME_INTERPOLATION),
            recommended = PerformanceProfile.X4,
            knownBad = mapOf(PerformanceProfile.FRAME_INTERPOLATION to 1)
        ).changeExplanation?.contains("historial") == true)

        assertTrue(build(
            input = input(currentProfile = PerformanceProfile.BALANCED),
            recommended = PerformanceProfile.X4
        ).changeExplanation?.contains("combinar") == true)
    }

    @Test
    fun `outcomes stay qualitative and long concise summaries are bounded`() {
        val result = build(reason = "x".repeat(250))
        assertEquals(180, result.conciseSummary.length)
        assertEquals(
            setOf(
                RecommendationOutcomeObjective.RECOMMENDED,
                RecommendationOutcomeObjective.BALANCED,
                RecommendationOutcomeObjective.BATTERY
            ),
            result.outcomes.map { it.objective }.toSet()
        )
        assertTrue(result.outcomes.all { !it.guaranteed })
        assertFalse(result.outcomes.any { it.summary.isBlank() })
    }
}

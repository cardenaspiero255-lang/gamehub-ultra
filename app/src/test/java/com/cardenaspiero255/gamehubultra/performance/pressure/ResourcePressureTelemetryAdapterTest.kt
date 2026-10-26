package com.cardenaspiero255.gamehubultra.performance.pressure

import com.cardenaspiero255.gamehubultra.platform.BatteryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.MemoryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.StorageTelemetry
import com.cardenaspiero255.gamehubultra.platform.ThermalTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CAR-52: prove Home telemetry mapping and previously untested diagnostic paths. */
class ResourcePressureTelemetryAdapterTest {
    private fun diagnostics(
        freeRamMb: Long,
        freeStorageBytes: Long = 500,
        totalRamMb: Long = 1000,
        totalStorageBytes: Long = 1000
    ) = RuntimeDiagnostics(
        thermal = ThermalTelemetry(null, null),
        battery = BatteryRuntimeTelemetry(null, false, false),
        refresh = RefreshTelemetry(emptySet(), null),
        connectivity = ConnectivityTelemetry(null, false, false, true, null, null, null),
        storage = StorageTelemetry(freeStorageBytes, totalStorageBytes),
        memory = MemoryRuntimeTelemetry(totalRamMb, freeRamMb, totalRamMb - freeRamMb),
        inputDeviceCount = 0,
        peripherals = PeripheralDiagnostics(0, 0, 0, 0)
    )

    @Test fun missingCurrentDiagnosticsCannotFabricatePressure() {
        assertNull(ResourcePressureIntelligence.assessDiagnostics(null, emptyList()))
    }

    @Test fun measuredRuntimeMemoryAndStorageMapToActualPercentages() {
        val report = ResourcePressureIntelligence.assessDiagnostics(
            diagnostics(freeRamMb = 120, freeStorageBytes = 30),
            emptyList()
        )!!
        assertEquals(12, report.availableRamPercent)
        assertEquals(3, report.freeStoragePercent)
        assertEquals(PressureLevel.ELEVATED, report.ramPressure)
        assertEquals(PressureLevel.CRITICAL, report.storagePressure)
        assertTrue(report.notes.any { "RAM" in it })
        assertTrue(report.notes.any { "Almacenamiento" in it })
    }

    @Test fun diagnosticsHistoryOmitsCurrentDuplicateBeforeCalculatingTrend() {
        val current = diagnostics(freeRamMb = 530)
        val report = ResourcePressureIntelligence.assessDiagnostics(
            current,
            listOf(diagnostics(900), diagnostics(760), current)
        )!!
        assertEquals(PressureTrend.INCREASING, report.ramTrend)
        assertTrue(report.notes.any { "disminuyendo" in it })
    }

    @Test fun runtimeInvalidCountersRemainUnknownInsteadOfNormal() {
        val report = ResourcePressureIntelligence.assessDiagnostics(
            diagnostics(freeRamMb = 1100, freeStorageBytes = 1200),
            emptyList()
        )!!
        assertEquals(PressureLevel.UNKNOWN, report.ramPressure)
        assertEquals(PressureLevel.UNKNOWN, report.storagePressure)
        assertNull(report.availableRamPercent)
        assertNull(report.freeStoragePercent)
    }

    @Test fun lowMemoryAndroidFlagKeepsUnknownMeasurementDistinctFromNormal() {
        val report = ResourcePressureIntelligence.assess(
            ResourcePressureSample(0, -1, 100, 50, androidLowMemory = true)
        )
        assertEquals(PressureLevel.ELEVATED, report.ramPressure)
        assertNull(report.availableRamPercent)
        assertTrue(report.notes.any { "Android" in it })
    }

    @Test fun storageElevatedAdvisoryDoesNotPretendToClearSpace() {
        val report = ResourcePressureIntelligence.assess(
            ResourcePressureSample(1000, 500, 1000, 120)
        )
        assertEquals(PressureLevel.ELEVATED, report.storagePressure)
        assertTrue(report.notes.any { "revisa el espacio" in it })
        assertEquals(false, report.claimsMemoryReclaimed)
    }

    @Test fun invalidHistoryReadingsCannotManufactureATrend() {
        val report = ResourcePressureIntelligence.assess(
            ResourcePressureSample(1000, 750, 1000, 500),
            listOf(
                ResourcePressureSample(1000, 1300, 1000, 500),
                ResourcePressureSample(1000, -1, 1000, 500)
            )
        )
        assertEquals(PressureTrend.INSUFFICIENT_HISTORY, report.ramTrend)
    }
}

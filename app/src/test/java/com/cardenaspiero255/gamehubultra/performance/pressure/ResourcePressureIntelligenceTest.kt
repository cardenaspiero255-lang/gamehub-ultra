package com.cardenaspiero255.gamehubultra.performance.pressure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourcePressureIntelligenceTest {
    private fun sample(
        totalRam: Long = 8192,
        freeRam: Long = 4096,
        totalDisk: Long = 100_000,
        freeDisk: Long = 50_000,
        lowMemory: Boolean? = null
    ) = ResourcePressureSample(totalRam, freeRam, totalDisk, freeDisk, lowMemory)

    @Test fun healthyDeviceReportsMeasuredNormalPressure() {
        val report = ResourcePressureIntelligence.assess(sample())
        assertEquals(PressureLevel.NORMAL, report.ramPressure)
        assertEquals(PressureLevel.NORMAL, report.storagePressure)
        assertEquals(50, report.availableRamPercent)
        assertEquals(50, report.freeStoragePercent)
        assertEquals(PressureTrend.INSUFFICIENT_HISTORY, report.ramTrend)
        assertFalse(report.claimsMemoryReclaimed)
    }

    @Test fun unknownTotalsMustNeverBeDescribedAsHealthy() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 0, totalDisk = 0))
        assertEquals(PressureLevel.UNKNOWN, report.ramPressure)
        assertEquals(PressureLevel.UNKNOWN, report.storagePressure)
        assertNull(report.availableRamPercent)
        assertNull(report.freeStoragePercent)
    }

    @Test fun invalidAvailableValuesFailClosed() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 10, freeRam = 11, totalDisk = 10, freeDisk = -1))
        assertEquals(PressureLevel.UNKNOWN, report.ramPressure)
        assertEquals(PressureLevel.UNKNOWN, report.storagePressure)
    }

    @Test fun elevatedResourcePressureIsDistinguishedFromCritical() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 1000, freeRam = 190, totalDisk = 1000, freeDisk = 140))
        assertEquals(PressureLevel.ELEVATED, report.ramPressure)
        assertEquals(PressureLevel.ELEVATED, report.storagePressure)
    }

    @Test fun criticalLevelsUseOnlyObservedFreeHeadroom() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 1000, freeRam = 99, totalDisk = 1000, freeDisk = 49))
        assertEquals(PressureLevel.CRITICAL, report.ramPressure)
        assertEquals(PressureLevel.CRITICAL, report.storagePressure)
    }

    @Test fun androidLowMemoryFlagElevatesEvenWhenRamAppearsFree() {
        val report = ResourcePressureIntelligence.assess(sample(lowMemory = true))
        assertEquals(PressureLevel.ELEVATED, report.ramPressure)
        assertTrue(report.notes.any { "Android" in it })
        assertFalse(report.claimsMemoryReclaimed)
    }

    @Test fun flagDoesNotOverwriteMoreSevereMeasuredPressure() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 1000, freeRam = 50, lowMemory = true))
        assertEquals(PressureLevel.CRITICAL, report.ramPressure)
    }

    @Test fun trendRequiresThreeCompatibleSamples() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 1000, freeRam = 100), history = listOf(sample(totalRam = 1000, freeRam = 900)))
        assertEquals(PressureTrend.INSUFFICIENT_HISTORY, report.ramTrend)
    }

    @Test fun fallingFreeRamTriggersIncreasingPressureTrend() {
        val report = ResourcePressureIntelligence.assess(
            sample(totalRam = 1000, freeRam = 500),
            history = listOf(sample(totalRam = 1000, freeRam = 850), sample(totalRam = 1000, freeRam = 670))
        )
        assertEquals(PressureTrend.INCREASING, report.ramTrend)
    }

    @Test fun recoveringRamIsNeverCalledAForcedCleanup() {
        val report = ResourcePressureIntelligence.assess(
            sample(totalRam = 1000, freeRam = 800),
            history = listOf(sample(totalRam = 1000, freeRam = 400), sample(totalRam = 1000, freeRam = 650))
        )
        assertEquals(PressureTrend.RECOVERING, report.ramTrend)
        assertFalse(report.claimsMemoryReclaimed)
    }

    @Test fun stableTrendDoesNotCreateFalseMemoryPressureAlert() {
        val report = ResourcePressureIntelligence.assess(
            sample(totalRam = 1000, freeRam = 500),
            history = listOf(sample(totalRam = 1000, freeRam = 520), sample(totalRam = 1000, freeRam = 510))
        )
        assertEquals(PressureTrend.STABLE, report.ramTrend)
    }

    @Test fun samplesFromAnotherRamCapacityDoNotContaminateTrend() {
        val report = ResourcePressureIntelligence.assess(
            sample(totalRam = 1000, freeRam = 300),
            history = listOf(sample(totalRam = 2000, freeRam = 1200), sample(totalRam = 2000, freeRam = 900))
        )
        assertEquals(PressureTrend.INSUFFICIENT_HISTORY, report.ramTrend)
    }

    @Test fun allHintsAreInformationalNotPrivilegedRamCleanerClaims() {
        val report = ResourcePressureIntelligence.assess(sample(totalRam = 1000, freeRam = 10, totalDisk = 1000, freeDisk = 10))
        assertTrue(report.notes.isNotEmpty())
        assertTrue(report.notes.all { "limpiador" !in it.lowercase() })
        assertFalse(report.claimsMemoryReclaimed)
    }

    @Test fun veryLargeLongValuesDoNotOverflowPercentage() {
        val half = Long.MAX_VALUE / 2
        val report = ResourcePressureIntelligence.assess(sample(totalRam = Long.MAX_VALUE, freeRam = half, totalDisk = Long.MAX_VALUE, freeDisk = half))
        assertTrue(report.availableRamPercent in 49..51)
        assertTrue(report.freeStoragePercent in 49..51)
    }
}

package com.cardenaspiero255.gamehubultra.ui.runtime

import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.AdaptiveRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.BatteryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.MemoryRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RefreshTelemetry
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.StorageTelemetry
import com.cardenaspiero255.gamehubultra.platform.ThermalTelemetry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardTelemetryControllerTest {

    @Test
    fun validUnmeteredNetworkProbesLatencyAndThrottlesRepeatedChecks() = runBlocking {
        var now = 1_000L
        var probes = 0
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = {
                probes += 1
                25L + probes
            },
            recordPerformanceEvent = {},
            nowMillis = { now }
        )

        val first = controller.sample(
            diagnostics = diagnostics(networkHandle = 11L),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )
        now = 2_000L
        val second = controller.sample(
            diagnostics = diagnostics(networkHandle = 11L),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )
        now = 31_001L
        val third = controller.sample(
            diagnostics = diagnostics(networkHandle = 11L),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )

        assertEquals(2, probes)
        assertEquals(26L, first.diagnostics.connectivity.latencyMs)
        assertEquals(26L, second.diagnostics.connectivity.latencyMs)
        assertEquals(27L, third.diagnostics.connectivity.latencyMs)
    }

    @Test
    fun meteredOrUnvalidatedNetworkClearsLatencyAndForcesFreshProbeWhenValidAgain() = runBlocking {
        var now = 1_000L
        var probes = 0
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = {
                probes += 1
                40L
            },
            recordPerformanceEvent = {},
            nowMillis = { now }
        )

        controller.sample(
            diagnostics = diagnostics(networkHandle = 9L),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )
        now = 2_000L
        val invalid = controller.sample(
            diagnostics = diagnostics(
                networkHandle = 9L,
                validated = false
            ),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )
        now = 3_000L
        controller.sample(
            diagnostics = diagnostics(networkHandle = 9L),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = false,
            performanceHintsAvailable = false
        )

        assertNull(invalid.diagnostics.connectivity.latencyMs)
        assertEquals(2, probes)
    }

    @Test
    fun thermalChangesCreateEventsOnlyForActiveSession() = runBlocking {
        var now = 10_000L
        val events = mutableListOf<PerformanceEvent>()
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = events::add,
            nowMillis = { now }
        )

        controller.sample(
            diagnostics = diagnostics(thermalStatus = 1),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )
        now = 20_000L
        val active = controller.sample(
            diagnostics = diagnostics(thermalStatus = 3),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )
        now = 30_000L
        val idle = controller.sample(
            diagnostics = diagnostics(thermalStatus = 4),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )

        val thermalEvents = events.filter { it.type == PerformanceEventType.THERMAL_CHANGED }
        assertEquals(1, thermalEvents.size)
        assertEquals("session-a", thermalEvents.single().sessionId)
        assertEquals("3", thermalEvents.single().detail)
        assertEquals(2, active.timelineSamples.size)
        assertTrue(idle.timelineSamples.isEmpty())
    }

    @Test
    fun newSessionResetsThermalBaselineAndTimelineSamples() = runBlocking {
        var now = 10_000L
        val events = mutableListOf<PerformanceEvent>()
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = events::add,
            nowMillis = { now }
        )

        val first = controller.sample(
            diagnostics = diagnostics(thermalStatus = 1),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )
        now = 20_000L
        val second = controller.sample(
            diagnostics = diagnostics(thermalStatus = 4),
            activeGamePackage = "game.b",
            sessionId = "session-b",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )

        assertEquals(1, first.timelineSamples.size)
        assertEquals(1, second.timelineSamples.size)
        assertTrue(
            events.none {
                it.type == PerformanceEventType.THERMAL_CHANGED &&
                    it.sessionId == "session-b"
            }
        )
    }

    @Test
    fun changedAdaptiveDecisionIsRecordedWithSessionAndSnapshotUsesRealSessionState() = runBlocking {
        val events = mutableListOf<PerformanceEvent>()
        var adaptiveSnapshot: AdaptiveRuntimeSnapshot? = null
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { snapshot ->
                adaptiveSnapshot = snapshot
                AdaptiveDecision(
                    profile = PerformanceProfile.X4,
                    enableSustainedPerformance = true,
                    score = 77,
                    reason = "thermal-safe",
                    changed = true
                )
            },
            latencyProbe = { null },
            recordPerformanceEvent = events::add,
            nowMillis = { 44_000L }
        )

        val update = controller.sample(
            diagnostics = diagnostics(
                thermalStatus = 1,
                thermalHeadroom = 0.2f,
                batteryPercent = 88
            ),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = false
        )

        assertEquals(PerformanceProfile.X4, update.adaptiveDecision.profile)
        assertNotNull(adaptiveSnapshot)
        assertTrue(adaptiveSnapshot.sessionActive)
        assertTrue(adaptiveSnapshot.sustainedPerformanceSupported)
        assertFalse(adaptiveSnapshot.performanceHintsAvailable)

        val policy = events.single { it.type == PerformanceEventType.POLICY_CHANGED }
        assertEquals("session-a", policy.sessionId)
        assertEquals(PerformanceProfile.X4, policy.profile)
        assertEquals(77, policy.score)
        assertEquals("thermal-safe", policy.detail)
    }

    @Test
    fun sessionCoachIgnoresNoiseAndReportsMeaningfulChanges() = runBlocking {
        var now = 10_000L
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = {},
            nowMillis = { now }
        )

        val first = controller.sample(
            diagnostics = diagnostics(
                thermalStatus = 1,
                thermalHeadroom = 0.30f,
                batteryPercent = 80
            ),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )
        now = 20_000L
        val noise = controller.sample(
            diagnostics = diagnostics(
                thermalStatus = 1,
                thermalHeadroom = 0.38f,
                batteryPercent = 79
            ),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )
        now = 30_000L
        val meaningful = controller.sample(
            diagnostics = diagnostics(
                thermalStatus = 3,
                thermalHeadroom = 0.84f,
                batteryPercent = 73
            ),
            activeGamePackage = "game.a",
            sessionId = "session-a",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )

        assertEquals(1, first.coachSamples.size)
        assertTrue(first.coachObservations.isEmpty())
        assertEquals(2, noise.coachSamples.size)
        assertTrue(noise.coachObservations.isEmpty())
        assertEquals(3, meaningful.coachSamples.size)
        assertTrue(meaningful.coachObservations.any { it.signal.name == "THERMAL" })
        assertTrue(meaningful.coachObservations.any { it.signal.name == "BATTERY" })
    }

    @Test
    fun completedSessionKeepsPostSessionCoachReportAndResetsLiveSamples() = runBlocking {
        var now = 10_000L
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = {},
            nowMillis = { now }
        )

        repeat(3) { index ->
            controller.sample(
                diagnostics = diagnostics(
                    thermalStatus = 3,
                    thermalHeadroom = 0.85f,
                    batteryPercent = 90 - index * 10
                ),
                activeGamePackage = "game.a",
                sessionId = "session-a",
                sustainedPerformanceSupported = true,
                performanceHintsAvailable = true
            )
            now += 10_000L
        }

        val idle = controller.sample(
            diagnostics = diagnostics(
                thermalStatus = 1,
                thermalHeadroom = 0.20f,
                batteryPercent = 69
            ),
            activeGamePackage = null,
            sessionId = null,
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )

        assertTrue(idle.coachSamples.isEmpty())
        val report = assertNotNull(idle.lastCompletedCoachReport)
        assertEquals(20, report.batteryDropPercent)
        assertTrue(report.patterns.any { it.signal.name == "THERMAL" })
        assertTrue(report.patterns.any { it.signal.name == "BATTERY" })
    }

    @Test
    fun telemetryTrendIsBoundedToTwelveSamples() = runBlocking {
        var now = 0L
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = {},
            nowMillis = { now }
        )

        var latest: DashboardTelemetryUpdate? = null
        repeat(15) { index ->
            now += 10_000L
            latest = controller.sample(
                diagnostics = diagnostics(batteryPercent = index),
                activeGamePackage = null,
                sessionId = null,
                sustainedPerformanceSupported = false,
                performanceHintsAvailable = false
            )
        }

        assertEquals(12, latest?.telemetryTrend?.size)
        assertEquals(14, latest?.telemetryTrend?.last()?.battery?.percent)
    }


    @Test
    fun dashboardCoachSnapshotPreservesChargingAndPowerSaveState() = runBlocking {
        val controller = DashboardTelemetryController(
            adaptiveEvaluator = { unchangedDecision() },
            latencyProbe = { null },
            recordPerformanceEvent = {},
            nowMillis = { 12_000L }
        )

        val update = controller.sample(
            diagnostics = diagnostics(
                batteryPercent = 12,
                charging = true,
                powerSaveMode = true
            ),
            activeGamePackage = "game.a",
            sessionId = "session-battery-state",
            sustainedPerformanceSupported = true,
            performanceHintsAvailable = true
        )

        val snapshot = update.coachSamples.single()
        assertEquals(true, snapshot.batteryCharging)
        assertEquals(true, snapshot.powerSaveMode)
    }

    private fun unchangedDecision() =
        AdaptiveDecision(
            profile = PerformanceProfile.BALANCED,
            enableSustainedPerformance = false,
            score = 50,
            reason = "stable",
            changed = false
        )

    private fun diagnostics(
        networkHandle: Long? = 1L,
        connected: Boolean = true,
        validated: Boolean = true,
        metered: Boolean = false,
        thermalStatus: Int? = 1,
        thermalHeadroom: Float? = 0.2f,
        batteryPercent: Int? = 80,
        charging: Boolean = false,
        powerSaveMode: Boolean = false
    ) = RuntimeDiagnostics(
        thermal = ThermalTelemetry(
            status = thermalStatus,
            headroom = thermalHeadroom
        ),
        battery = BatteryRuntimeTelemetry(
            percent = batteryPercent,
            charging = charging,
            powerSaveMode = powerSaveMode
        ),
        refresh = RefreshTelemetry(
            supportedRefreshRatesHz = setOf(60, 120),
            currentRefreshRateHz = 120f
        ),
        connectivity = ConnectivityTelemetry(
            networkHandle = networkHandle,
            connected = connected,
            validated = validated,
            metered = metered,
            transport = "wifi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = null
        ),
        storage = StorageTelemetry(
            freeBytes = 10L,
            totalBytes = 20L
        ),
        memory = MemoryRuntimeTelemetry(
            totalRamMb = 1_000L,
            availableRamMb = 400L,
            usedRamMb = 600L
        ),
        inputDeviceCount = 0,
        peripherals = PeripheralDiagnostics(
            gamepadCount = 0,
            keyboardCount = 0,
            mouseCount = 0,
            externalAudioCount = 0
        )
    )
}

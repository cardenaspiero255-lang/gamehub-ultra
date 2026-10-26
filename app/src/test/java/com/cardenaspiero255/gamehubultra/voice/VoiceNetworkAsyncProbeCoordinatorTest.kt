package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** CAR-51: network probes must not block an Android caller or reuse stale network data. */
class VoiceNetworkAsyncProbeCoordinatorTest {
    private fun telemetry(handle: Long, transport: String = "Wi-Fi") =
        ConnectivityTelemetry(
            networkHandle = handle,
            connected = true,
            validated = true,
            metered = false,
            transport = transport,
            downstreamBandwidthKbps = 100_000,
            latencyMs = null
        )

    @Test fun firstVoiceReplyReturnsUnmeasuredImmediatelyWhileProbeIsScheduled() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        var scheduleCount = 0
        var completion: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
        val initial = assertNotNull(session.capture(telemetry(1), 2_000L) { handle, done ->
            assertEquals(1L, handle)
            scheduleCount++
            completion = done
        })
        assertEquals(NetworkStability.UNMEASURED, initial.metrics.stability)
        assertEquals(1, scheduleCount)

        session.capture(telemetry(1), 2_100L) { _, _ -> scheduleCount++ }
        assertEquals(1, scheduleCount, "Never create competing probes for the same network")

        assertNotNull(completion).invoke(36L, telemetry(1), 2_200L)
        val updated = assertNotNull(session.capture(telemetry(1), 2_250L) { _, _ ->
            error("Probe interval has not elapsed")
        })
        assertEquals(listOf(36L), assertNotNull(updated.diagnostics).latencyHistoryMs)
        assertFalse(assertNotNull(updated.diagnostics).packetLossMeasured)
    }

    @Test fun staleWifiProbeCannotBeAssignedToNewMobileConnection() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        var wifiDone: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
        session.capture(telemetry(10), 1_000L) { _, done -> wifiDone = done }
        val mobile = telemetry(11, "Móvil")
        val switched = assertNotNull(session.capture(mobile, 2_000L) { _, _ -> })
        assertEquals(NetworkStability.UNMEASURED, switched.metrics.stability)
        assertEquals(1, assertNotNull(switched.diagnostics).networkHandleChangeCount)
        assertNotNull(wifiDone).invoke(15L, telemetry(10), 2_100L)
        val next = assertNotNull(session.capture(mobile, 2_200L) { _, _ -> })
        assertTrue(assertNotNull(next.diagnostics).latencyHistoryMs.isEmpty())
    }

    @Test fun disconnectedProbeCompletionCannotResurrectPreviousFastNetwork() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        val connected = telemetry(10)
        var done: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
        session.capture(connected, 2_000L) { _, result -> done = result }
        val offline = connected.copy(connected = false, validated = false)
        val snapshot = assertNotNull(session.capture(offline, 2_100L) { _, _ ->
            error("Offline device must not start probes")
        })
        assertEquals(NetworkStability.OFFLINE, snapshot.metrics.stability)
        assertNotNull(done).invoke(15L, connected, 2_200L)
        assertEquals(NetworkStability.OFFLINE,
            assertNotNull(session.capture(offline, 2_300L) { _, _ -> }).metrics.stability)
    }

    @Test fun realMeasuredLatencyNeverFabricatesMeasuredPacketLossOrNetworkControl() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        val wifi = telemetry(42)
        listOf(40L, 50L, 45L).forEachIndexed { index, latency ->
            var done: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
            session.capture(wifi, 4_000L * (index + 1)) { _, complete -> done = complete }
            assertNotNull(done).invoke(latency, wifi, 4_000L * (index + 1) + 10L)
        }
        val observed = assertNotNull(session.capture(wifi, 12_500L) { _, _ -> })
        assertEquals(listOf(40L, 50L, 45L), assertNotNull(observed.diagnostics).latencyHistoryMs)
        assertEquals(45.0, observed.metrics.averageLatencyMs)
        assertEquals(NetworkGameProfile.BALANCED, observed.recommendedProfile)
        assertFalse(assertNotNull(observed.diagnostics).packetLossMeasured)
        assertFalse(assertNotNull(observed.diagnostics).networkControlApplied)
    }

    @Test fun failedSchedulingCanBeRetriedRatherThanStickingForever() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        val wifi = telemetry(23)
        session.capture(wifi, 2_000L) { _, _ -> throw IllegalStateException("Executor rejected") }
        var called = 0
        session.capture(wifi, 3_100L) { _, _ -> called++ }
        assertEquals(1, called)
    }

    @Test fun previousProbeCannotOverwriteNewProbeAfterValidationRecoversOnSameHandle() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        val wifi = telemetry(51)
        var obsolete: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
        var current: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null

        session.capture(wifi, 1_000L) { _, done -> obsolete = done }
        session.capture(wifi.copy(validated = false), 1_100L) { _, _ ->
            error("Unvalidated connection must not start a probe")
        }
        session.capture(wifi, 1_200L) { _, done -> current = done }
        assertNotNull(obsolete).invoke(13L, wifi, 1_250L)
        assertNotNull(current).invoke(77L, wifi, 1_300L)

        val result = assertNotNull(session.capture(wifi, 1_350L) { _, _ ->
            error("New probe already finished within the minimum interval")
        })
        assertEquals(listOf(77L), assertNotNull(result.diagnostics).latencyHistoryMs,
            "An old callback with the same network handle must not clear the new probe")
    }

    @Test fun returningToWifiHandleCannotAcceptCallbackFromPriorWifiGeneration() {
        val session = VoiceNetworkAsyncProbeCoordinator(minProbeIntervalMs = 1_000L)
        val wifi = telemetry(52)
        val mobile = telemetry(53, "Móvil")
        var oldWifiCallback: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null
        var newWifiCallback: ((Long?, ConnectivityTelemetry, Long) -> Unit)? = null

        session.capture(wifi, 1_000L) { _, done -> oldWifiCallback = done }
        session.capture(mobile, 1_100L) { _, _ -> }
        session.capture(wifi, 1_200L) { _, done -> newWifiCallback = done }
        assertNotNull(oldWifiCallback).invoke(9L, wifi, 1_250L)
        assertNotNull(newWifiCallback).invoke(91L, wifi, 1_300L)

        val result = assertNotNull(session.capture(wifi, 1_350L) { _, _ -> })
        assertEquals(listOf(91L), assertNotNull(result.diagnostics).latencyHistoryMs,
            "Wi-Fi -> mobile -> Wi-Fi must invalidate callbacks from the first Wi-Fi session")
    }

}

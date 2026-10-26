package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VoiceNetworkSnapshotSessionTest {
    private fun wifi() = ConnectivityTelemetry(
        networkHandle = 101L,
        connected = true,
        validated = true,
        metered = false,
        transport = "Wi-Fi",
        downstreamBandwidthKbps = 90_000,
        latencyMs = null
    )

    private fun mobile() = ConnectivityTelemetry(
        networkHandle = 202L,
        connected = true,
        validated = true,
        metered = true,
        transport = "Móvil",
        downstreamBandwidthKbps = 30_000,
        latencyMs = null
    )

    @Test
    fun handoffDuringProbeImmediatelyReportsMobileWithoutReusingWifiLatency() {
        val session = VoiceNetworkSnapshotSession()
        var active = wifi()

        val first = assertNotNull(session.capture(
            readConnectivity = { active },
            measureLatency = { 31L },
            timestampMs = { 1_000L }
        ))
        assertEquals(31.0, first.metrics.averageLatencyMs)

        val switched = assertNotNull(session.capture(
            readConnectivity = { active },
            measureLatency = {
                active = mobile()
                24L // Even an erroneously late probe must never be attributed to mobile.
            },
            timestampMs = { 2_000L }
        ))

        val diagnostic = assertNotNull(switched.diagnostics)
        assertEquals(1, diagnostic.networkHandleChangeCount)
        assertEquals("Wi-Fi", diagnostic.transportChanges.single().fromTransport)
        assertEquals("Móvil", diagnostic.transportChanges.single().toTransport)
        assertEquals(emptyList(), diagnostic.latencyHistoryMs)
        assertEquals(null, switched.metrics.averageLatencyMs)
        assertEquals(NetworkStability.UNMEASURED, switched.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, switched.recommendedProfile)
        assertEquals(true, switched.metered)
    }

    @Test
    fun slowOldProbeCannotReplaceNewerNetworkMeasurement() {
        val session = VoiceNetworkSnapshotSession()
        var active = wifi()
        session.capture(
            readConnectivity = { active },
            measureLatency = { 35L },
            timestampMs = { 1_000L }
        )

        val latest = assertNotNull(session.capture(
            readConnectivity = { active },
            measureLatency = {
                active = mobile()
                val nested = assertNotNull(session.capture(
                    readConnectivity = { active },
                    measureLatency = { 82L },
                    timestampMs = { 2_000L }
                ))
                assertEquals(82.0, nested.metrics.averageLatencyMs)
                28L
            },
            timestampMs = { 3_000L }
        ))

        assertEquals(listOf(82L), assertNotNull(latest.diagnostics).latencyHistoryMs)
        assertEquals(82.0, latest.metrics.averageLatencyMs)
        assertEquals(1, assertNotNull(latest.diagnostics).networkHandleChangeCount)
    }

    @Test
    fun disconnectDuringProbeClearsAllPriorLatency() {
        val session = VoiceNetworkSnapshotSession()
        var active = wifi()
        session.capture(
            readConnectivity = { active },
            measureLatency = { 40L },
            timestampMs = { 1_000L }
        )
        val offline = assertNotNull(session.capture(
            readConnectivity = { active },
            measureLatency = {
                active = wifi().copy(
                    networkHandle = null,
                    connected = false,
                    validated = false,
                    transport = null
                )
                40L
            },
            timestampMs = { 2_000L }
        ))
        assertEquals(NetworkStability.OFFLINE, offline.metrics.stability)
        assertEquals(null, offline.metrics.averageLatencyMs)
        assertEquals(NetworkGameProfile.BALANCED, offline.recommendedProfile)
    }

    @Test
    fun connectionAppearingAfterInitialReadIsReportedAsUnmeasured() {
        val session = VoiceNetworkSnapshotSession()
        var reads = 0
        var probeCalls = 0
        val result = assertNotNull(session.capture(
            readConnectivity = {
                reads++
                if (reads == 1) wifi().copy(connected = false, validated = false)
                else mobile()
            },
            measureLatency = {
                probeCalls++
                30L
            },
            timestampMs = { 1_000L }
        ))
        assertEquals(0, probeCalls)
        assertEquals(NetworkStability.UNMEASURED, result.metrics.stability)
        assertEquals(null, result.metrics.averageLatencyMs)
        assertEquals(true, result.metered)
    }

    @Test
    fun stableValidatedNetworkStillUsesGenuineProbeMeasurement() {
        val session = VoiceNetworkSnapshotSession()
        val result = assertNotNull(session.capture(
            readConnectivity = { wifi() },
            measureLatency = { 27L },
            timestampMs = { 1_000L }
        ))
        assertEquals(27.0, result.metrics.averageLatencyMs)
        assertEquals(listOf(27L), assertNotNull(result.diagnostics).latencyHistoryMs)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
    }
}

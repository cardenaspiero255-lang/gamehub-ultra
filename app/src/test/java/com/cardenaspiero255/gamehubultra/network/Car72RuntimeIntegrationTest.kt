package com.cardenaspiero255.gamehubultra.network

import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RouterDiscoveryParser
import com.cardenaspiero255.gamehubultra.voice.VoiceNetworkSnapshotFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Car72RuntimeIntegrationTest {

    @Test
    fun snapshotFactoryUsesMeasuredRuntimeTelemetry() {
        val snapshot = VoiceNetworkSnapshotFactory.from(
            listOf(
                ConnectivityTelemetry(
                    networkHandle = 7L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = 32L
                ),
                ConnectivityTelemetry(
                    networkHandle = 7L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = 36L
                ),
                ConnectivityTelemetry(
                    networkHandle = 7L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = 34L
                )
            )
        )

        assertNotNull(snapshot)
        assertEquals(34.0, snapshot.metrics.averageLatencyMs!!, 0.01)
        assertEquals(NetworkGameProfile.COMPETITIVE, snapshot.recommendedProfile)
        assertFalse(snapshot.metered)
    }

    @Test
    fun snapshotFactoryDoesNotInventStatusWithoutMeasuredLatency() {
        val snapshot = VoiceNetworkSnapshotFactory.from(
            listOf(
                ConnectivityTelemetry(
                    networkHandle = 9L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = null
                )
            )
        )

        assertNull(snapshot)
    }

    @Test
    fun competitiveProfileUsesLowLatencyWifiOnAndroid10Plus() {
        assertEquals(
            NetworkPriorityAction.LOW_LATENCY_WIFI,
            NetworkLocalPriorityPolicy.actionFor(
                profile = NetworkGameProfile.COMPETITIVE,
                transport = "Wi-Fi",
                connected = true,
                validated = true,
                sdkInt = 36
            )
        )
    }

    @Test
    fun competitiveProfileFallsBackToHighPerformanceWifiBeforeAndroid10() {
        assertEquals(
            NetworkPriorityAction.HIGH_PERFORMANCE_WIFI,
            NetworkLocalPriorityPolicy.actionFor(
                profile = NetworkGameProfile.COMPETITIVE,
                transport = "Wi-Fi",
                connected = true,
                validated = true,
                sdkInt = 28
            )
        )
    }

    @Test
    fun competitiveProfileDoesNotClaimPriorityOnCellular() {
        assertEquals(
            NetworkPriorityAction.UNAVAILABLE,
            NetworkLocalPriorityPolicy.actionFor(
                profile = NetworkGameProfile.COMPETITIVE,
                transport = "Móvil",
                connected = true,
                validated = true,
                sdkInt = 36
            )
        )
    }

    @Test
    fun balancedAndDataSaverReleaseAnyWifiPriorityLock() {
        for (profile in listOf(NetworkGameProfile.BALANCED, NetworkGameProfile.DATA_SAVER)) {
            assertEquals(
                NetworkPriorityAction.RELEASE_WIFI_LOCK,
                NetworkLocalPriorityPolicy.actionFor(
                    profile = profile,
                    transport = "Wi-Fi",
                    connected = true,
                    validated = true,
                    sdkInt = 36
                )
            )
        }
    }

    @Test
    fun ssdpParserExtractsOnlyGatewayRouterDescription() {
        val response = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.1:1900/rootDesc.xml
            SERVER: FiberHome/1.0 UPnP/1.1
            ST: upnp:rootdevice
        """.trimIndent()

        val parsed = RouterDiscoveryParser.parseSsdpResponse(response)

        assertNotNull(parsed)
        assertEquals("http://192.168.1.1:1900/rootDesc.xml", parsed.location)
        assertTrue(RouterDiscoveryParser.locationMatchesGateway(parsed.location, "192.168.1.1"))
        assertFalse(RouterDiscoveryParser.locationMatchesGateway(parsed.location, "192.168.1.2"))
    }

    @Test
    fun routerDescriptionParserRecognizesFiberHomeHg5853sf() {
        val xml = """
            <root>
              <device>
                <manufacturer>FiberHome</manufacturer>
                <modelName>HG5853SF</modelName>
              </device>
            </root>
        """.trimIndent()

        val identity = RouterDiscoveryParser.parseDeviceDescription(xml)

        assertEquals("FiberHome", identity.manufacturer)
        assertEquals("HG5853SF", identity.model)
    }
}

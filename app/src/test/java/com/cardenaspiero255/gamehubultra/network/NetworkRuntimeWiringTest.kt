package com.cardenaspiero255.gamehubultra.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkRuntimeWiringTest {

    @Test
    fun connectedValidatedNetworkWithoutLatencyIsUnmeasuredNotOffline() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(
                    latencyMs = null,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi"
                )
            )
        )

        assertEquals(NetworkStability.UNMEASURED, metrics.stability)
        assertNull(metrics.averageLatencyMs)
    }

    @Test
    fun competitiveWifiOnAndroid10RequestsLowLatencyMode() {
        val action = LocalNetworkPriorityPolicy.decide(
            profile = NetworkGameProfile.COMPETITIVE,
            transport = "Wi-Fi",
            sdkInt = 29
        )

        assertTrue(action.acquireWifiLowLatencyLock)
    }

    @Test
    fun localPriorityNeverClaimsWifiLowLatencyOnUnsupportedPaths() {
        assertFalse(
            LocalNetworkPriorityPolicy.decide(
                profile = NetworkGameProfile.COMPETITIVE,
                transport = "Móvil",
                sdkInt = 36
            ).acquireWifiLowLatencyLock
        )
        assertFalse(
            LocalNetworkPriorityPolicy.decide(
                profile = NetworkGameProfile.COMPETITIVE,
                transport = "Wi-Fi",
                sdkInt = 28
            ).acquireWifiLowLatencyLock
        )
        assertFalse(
            LocalNetworkPriorityPolicy.decide(
                profile = NetworkGameProfile.BALANCED,
                transport = "Wi-Fi",
                sdkInt = 36
            ).acquireWifiLowLatencyLock
        )
    }

    @Test
    fun ssdpParserRecognizesKnownFiberHomeModelWhenAdvertised() {
        val parsed = RouterSsdpParser.parse(
            """
            HTTP/1.1 200 OK
            SERVER: FiberHome/1.0 UPnP/1.1 HG5853SF
            USN: uuid:router-hg5853sf::upnp:rootdevice
            ST: upnp:rootdevice
            """.trimIndent()
        )

        assertEquals("FiberHome", parsed.manufacturer)
        assertEquals("HG5853SF", parsed.model)
        assertTrue(parsed.upnpVisible)
    }

    @Test
    fun ssdpParserDoesNotGuessExactModelFromGenericFiberHomeResponse() {
        val parsed = RouterSsdpParser.parse(
            """
            HTTP/1.1 200 OK
            SERVER: FiberHome/1.0 UPnP/1.1
            ST: upnp:rootdevice
            """.trimIndent()
        )

        assertEquals("FiberHome", parsed.manufacturer)
        assertNull(parsed.model)
        assertTrue(parsed.upnpVisible)
    }
}

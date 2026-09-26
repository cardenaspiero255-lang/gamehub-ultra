package com.cardenaspiero255.gamehubultra.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGameBoosterTest {

    @Test
    fun metricsCalculator_computesAverageJitterAndSpikes() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = 40),
                NetworkSample(latencyMs = 42),
                NetworkSample(latencyMs = 41),
                NetworkSample(latencyMs = 160)
            )
        )

        assertEquals(70.75, metrics.averageLatencyMs!!, 0.01)
        assertEquals(40.33, metrics.jitterMs!!, 0.02)
        assertEquals(1, metrics.spikeCount)
    }

    @Test
    fun metricsCalculator_usesReliablePacketLossMeasurementsOnly() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = 45, packetLossPercent = null),
                NetworkSample(latencyMs = 44, packetLossPercent = 2.0),
                NetworkSample(latencyMs = 46, packetLossPercent = 4.0)
            )
        )

        assertEquals(3.0, metrics.packetLossPercent!!, 0.01)
    }

    @Test
    fun metricsCalculator_returnsOfflineWithoutValidatedConnectivity() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = null, connected = false, validated = false),
                NetworkSample(latencyMs = null, connected = true, validated = false)
            )
        )

        assertEquals(NetworkStability.OFFLINE, metrics.stability)
        assertNull(metrics.averageLatencyMs)
        assertNull(metrics.jitterMs)
    }

    @Test
    fun stabilityClassifier_marksStableLowLatencySessionExcellent() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = 28, packetLossPercent = 0.0),
                NetworkSample(latencyMs = 31, packetLossPercent = 0.0),
                NetworkSample(latencyMs = 29, packetLossPercent = 0.0),
                NetworkSample(latencyMs = 30, packetLossPercent = 0.0)
            )
        )

        assertEquals(NetworkStability.EXCELLENT, metrics.stability)
    }

    @Test
    fun stabilityClassifier_marksRepeatedSpikesPoor() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = 45),
                NetworkSample(latencyMs = 170),
                NetworkSample(latencyMs = 48),
                NetworkSample(latencyMs = 190)
            )
        )

        assertEquals(NetworkStability.POOR, metrics.stability)
        assertEquals(2, metrics.spikeCount)
    }

    @Test
    fun profilePolicy_prefersCompetitiveOnGoodUnmeteredNetwork() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(
                NetworkSample(latencyMs = 35, packetLossPercent = 0.0),
                NetworkSample(latencyMs = 38, packetLossPercent = 0.0),
                NetworkSample(latencyMs = 36, packetLossPercent = 0.0)
            )
        )

        assertEquals(
            NetworkGameProfile.COMPETITIVE,
            NetworkProfilePolicy.recommend(metrics = metrics, metered = false)
        )
    }

    @Test
    fun profilePolicy_prefersDataSaverOnMeteredConnection() {
        val metrics = NetworkMetricsCalculator.calculate(
            listOf(NetworkSample(latencyMs = 40, packetLossPercent = 0.0, metered = true))
        )

        assertEquals(
            NetworkGameProfile.DATA_SAVER,
            NetworkProfilePolicy.recommend(metrics = metrics, metered = true)
        )
    }

    @Test
    fun dnsPolicy_selectsReliableResolverWithLowestStableLatency() {
        val selected = DnsBenchmarkPolicy.selectBest(
            listOf(
                DnsProbeResult("dns-a", latenciesMs = listOf(18, 19, 18, 20), attempts = 4),
                DnsProbeResult("dns-b", latenciesMs = listOf(10, 50, 11, 49), attempts = 4),
                DnsProbeResult("dns-c", latenciesMs = listOf(8), attempts = 4)
            )
        )

        assertEquals("dns-a", selected?.resolver)
    }

    @Test
    fun dnsPolicy_returnsNullWhenNoResolverIsReliable() {
        val selected = DnsBenchmarkPolicy.selectBest(
            listOf(
                DnsProbeResult("dns-a", latenciesMs = listOf(12), attempts = 4),
                DnsProbeResult("dns-b", latenciesMs = emptyList(), attempts = 4)
            )
        )

        assertNull(selected)
    }

    @Test
    fun bufferbloatClassifier_usesLatencyIncreaseUnderLoad() {
        assertEquals(
            BufferbloatRating.EXCELLENT,
            BufferbloatCalculator.classify(idleLatencyMs = 30, loadedLatencyMs = 40)
        )
        assertEquals(
            BufferbloatRating.POOR,
            BufferbloatCalculator.classify(idleLatencyMs = 30, loadedLatencyMs = 150)
        )
    }

    @Test
    fun capabilityPolicy_neverClaimsImpossibleNetworkControl() {
        val capabilities = NetworkCapabilityPolicy.evaluate(
            vpnConsentGranted = false,
            routerQosIntegrationAvailable = false
        )

        assertFalse(capabilities.canUseVpnTunnel)
        assertFalse(capabilities.canPrioritizeOtherApps)
        assertFalse(capabilities.canChangeIspRouting)
        assertFalse(capabilities.canExceedIspBandwidth)
        assertFalse(capabilities.canUseRouterQos)
    }

    @Test
    fun capabilityPolicy_exposesOnlyExplicitlyAvailableFeatures() {
        val capabilities = NetworkCapabilityPolicy.evaluate(
            vpnConsentGranted = true,
            routerQosIntegrationAvailable = true
        )

        assertTrue(capabilities.canUseVpnTunnel)
        assertTrue(capabilities.canUseRouterQos)
        assertFalse(capabilities.canPrioritizeOtherApps)
        assertFalse(capabilities.canChangeIspRouting)
        assertFalse(capabilities.canExceedIspBandwidth)
    }
}

package com.cardenaspiero255.gamehubultra.network.diagnostics

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkGamingDiagnosticsEngineTest {
    private val policy = NetworkGamingDiagnosticsPolicy()
    private val engine = NetworkGamingDiagnosticsEngine(policy)

    @Test
    fun latencyIsUsedOnlyWhenExplicitlyMeasured() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 40L, latencyMeasured = false),
                sample(1_000L, latencyMs = 50L, latencyMeasured = false),
                sample(2_000L, latencyMs = 60L, latencyMeasured = false)
            )
        )

        assertTrue(result.latencyHistoryMs.isEmpty())
        assertNull(result.metrics.averageLatencyMs)
        assertNull(result.metrics.jitterMs)
        assertEquals(NetworkStability.UNMEASURED, result.metrics.stability)
    }

    @Test
    fun measuredLatencyBuildsHistoryAndJitter() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 40L, latencyMeasured = true),
                sample(1_000L, latencyMs = 50L, latencyMeasured = true),
                sample(2_000L, latencyMs = 45L, latencyMeasured = true),
                sample(3_000L, latencyMs = 55L, latencyMeasured = true)
            )
        )

        assertEquals(listOf(40L, 50L, 45L, 55L), result.latencyHistoryMs)
        assertEquals(47.5, result.metrics.averageLatencyMs)
        assertTrue((result.metrics.jitterMs ?: 0.0) > 0.0)
        assertEquals(NetworkStability.EXCELLENT, result.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
        assertFalse(result.competitiveRecommended)
    }

    @Test
    fun packetLossIsNotClaimedUnlessItWasActuallyMeasured() {
        val unmeasured = engine.analyze(
            listOf(
                sample(
                    0L,
                    latencyMs = 40L,
                    latencyMeasured = true,
                    packetLossPercent = 8.0,
                    packetLossMeasured = false
                ),
                sample(1_000L, latencyMs = 45L, latencyMeasured = true),
                sample(2_000L, latencyMs = 43L, latencyMeasured = true)
            )
        )

        assertFalse(unmeasured.packetLossMeasured)
        assertNull(unmeasured.metrics.packetLossPercent)

        val measured = engine.analyze(
            listOf(
                sample(
                    0L,
                    latencyMs = 40L,
                    latencyMeasured = true,
                    packetLossPercent = 6.0,
                    packetLossMeasured = true
                ),
                sample(
                    1_000L,
                    latencyMs = 45L,
                    latencyMeasured = true,
                    packetLossPercent = 4.0,
                    packetLossMeasured = true
                ),
                sample(2_000L, latencyMs = 43L, latencyMeasured = true)
            )
        )

        assertTrue(measured.packetLossMeasured)
        assertEquals(5.0, measured.metrics.packetLossPercent)
        assertEquals(NetworkStability.POOR, measured.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, measured.recommendedProfile)
        assertFalse(measured.competitiveRecommended)
    }

    @Test
    fun transportChangesAreReportedDuringTheSession() {
        val result = engine.analyze(
            listOf(
                sample(0L, transport = "Wi‑Fi", networkHandle = 1L),
                sample(1_000L, transport = "Wi‑Fi", networkHandle = 1L),
                sample(2_000L, transport = "Móvil", networkHandle = 2L),
                sample(3_000L, transport = "Wi‑Fi", networkHandle = 3L)
            )
        )

        assertEquals(2, result.transportChanges.size)
        assertEquals("Wi‑Fi", result.transportChanges[0].fromTransport)
        assertEquals("Móvil", result.transportChanges[0].toTransport)
        assertEquals(2, result.networkHandleChangeCount)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
        assertFalse(result.competitiveRecommended)
    }

    @Test
    fun largeLatencySpikesAreDetectedAgainstMedianBaseline() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 40L, latencyMeasured = true),
                sample(1_000L, latencyMs = 42L, latencyMeasured = true),
                sample(2_000L, latencyMs = 41L, latencyMeasured = true),
                sample(3_000L, latencyMs = 180L, latencyMeasured = true),
                sample(4_000L, latencyMs = 43L, latencyMeasured = true)
            )
        )

        assertEquals(1, result.metrics.spikeCount)
        assertEquals(NetworkStability.FAIR, result.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
    }

    @Test
    fun oldSamplesOutsideHistoryWindowAreIgnored() {
        val shortPolicy = policy.copy(historyWindowMs = 2_000L)
        val shortEngine = NetworkGamingDiagnosticsEngine(shortPolicy)

        val result = shortEngine.analyze(
            listOf(
                sample(0L, latencyMs = 400L, latencyMeasured = true),
                sample(8_000L, latencyMs = 40L, latencyMeasured = true),
                sample(9_000L, latencyMs = 42L, latencyMeasured = true),
                sample(10_000L, latencyMs = 41L, latencyMeasured = true)
            )
        )

        assertEquals(listOf(40L, 42L, 41L), result.latencyHistoryMs)
        assertTrue((result.metrics.averageLatencyMs ?: 999.0) < 50.0)
    }

    @Test
    fun meteredConnectionUsesDataSaverRecommendationWithoutPretendingToControlNetwork() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 35L, latencyMeasured = true, metered = true),
                sample(1_000L, latencyMs = 36L, latencyMeasured = true, metered = true),
                sample(2_000L, latencyMs = 34L, latencyMeasured = true, metered = true)
            )
        )

        assertEquals(NetworkGameProfile.DATA_SAVER, result.recommendedProfile)
        assertFalse(result.competitiveRecommended)
        assertFalse(result.networkControlApplied)
    }

    @Test
    fun invalidMeasurementsAreIgnoredFailClosed() {
        val result = engine.analyze(
            listOf(
                sample(-1L, latencyMs = 20L, latencyMeasured = true),
                sample(0L, latencyMs = 0L, latencyMeasured = true),
                sample(1_000L, latencyMs = -5L, latencyMeasured = true),
                sample(
                    2_000L,
                    latencyMs = 30L,
                    latencyMeasured = true,
                    packetLossPercent = Double.NaN,
                    packetLossMeasured = true
                )
            )
        )

        assertEquals(listOf(30L), result.latencyHistoryMs)
        assertNull(result.metrics.packetLossPercent)
        assertFalse(result.packetLossMeasured)
    }

    @Test
    fun adapterRequiresExplicitMeasurementProvenance() {
        val adapter = NetworkGamingDiagnosticsAdapter(policy)
        val telemetry = ConnectivityTelemetry(
            networkHandle = 7L,
            connected = true,
            validated = true,
            metered = false,
            transport = "Wi‑Fi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = 33L
        )

        val unmeasured = adapter.fromTelemetry(
            telemetry = telemetry,
            timestampMs = 10L,
            latencyMeasured = false
        )
        val measured = adapter.fromTelemetry(
            telemetry = telemetry,
            timestampMs = 10L,
            latencyMeasured = true
        )

        assertFalse(unmeasured.latencyMeasured)
        assertNull(unmeasured.latencyMs)
        assertTrue(measured.latencyMeasured)
        assertEquals(33L, measured.latencyMs)
        assertFailsWith<IllegalArgumentException> {
            adapter.fromTelemetry(
                telemetry = telemetry,
                timestampMs = -1L,
                latencyMeasured = true
            )
        }
    }

    @Test
    fun policyRejectsPhysicallyInvalidThresholds() {
        assertFailsWith<IllegalArgumentException> {
            NetworkGamingDiagnosticsPolicy(historyWindowMs = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkGamingDiagnosticsPolicy(minLatencySamples = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkGamingDiagnosticsPolicy(packetLossPoorPercent = 101.0)
        }
        assertFailsWith<IllegalArgumentException> {
            NetworkGamingDiagnosticsPolicy(spikeRelativeIncreaseRatio = -0.1)
        }
    }


    @Test
    fun latestDisconnectedSampleInvalidatesPreviouslyExcellentReadings() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 40L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(1_000L, latencyMs = 42L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(2_000L, latencyMs = 41L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(3_000L, connected = false, validated = false)
            )
        )

        assertEquals(NetworkStability.OFFLINE, result.metrics.stability)
        assertNull(result.metrics.averageLatencyMs)
        assertNull(result.metrics.packetLossPercent)
        assertTrue(result.latencyHistoryMs.isEmpty())
        assertFalse(result.competitiveRecommended)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
    }

    @Test
    fun competitiveRecommendationRequiresMeasuredPacketLoss() {
        val measuredLatencyOnly = listOf(
            sample(0L, latencyMs = 35L, latencyMeasured = true),
            sample(1_000L, latencyMs = 36L, latencyMeasured = true),
            sample(2_000L, latencyMs = 34L, latencyMeasured = true)
        )
        val incomplete = engine.analyze(measuredLatencyOnly)
        assertEquals(NetworkStability.EXCELLENT, incomplete.metrics.stability)
        assertNull(incomplete.metrics.packetLossPercent)
        assertEquals(NetworkGameProfile.BALANCED, incomplete.recommendedProfile)

        val verified = engine.analyze(
            measuredLatencyOnly.map {
                it.copy(packetLossPercent = 0.0, packetLossMeasured = true)
            }
        )
        assertEquals(NetworkGameProfile.COMPETITIVE, verified.recommendedProfile)
    }

    @Test
    fun latestUnvalidatedNetworkCannotReusePreviousGoodMeasurements() {
        val result = engine.analyze(
            listOf(
                sample(0L, latencyMs = 40L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(1_000L, latencyMs = 42L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(2_000L, latencyMs = 41L, latencyMeasured = true,
                    packetLossPercent = 0.0, packetLossMeasured = true),
                sample(3_000L, validated = false)
            )
        )
        assertEquals(NetworkStability.OFFLINE, result.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
    }

    @Test
    fun offlineMeteredNetworkStillRecommendsNoAppliedOptimization() {
        val result = engine.analyze(
            listOf(
                sample(1_000L, latencyMs = 40L, latencyMeasured = true,
                    metered = true),
                sample(2_000L, connected = false, validated = false, metered = true)
            )
        )
        assertEquals(NetworkStability.OFFLINE, result.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, result.recommendedProfile)
        assertFalse(result.networkControlApplied)
    }

    private fun sample(
        timestampMs: Long,
        latencyMs: Long? = null,
        latencyMeasured: Boolean = false,
        packetLossPercent: Double? = null,
        packetLossMeasured: Boolean = false,
        transport: String? = "Wi‑Fi",
        networkHandle: Long? = 1L,
        connected: Boolean = true,
        validated: Boolean = true,
        metered: Boolean = false
    ) = NetworkGamingDiagnosticSample(
        timestampMs = timestampMs,
        networkHandle = networkHandle,
        transport = transport,
        connected = connected,
        validated = validated,
        metered = metered,
        latencyMs = latencyMs,
        latencyMeasured = latencyMeasured,
        packetLossPercent = packetLossPercent,
        packetLossMeasured = packetLossMeasured
    )
}

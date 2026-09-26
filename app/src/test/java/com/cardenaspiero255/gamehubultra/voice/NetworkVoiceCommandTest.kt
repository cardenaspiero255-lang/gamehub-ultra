package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkVoiceCommandTest {

    @Test
    fun parsesCar72NetworkCommandsBeforeGameAliases() {
        assertEquals(
            VoiceCommand.Network(NetworkVoiceRequest.OPTIMIZE),
            VoiceCommandParser.parse(
                transcript = "Ultra, optimiza mi internet",
                knownGameAliases = setOf("optimiza mi internet")
            )
        )
        assertEquals(
            VoiceCommand.Network(NetworkVoiceRequest.STATUS),
            VoiceCommandParser.parse("Ultra, como esta mi conexion")
        )
        assertEquals(
            VoiceCommand.Network(NetworkVoiceRequest.PACKET_LOSS),
            VoiceCommandParser.parse("Ultra, tengo packet loss")
        )
    }

    @Test
    fun networkStatusUsesMeasuredSnapshot() {
        val snapshot = VoiceNetworkSnapshot(
            metrics = NetworkMetrics(
                averageLatencyMs = 42.0,
                jitterMs = 4.0,
                packetLossPercent = 1.5,
                spikeCount = 0,
                stability = NetworkStability.GOOD
            ),
            recommendedProfile = NetworkGameProfile.COMPETITIVE,
            metered = false
        )

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.Network(NetworkVoiceRequest.STATUS),
            gamesProvider = { emptyList() },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            networkStatusProvider = { snapshot }
        )

        val report = assertIs<VoiceActionResult.NetworkReport>(result)
        assertEquals(NetworkVoiceRequest.STATUS, report.request)
        assertEquals(42.0, report.snapshot.metrics.averageLatencyMs)
        assertEquals(1.5, report.snapshot.metrics.packetLossPercent)
    }

    @Test
    fun optimizeNetworkOnlyReportsAppliedWhenCallbackSucceeds() {
        val snapshot = VoiceNetworkSnapshot(
            metrics = NetworkMetrics(
                averageLatencyMs = 35.0,
                jitterMs = 2.0,
                packetLossPercent = 0.0,
                spikeCount = 0,
                stability = NetworkStability.EXCELLENT
            ),
            recommendedProfile = NetworkGameProfile.COMPETITIVE,
            metered = false
        )
        var applied: NetworkGameProfile? = null

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.Network(NetworkVoiceRequest.OPTIMIZE),
            gamesProvider = { emptyList() },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            networkStatusProvider = { snapshot },
            applyNetworkProfile = {
                applied = it
                true
            }
        )

        val report = assertIs<VoiceActionResult.NetworkReport>(result)
        assertTrue(report.optimizationApplied)
        assertEquals(snapshot.recommendedProfile, applied)
    }

    @Test
    fun unavailableNetworkMetricsAreNeverInvented() {
        val noProvider = VoiceCommandEngine.execute(
            command = VoiceCommand.Network(NetworkVoiceRequest.STATUS),
            gamesProvider = { emptyList() },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") }
        )
        assertIs<VoiceActionResult.NotAvailable>(noProvider)

        val snapshot = VoiceNetworkSnapshot(
            metrics = NetworkMetrics(
                averageLatencyMs = 60.0,
                jitterMs = 6.0,
                packetLossPercent = null,
                spikeCount = 0,
                stability = NetworkStability.GOOD
            ),
            recommendedProfile = NetworkGameProfile.BALANCED,
            metered = false
        )

        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.Network(NetworkVoiceRequest.PACKET_LOSS),
            gamesProvider = { emptyList() },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            networkStatusProvider = { snapshot }
        )

        val report = assertIs<VoiceActionResult.NetworkReport>(result)
        assertEquals(null, report.snapshot.metrics.packetLossPercent)
    }
}

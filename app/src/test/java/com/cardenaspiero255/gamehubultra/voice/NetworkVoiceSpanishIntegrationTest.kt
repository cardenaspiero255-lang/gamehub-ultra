package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.network.NetworkLockLifecyclePolicy
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationResultPolicy
import com.cardenaspiero255.gamehubultra.network.NetworkPriorityAction
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NetworkVoiceSpanishIntegrationTest {

    @Test
    fun optimizeInternetRoutesToCar72NetworkEngine() {
        assertEquals(
            VoiceCommand.Network(NetworkVoiceRequest.OPTIMIZE),
            VoiceCommandParser.parse("Ultra, optimiza mi internet")
        )

        val snapshot = VoiceNetworkSnapshot(
            metrics = NetworkMetrics(
                averageLatencyMs = 32.0,
                jitterMs = 3.0,
                packetLossPercent = 0.0,
                spikeCount = 0,
                stability = NetworkStability.EXCELLENT
            ),
            recommendedProfile = NetworkGameProfile.COMPETITIVE,
            metered = false
        )

        var appliedProfile: NetworkGameProfile? = null
        val result = VoiceCommandEngine.execute(
            command = VoiceCommand.Network(NetworkVoiceRequest.OPTIMIZE),
            gamesProvider = { emptyList() },
            launchGame = { true },
            saveSelectedGame = {},
            saveSelectedProfile = {},
            isProfileAvailable = { true },
            statusProvider = { VoiceDeviceStatus(80, "Normal") },
            networkStatusProvider = { snapshot },
            applyNetworkProfile = { profile ->
                appliedProfile = profile
                true
            }
        )

        val report = assertIs<VoiceActionResult.NetworkReport>(result)
        assertTrue(report.optimizationApplied)
        assertEquals(NetworkGameProfile.COMPETITIVE, appliedProfile)
    }

    @Test
    fun unifiedAgentRoutesInternetOptimizationToCar72Command() {
        val route = UltraUnifiedAgentRouter.route("Ultra, optimiza mi internet")
        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        assertEquals(
            VoiceCommand.Network(NetworkVoiceRequest.OPTIMIZE),
            commandRoute.command
        )
    }

    @Test
    fun onlyRealPriorityActivationIsReportedAsApplied() {
        assertTrue(
            NetworkOptimizationResultPolicy.reportsApplied(
                NetworkPriorityAction.LOW_LATENCY_WIFI
            )
        )
        assertTrue(
            NetworkOptimizationResultPolicy.reportsApplied(
                NetworkPriorityAction.HIGH_PERFORMANCE_WIFI
            )
        )
        assertFalse(
            NetworkOptimizationResultPolicy.reportsApplied(
                NetworkPriorityAction.RELEASE_WIFI_LOCK
            )
        )
        assertFalse(
            NetworkOptimizationResultPolicy.reportsApplied(
                NetworkPriorityAction.UNAVAILABLE
            )
        )
    }

    @Test
    fun networkLockReleasesWhenWifiIsLostOrUnvalidated() {
        assertFalse(
            NetworkLockLifecyclePolicy.shouldRelease(
                wifiTransport = true,
                validated = true
            )
        )
        assertTrue(
            NetworkLockLifecyclePolicy.shouldRelease(
                wifiTransport = false,
                validated = true
            )
        )
        assertTrue(
            NetworkLockLifecyclePolicy.shouldRelease(
                wifiTransport = true,
                validated = false
            )
        )
    }

    @Test
    fun networkVoiceTextUsesSpanishLabelsOnly() {
        val result = VoiceActionResult.NetworkReport(
            request = NetworkVoiceRequest.OPTIMIZE,
            snapshot = VoiceNetworkSnapshot(
                metrics = NetworkMetrics(
                    averageLatencyMs = 32.0,
                    jitterMs = 3.0,
                    packetLossPercent = 0.0,
                    spikeCount = 0,
                    stability = NetworkStability.EXCELLENT
                ),
                recommendedProfile = NetworkGameProfile.COMPETITIVE,
                metered = false
            ),
            optimizationApplied = true
        )

        val text = NetworkVoiceResponseText.format(result).lowercase()

        assertTrue(text.contains("competitivo"))
        assertTrue(text.contains("aplicado"))
        assertFalse(text.contains("competitive"))
        assertFalse(text.contains("excellent"))
        assertFalse(text.contains("balanced"))
        assertFalse(text.contains("data_saver"))
    }
}

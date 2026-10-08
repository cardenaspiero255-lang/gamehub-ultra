package com.cardenaspiero255.gamehubultra.voice

import android.speech.SpeechRecognizer
import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkMetrics
import com.cardenaspiero255.gamehubultra.network.NetworkStability
import com.cardenaspiero255.gamehubultra.network.NetworkLockLifecyclePolicy
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationResultPolicy
import com.cardenaspiero255.gamehubultra.network.NetworkPriorityAction
import com.cardenaspiero255.gamehubultra.network.NetworkLeaseGenerationPolicy
import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.RouterDiscoveryParser
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkVoiceSpanishIntegrationTest {

    @Test
    fun ultraVoicePrefersSpanishChileForSpeechAndRecognition() {
        assertEquals("es-CL", UltraSpeechLocalePolicy.PREFERRED_TAG)
        assertEquals("es", UltraSpeechLocalePolicy.FALLBACK_TAG)
    }

    @Test
    fun recognitionFallsBackToGenericSpanishWhenSpanishChileIsUnsupported() {
        assertEquals(
            "es",
            UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                currentTag = "es-CL"
            )
        )
        assertEquals(
            "es",
            UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                currentTag = "es-CL"
            )
        )
        assertEquals(
            null,
            UltraSpeechLocalePolicy.fallbackRecognitionTag(
                error = SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                currentTag = "es"
            )
        )
    }

    @Test
    fun lowLatencyLockIsOnlyReportedActiveWhileDeviceIsInteractive() {
        assertEquals(
            NetworkOptimizationOutcome.APPLIED,
            NetworkOptimizationResultPolicy.outcomeFor(
                action = NetworkPriorityAction.LOW_LATENCY_WIFI,
                leaseHeld = true,
                interactive = true,
                appForeground = true
            )
        )
        assertEquals(
            NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE,
            NetworkOptimizationResultPolicy.outcomeFor(
                action = NetworkPriorityAction.LOW_LATENCY_WIFI,
                leaseHeld = true,
                interactive = false,
                appForeground = true
            )
        )
        assertEquals(
            NetworkOptimizationOutcome.APPLIED,
            NetworkOptimizationResultPolicy.outcomeFor(
                action = NetworkPriorityAction.HIGH_PERFORMANCE_WIFI,
                leaseHeld = true,
                interactive = false,
                appForeground = false
            )
        )
    }

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
                NetworkOptimizationOutcome.APPLIED
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
    fun savedGameAliasBeatsBuiltInNetworkPhrase() {
        val command = VoiceCommandParser.parse(
            transcript = "Ultra, optimiza mi internet",
            knownGameAliases = setOf("optimiza mi internet")
        )
        assertIs<VoiceCommand.OpenGame>(command)
    }

    @Test
    fun explicitGameLaunchBeatsNetworkPhraseInsideTitle() {
        val command = VoiceCommandParser.parse("Ultra, abre Packet Loss")
        val launch = assertIs<VoiceCommand.OpenGame>(command)
        assertTrue(launch.query.contains("packet loss"))
    }

    @Test
    fun measuredNetworkSamplesDriveTheRecommendedProfile() {
        val snapshot = VoiceNetworkSnapshotFactory.from(
            listOf(
                ConnectivityTelemetry(
                    networkHandle = 7L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = 35L
                ),
                ConnectivityTelemetry(
                    networkHandle = 7L,
                    connected = true,
                    validated = true,
                    metered = false,
                    transport = "Wi-Fi",
                    downstreamBandwidthKbps = 500_000,
                    latencyMs = 41L
                )
            ),
            latencyMeasured = true
        )

        requireNotNull(snapshot)
        assertEquals(38.0, snapshot.metrics.averageLatencyMs)
        assertEquals(NetworkGameProfile.BALANCED, snapshot.recommendedProfile)
        assertFalse(assertNotNull(snapshot.diagnostics).packetLossMeasured)
    }

    @Test
    fun unmeasuredWifiDoesNotPretendToBeCompetitive() {
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

        requireNotNull(snapshot)
        assertEquals(NetworkStability.UNMEASURED, snapshot.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, snapshot.recommendedProfile)
    }


    @Test
    fun voiceStatusExposesCar51SpikeDiagnosticsWithoutInventedLoss() {
        val samples = listOf(40L, 42L, 41L, 180L, 43L).map { latency ->
            ConnectivityTelemetry(
                networkHandle = 8L,
                connected = true,
                validated = true,
                metered = false,
                transport = "Wi-Fi",
                downstreamBandwidthKbps = 100_000,
                latencyMs = latency
            )
        }
        val snapshot = assertNotNull(
            VoiceNetworkSnapshotFactory.from(samples, latencyMeasured = true)
        )
        assertEquals(1, assertNotNull(snapshot.diagnostics).metrics.spikeCount)
        assertEquals(NetworkGameProfile.BALANCED, snapshot.recommendedProfile)
        val report = VoiceActionResult.NetworkReport(
            request = NetworkVoiceRequest.STATUS,
            snapshot = snapshot,
            optimizationOutcome = NetworkOptimizationOutcome.NOT_REQUESTED
        )
        val spoken = NetworkVoiceResponseText.format(report).lowercase()
        assertTrue(spoken.contains("picos de latencia: 1"))
        assertTrue(spoken.contains("pérdida de paquetes todavía no medida"))
    }

    @Test
    fun offlineNetworkReportNeverReusesPreviouslyMeasuredLatency() {
        val connected = ConnectivityTelemetry(
            networkHandle = 9L,
            connected = true,
            validated = true,
            metered = false,
            transport = "Wi-Fi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = 25L
        )
        val offline = connected.copy(connected = false, validated = false, latencyMs = null)
        val snapshot = assertNotNull(
            VoiceNetworkSnapshotFactory.from(
                listOf(connected, connected, connected, offline),
                latencyMeasured = true
            )
        )
        assertEquals(NetworkStability.OFFLINE, snapshot.metrics.stability)
        assertEquals(null, snapshot.metrics.averageLatencyMs)
        assertEquals(NetworkGameProfile.BALANCED, snapshot.recommendedProfile)
    }

    @Test
    fun unverifiedLegacyLatencyIsNotTreatedAsMeasured() {
        val snapshot = assertNotNull(
            VoiceNetworkSnapshotFactory.from(
                listOf(
                    ConnectivityTelemetry(8L, true, true, false, "Wi-Fi", 100_000, 15L),
                    ConnectivityTelemetry(8L, true, true, false, "Wi-Fi", 100_000, 20L),
                    ConnectivityTelemetry(8L, true, true, false, "Wi-Fi", 100_000, 18L)
                )
            )
        )
        assertEquals(NetworkStability.UNMEASURED, snapshot.metrics.stability)
        assertEquals(NetworkGameProfile.BALANCED, snapshot.recommendedProfile)
    }

    @Test
    fun staleLeaseCallbackCannotReleaseNewerWifiLease() {
        assertFalse(
            NetworkLeaseGenerationPolicy.shouldRelease(
                callbackGeneration = 1L,
                activeGeneration = 2L
            )
        )
        assertTrue(
            NetworkLeaseGenerationPolicy.shouldRelease(
                callbackGeneration = 2L,
                activeGeneration = 2L
            )
        )
    }

    @Test
    fun ssdpParserRejectsNonSuccessResponses() {
        val parsed = RouterDiscoveryParser.parseSsdpResponse(
            "HTTP/1.1 404 Not Found\r\nLOCATION: http://192.0.2.1/device.xml\r\n"
        )
        assertEquals(null, parsed)
    }

    @Test
    fun releaseOnlyOptimizationIsNotReportedAsFailure() {
        val result = VoiceActionResult.NetworkReport(
            request = NetworkVoiceRequest.OPTIMIZE,
            snapshot = VoiceNetworkSnapshot(
                metrics = NetworkMetrics(
                    averageLatencyMs = 120.0,
                    jitterMs = 20.0,
                    packetLossPercent = 2.0,
                    spikeCount = 1,
                    stability = NetworkStability.FAIR
                ),
                recommendedProfile = NetworkGameProfile.BALANCED,
                metered = false
            ),
            optimizationOutcome = NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED
        )

        val text = NetworkVoiceResponseText.format(result).lowercase()
        assertFalse(text.contains("no pude aplicar"))
        assertTrue(text.contains("no fue necesario"))
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
            optimizationOutcome = NetworkOptimizationOutcome.APPLIED
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

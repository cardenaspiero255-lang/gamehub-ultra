package com.cardenaspiero255.gamehubultra.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cardenaspiero255.gamehubultra.UltraAssistantQueryRunner
import com.cardenaspiero255.gamehubultra.UltraAssistantSessionMemory
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraAssistantGateway
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android 14 acceptance contract: continuous listening must explain Android's
 * background-activity-launch restriction before offering the optional overlay
 * grant. The button must never be shown to a user who has not enabled listening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9BackgroundLaunchPermissionUiRobolectricTest {
    @get:Rule val rule = createComposeRule()

    private val gateway by lazy {
        AndroidContinuousVoiceGateway(RuntimeEnvironment.getApplication())
    }

    @After fun clearContinuousVoicePreference() {
        gateway.setEnabled(false)
    }

    private fun runtime() = UltraUiRuntimeDependencies(
        queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
        assistant = Mockito.mock(UltraAssistantGateway::class.java),
        sessionMemory = Mockito.mock(UltraAssistantSessionMemory::class.java),
        agentRouter = Mockito.mock(UltraAgentRoutingGateway::class.java),
        networkGaming = Mockito.mock(UltraNetworkGamingGateway::class.java),
        aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
        memoryPrivacy = Mockito.mock(UltraConversationMemoryStore::class.java)
    )

    private fun context() = GameHubAiContext(
        selectedGamePackage = null, sustainedPerformanceSupported = false,
        cpuCores = 8, totalRamMb = 8192, gpuAvailable = true,
        thermalStatus = null, thermalHeadroom = null, batteryPercent = 50,
        charging = false, refreshRateHz = 60f, networkValidated = false,
        networkLatencyMs = null, downstreamBandwidthKbps = null,
        storageFreePercent = 50, inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED, sessionActive = false
    )

    @Composable
    private fun LiveCard(dependencies: UltraUiRuntimeDependencies) {
        var conversation by remember { mutableStateOf(emptyList<String>()) }
        val runner = remember {
            UltraAssistantQueryRunner(
                ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                currentGamePackage = { null },
                currentConversation = { conversation },
                publishConversation = { conversation = it }
            )
        }
        VoiceAssistantCard(
            selectedProfileName = "Balanceado",
            onProfileSelected = {},
            onGameSelected = {},
            onVoiceSelectedGame = {},
            onVoiceSelectedProfile = {},
            onVoiceSelectedGameWithProfile = { _, _ -> },
            aiContext = context(),
            ultraRuntime = dependencies,
            queryRunner = runner,
            conversation = conversation,
            onConversationChanged = { conversation = it },
            assistantInputEnabled = true
        )
    }

    @Test fun enablingContinuousListeningShowsBackgroundLaunchExplanationAndOptionalPermission() {
        gateway.setEnabled(false)
        val dependencies = runtime()
        rule.setContent { LiveCard(dependencies) }

        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertDoesNotExist()

        // A service-side preference update, not a fabricated Compose state,
        // must update the same production UI observer.
        rule.runOnIdle { gateway.setEnabled(true) }

        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertExists()
        rule.onNodeWithText("Conceder «Mostrar sobre otras aplicaciones» (opcional)")
            .assertExists()
            .performScrollTo()
            .performClick()
        // The explicit button must use Android's settings contract, or show
        // the in-app fallback if this Robolectric host has no settings handler.
        // Neither path is a permission grant and neither starts a game.
    }

    @Test fun disablingContinuousListeningHidesBackgroundPermissionInstructions() {
        gateway.setEnabled(true)
        val dependencies = runtime()
        rule.setContent { LiveCard(dependencies) }
        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertExists()

        rule.runOnIdle { gateway.setEnabled(false) }

        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertDoesNotExist()
        rule.onNodeWithText("Conceder «Mostrar sobre otras aplicaciones» (opcional)")
            .assertDoesNotExist()
    }
}

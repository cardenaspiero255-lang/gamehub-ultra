package com.cardenaspiero255.gamehubultra.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
import com.cardenaspiero255.gamehubultra.voice.UltraWakeNameStore
import org.robolectric.shadows.ShadowSettings
import kotlin.test.assertEquals
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
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("ultra_wake_identity_v1", 0).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(false)
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
    private fun LiveCard(
        dependencies: UltraUiRuntimeDependencies,
        onVoiceController: ((com.cardenaspiero255.gamehubultra.voice.VoiceAssistantController) -> Unit)? = null
    ) {
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
            assistantInputEnabled = true,
            voiceControllerObserver = onVoiceController
        )
    }

    @Test fun enablingContinuousListeningShowsBackgroundLaunchExplanationAndOptionalPermission() {
        gateway.setEnabled(false)
        val dependencies = runtime()
        rule.setContent {
            // Match the production scrollable host: this button sits below
            // the default Robolectric viewport and must be reachable.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveCard(dependencies)
            }
        }

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
        rule.setContent {
            // Match the production scrollable host: this button sits below
            // the default Robolectric viewport and must be reachable.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveCard(dependencies)
            }
        }
        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertExists()

        rule.runOnIdle { gateway.setEnabled(false) }

        rule.onNodeWithText("Fuera de GameHub, Android puede impedir abrir juegos", substring = true)
            .assertDoesNotExist()
        rule.onNodeWithText("Conceder «Mostrar sobre otras aplicaciones» (opcional)")
            .assertDoesNotExist()
    }

    @Test fun typedChatRenamesAndAnswersLocallyWithoutSendingTheNameToAI() {
        val appContext = RuntimeEnvironment.getApplication()
        val identity = appContext.getSharedPreferences("ultra_wake_identity_v1", 0)
        identity.edit().clear().commit()
        val dependencies = runtime()
        rule.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveCard(dependencies)
            }
        }
        rule.onNodeWithText("Escribir").performClick()
        rule.onNodeWithTag("ultra-chat-private-draft", useUnmergedTree = true)
            .performTextInput("Ultra, desde ahora te llamarás Juana")
        rule.onNodeWithTag("ultra-chat-send").performClick()
        rule.waitUntil(15000) { UltraWakeNameStore(appContext).load() == "juana" }
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(
                "Entendido. Ahora me puedes llamar juana.", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("ultra-chat-private-draft", useUnmergedTree = true)
            .performTextInput("Juana, cómo te llamas")
        rule.onNodeWithTag("ultra-chat-send").performClick()
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(
                "Me llamo Juana, porque ese es el nombre que me pusiste.", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("juana", UltraWakeNameStore(appContext).load())
        Mockito.verifyNoInteractions(dependencies.agentRouter, dependencies.queryExecutor)
    }

    @Test fun existingOverlayPermissionExplainsDirectLaunchWithoutRequestingAgain() {
        ShadowSettings.setCanDrawOverlays(true)
        gateway.setEnabled(true)
        rule.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveCard(runtime())
            }
        }
        rule.onNodeWithText(
            "Permiso opcional de apertura desde segundo plano concedido.", substring = true
        ).assertExists()
        rule.onNodeWithText("Conceder «Mostrar sobre otras aplicaciones» (opcional)")
            .assertDoesNotExist()
    }

    /**
     * ASR listener forwarding is tested separately in
     * VoiceAssistantControllerRobolectricTest. Invoke the controller's real
     * transcript callback here to test Compose -> local identity dispatch.
     */
    private fun dispatchInAppTranscript(
        controller: com.cardenaspiero255.gamehubultra.voice.VoiceAssistantController,
        spokenText: String
    ) {
        val onTranscriptField = controller.javaClass.getDeclaredField("onTranscript")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callback = onTranscriptField.get(controller) as (String) -> Unit
        rule.runOnIdle { callback(spokenText) }
    }

    @Test fun inAppMicrophoneCallbackRenamesAndAnswersWithoutConversationalRouting() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ultra_wake_identity_v1", 0).edit().clear().commit()
        val dependencies = runtime()
        var activeController: com.cardenaspiero255.gamehubultra.voice.VoiceAssistantController? = null
        rule.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveCard(dependencies, onVoiceController = { activeController = it })
            }
        }
        dispatchInAppTranscript(
            requireNotNull(activeController), "Ultra, desde ahora te llamarás Juana"
        )
        rule.waitUntil(15000) { UltraWakeNameStore(context).load() == "juana" }
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(
                "Entendido. Ahora me puedes llamar juana.", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        dispatchInAppTranscript(
            requireNotNull(activeController), "Juana, cómo te llamas"
        )
        rule.waitUntil(15000) {
            rule.onAllNodesWithText(
                "Me llamo Juana, porque ese es el nombre que me pusiste.", substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verifyNoInteractions(dependencies.agentRouter, dependencies.queryExecutor)
    }
}

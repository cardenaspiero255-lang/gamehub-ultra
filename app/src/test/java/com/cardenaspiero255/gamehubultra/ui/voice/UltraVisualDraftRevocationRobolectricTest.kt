package com.cardenaspiero255.gamehubultra.ui.voice

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cardenaspiero255.gamehubultra.UltraAssistantQueryRunner
import com.cardenaspiero255.gamehubultra.UltraAssistantSessionMemory
import com.cardenaspiero255.gamehubultra.ai.*
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real parent-chat regression; OCR-derived drafts must be revoked outside child card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraVisualDraftRevocationRobolectricTest {
    @get:Rule val rule = createComposeRule()
    private var callback: ((Uri?) -> Unit)? = null
    private var game by mutableStateOf<String?>("game.a")

    private fun dependencies() = UltraUiRuntimeDependencies(
        queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
        assistant = Mockito.mock(UltraAssistantGateway::class.java),
        sessionMemory = Mockito.mock(UltraAssistantSessionMemory::class.java),
        agentRouter = Mockito.mock(UltraAgentRoutingGateway::class.java),
        networkGaming = Mockito.mock(UltraNetworkGamingGateway::class.java),
        aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
        memoryPrivacy = Mockito.mock(UltraConversationMemoryStore::class.java)
    )

    private fun context() = GameHubAiContext(
        selectedGamePackage = game, sustainedPerformanceSupported = false,
        cpuCores = 8, totalRamMb = 8192, gpuAvailable = true,
        thermalStatus = null, thermalHeadroom = null, batteryPercent = 50,
        charging = false, refreshRateHz = 60f, networkValidated = false,
        networkLatencyMs = null, downstreamBandwidthKbps = null,
        storageFreePercent = 50, inputDeviceCount = 0,
        selectedProfile = PerformanceProfile.BALANCED, sessionActive = false
    )

    private fun mount() {
        val runtime = dependencies()
        rule.setContent {
            var conversation by remember { mutableStateOf(emptyList<String>()) }
            val runner = remember {
                UltraAssistantQueryRunner(
                    ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                    currentGamePackage = { game },
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
                aiContext = context(), ultraRuntime = runtime, queryRunner = runner,
                conversation = conversation,
                onConversationChanged = { conversation = it },
                assistantInputEnabled = true,
                visualPickerOverride = { cb -> callback = cb },
                visualOcrOverride = { _, _, _, _ -> "ULTRA TEXTO PRIVADO" }
            )
        }
    }

    private fun prepareVisualDraft() {
        rule.onNodeWithTag("ultra-visual-select-image").performScrollTo().performClick()
        rule.runOnIdle { callback?.invoke(Uri.parse("content://media/picker/authorized.png")) }
        rule.onNodeWithTag("ultra-visual-confirm").performClick()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("ULTRA TEXTO PRIVADO").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("ultra-visual-use-text").performClick()
        rule.onNodeWithText("Ultra", substring = false).assertExists()
    }

    @Test fun externalMemoryErasureRemovesVisualDraftFromParentChat() {
        mount()
        prepareVisualDraft()
        rule.onNodeWithText("Ayúdame a interpretar", substring = true).assertExists()
        rule.runOnIdle { UltraPrivacyEpoch.invalidate() }
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("Ayúdame a interpretar", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Ayúdame a interpretar", substring = true).assertDoesNotExist()
    }

    @Test fun gameSwitchRemovesVisualDraftBeforeItCanBeSentInOtherGame() {
        mount()
        prepareVisualDraft()
        rule.runOnIdle { game = "game.b" }
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("Ayúdame a interpretar", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Ayúdame a interpretar", substring = true).assertDoesNotExist()
    }
}

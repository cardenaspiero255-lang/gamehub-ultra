package com.cardenaspiero255.gamehubultra.ui.voice

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createComposeRule
import com.cardenaspiero255.gamehubultra.UltraAssistantQueryRunner
import com.cardenaspiero255.gamehubultra.UltraAssistantSessionMemory
import com.cardenaspiero255.gamehubultra.ai.*
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UltraPhase5MemoryPrivacyUiRobolectricTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun userCanGrantConsentSelectRetentionAndDeleteHistoryWithConfirmation() {
        val store = Mockito.mock(UltraConversationMemoryStore::class.java)
        Mockito.`when`(store.isMemoryEnabled()).thenReturn(false)
        Mockito.`when`(store.conversationRetentionDays()).thenReturn(30)
        Mockito.`when`(store.setConversationRetentionDays(7)).thenReturn(true)
        val ui = dependencies(store)
        rule.setContent { DemoPrivacyCard(store, ui) }
        rule.onNodeWithText("Privacidad y memoria").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Memoria privada de Ultra").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Memoria privada de Ultra").assertExists()
        rule.onNodeWithTag("ultra-memory-consent-toggle").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Memoria activada con tu consentimiento.").fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verify(store).setMemoryEnabled(true)
        rule.onNodeWithText("7 días").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Retención de conversaciones: 7 días.").fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verify(store).setConversationRetentionDays(7)
        rule.onNodeWithText("Borrar historial del juego").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Historial del juego actual borrado.").fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verify(store).clearConversationHistory(UltraMemoryScope(userId = "local"))
        rule.onNodeWithText("Borrar toda mi memoria").performScrollTo().performClick()
        rule.onNodeWithText("Confirmar borrado de memoria").assertExists()
        rule.onNodeWithTag("ultra-erase-cancel").assertExists().performClick()
        Mockito.verify(store, Mockito.never()).deleteAllPrivateMemory()
        rule.onNodeWithText("Memoria privada de Ultra").assertExists()
        rule.onNodeWithText("Borrar toda mi memoria").performScrollTo().performClick()
        rule.onNodeWithText("Confirmar borrado de memoria").assertExists()
        rule.onNodeWithTag("ultra-erase-confirm").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Memoria cifrada eliminada y consentimiento revocado.").fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verify(store).deleteAllPrivateMemory()
    }

    @Test
    fun revocationIsDistinctFromErasureAndUserMayCloseDialog() {
        val store = Mockito.mock(UltraConversationMemoryStore::class.java)
        Mockito.`when`(store.isMemoryEnabled()).thenReturn(true)
        Mockito.`when`(store.conversationRetentionDays()).thenReturn(30)
        val ui = dependencies(store)
        rule.setContent { DemoPrivacyCard(store, ui) }
        rule.onNodeWithText("Privacidad y memoria").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Memoria privada de Ultra").fetchSemanticsNodes().isNotEmpty()
        }
        // Dialog opens only after the opted-in preference has been loaded.
        rule.onNodeWithTag("ultra-memory-consent-toggle").assertIsOn()
        rule.onNodeWithTag("ultra-memory-consent-toggle").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Memoria desactivada; las conversaciones guardadas no se consultarán.").fetchSemanticsNodes().isNotEmpty()
        }
        Mockito.verify(store).setMemoryEnabled(false)
        Mockito.verify(store, Mockito.never()).deleteAllPrivateMemory()
        rule.onNodeWithText("Cerrar").performClick()
        rule.onNodeWithText("Memoria privada de Ultra").assertDoesNotExist()
    }

    @Composable
    private fun DemoPrivacyCard(
        store: UltraConversationMemoryStore,
        runtime: UltraUiRuntimeDependencies
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
            ultraRuntime = runtime,
            queryRunner = runner,
            conversation = conversation,
            onConversationChanged = { conversation = it },
            assistantInputEnabled = true
        )
    }

    private fun dependencies(store: UltraConversationMemoryStore) = UltraUiRuntimeDependencies(
        queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
        assistant = Mockito.mock(UltraAssistantGateway::class.java),
        sessionMemory = Mockito.mock(UltraAssistantSessionMemory::class.java),
        agentRouter = Mockito.mock(UltraAgentRoutingGateway::class.java),
        networkGaming = Mockito.mock(UltraNetworkGamingGateway::class.java),
        aliasRepository = Mockito.mock(GameAliasStateRepository::class.java),
        memoryPrivacy = store
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
}

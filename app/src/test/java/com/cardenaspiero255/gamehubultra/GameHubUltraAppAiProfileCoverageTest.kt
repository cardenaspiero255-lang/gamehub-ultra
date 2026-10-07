package com.cardenaspiero255.gamehubultra

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraAssistantGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.ui.GameHubUiState
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHubUltraAppAiProfileCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptySelectionComposesAiProfileDerivationWithoutExternalGameState() {
        val viewModel = Mockito.mock(GameHubViewModel::class.java)
        Mockito.`when`(viewModel.uiState).thenReturn(MutableStateFlow(GameHubUiState()))
        Mockito.`when`(viewModel.performanceHistory)
            .thenReturn(MutableStateFlow(emptyList<PerformanceEvent>()))
        Mockito.`when`(viewModel.runtimeGameSession)
            .thenReturn(MutableStateFlow(null))
        Mockito.`when`(viewModel.sessionHistory)
            .thenReturn(flowOf(emptyList<GameSessionRecord>()))

        val memory = object : UltraAssistantSessionMemory {
            override suspend fun warmUp() = Unit

            override suspend fun recentConversationLines(
                limit: Int,
                scope: UltraMemoryScope
            ): List<String> = emptyList()

            override fun enqueueSyncConversation(
                previous: List<String>,
                next: List<String>,
                scope: UltraMemoryScope,
                timestampMillis: Long
            ) = Unit

            override fun enqueueClearConversationHistory(scope: UltraMemoryScope) = Unit
        }
        val aliasRepository = object : GameAliasStateRepository {
            override fun aliases(): Map<String, String> = emptyMap()
            override fun save(alias: String, packageName: String) = Unit
        }
        val ultraRuntime = UltraUiRuntimeDependencies(
            queryExecutor = Mockito.mock(UltraQueryExecutor::class.java),
            assistant = Mockito.mock(UltraAssistantGateway::class.java),
            sessionMemory = memory,
            agentRouter = Mockito.mock(UltraAgentRoutingGateway::class.java),
            networkGaming = Mockito.mock(UltraNetworkGamingGateway::class.java),
            aliasRepository = aliasRepository
        )
        val connectedAccounts = object : ConnectedGameAccountsStateRepository {
            override fun accountsFlow() = flowOf(emptyList<ConnectedGameAccount>())
            override fun activeAccountIdFlow() = flowOf<String?>(null)
            override suspend fun setActiveAccount(accountId: String?): Boolean = true

            override suspend fun upsert(
                platform: GamePlatform,
                displayName: String,
                publicId: String,
                alias: String?,
                avatarUrl: String?
            ): ConnectedGameAccount = error("unused")

            override suspend fun updatePublicMetadata(
                accountId: String,
                alias: String?,
                avatarUrl: String?
            ): Boolean = false

            override suspend fun remove(accountId: String) = Unit
        }
        val storeLibrary = object : StoreLibraryStateRepository {
            override fun getAll(): List<StoreLibraryGame> = emptyList()
            override fun replaceForAccount(accountId: String, games: List<StoreLibraryGame>) = Unit
            override fun removeForAccount(accountId: String) = Unit
        }
        val optimizationMemory = object : GameOptimizationMemoryStateRepository {
            override fun observationsFlow(contextKey: OptimizationContextKey) =
                flowOf(emptyList<OptimizationObservation>())

            override suspend fun record(
                contextKey: OptimizationContextKey,
                observation: OptimizationObservation
            ) = Unit

            override suspend fun pruneTo(contextKey: OptimizationContextKey) = Unit
            override suspend fun clearAll() = Unit
            override suspend fun clearGame(contextKey: OptimizationContextKey) = Unit
        }
        val app = RuntimeEnvironment.getApplication<Application>()
        val device = DeviceInfo(
            manufacturer = "test",
            model = "test",
            androidVersion = "15",
            sdkInt = 35,
            supportedAbis = listOf("arm64-v8a"),
            cpuModel = "test",
            cpuCores = 8,
            totalRamMb = 8192,
            gpuVendor = "test",
            gpuRenderer = "test"
        )

        composeRule.setContent {
            GameHubUltraApp(
                initialState = PerformanceState(),
                device = device,
                viewModel = viewModel,
                ultraRuntime = ultraRuntime,
                connectedAccountsRepository = connectedAccounts,
                storeLibraryRepository = storeLibrary,
                optimizationMemoryStore = optimizationMemory,
                aiProfileProposalStore = AiProfileProposalStore(app),
                initialTab = 1,
                onProfileApplied = { profile ->
                    PerformanceState(selectedProfile = profile)
                }
            )
        }

        composeRule.waitForIdle()
    }
}

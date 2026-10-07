package com.cardenaspiero255.gamehubultra.composition

import android.content.Context
import androidx.activity.ComponentActivity
import com.cardenaspiero255.gamehubultra.UltraConversationSessionMemoryAdapter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingRequest
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingRuntimeController
import com.cardenaspiero255.gamehubultra.ai.UltraProductionQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import com.cardenaspiero255.gamehubultra.ai.persistence.SharedPreferencesUltraResearchPersistentStore
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStore
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinator
import com.cardenaspiero255.gamehubultra.data.GameSessionLifecycleCoordinatorFactory
import com.cardenaspiero255.gamehubultra.data.GameSessionStore
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStore
import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStore
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModelDependencies
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModelDependencyFactory
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import com.cardenaspiero255.gamehubultra.voice.ContinuousVoiceController
import com.cardenaspiero255.gamehubultra.voice.SharedPreferencesGameAliasStateRepository
import com.cardenaspiero255.gamehubultra.voice.SharedPreferencesUltraVoicePreferenceRepository
import com.cardenaspiero255.gamehubultra.voice.UltraVoicePreferenceRepository

internal data class GameHubProductionBootstrap(
    val initialState: PerformanceState,
    val device: DeviceInfo,
    val performanceController: PerformanceController,
    val ultraRuntime: UltraUiRuntimeDependencies,
    val viewModelDependencyFactory: GameHubViewModelDependencyFactory,
    val connectedAccountsRepository: ConnectedGameAccountsStateRepository,
    val storeLibraryRepository: StoreLibraryStateRepository,
    val optimizationMemoryRepository: GameOptimizationMemoryStateRepository,
    val aiProfileProposalStore: AiProfileProposalStore
)

internal object GameHubProductionComposition {

    fun create(activity: ComponentActivity): GameHubProductionBootstrap {
        val capabilities = DeviceCapabilitiesProvider.get(activity)
        val performanceController = PerformanceController(capabilities)
        val initialState =
            performanceController.apply(PerformanceProfile.BALANCED, activity.window)
        val device = DeviceInfoProvider.get(activity)
        val appContext = activity.applicationContext
        configureUltraResearchPersistence(appContext)
        val preferencesRepository = GameHubPreferencesRepository(appContext)
        val sessionRepository = GameSessionStore(appContext)
        val connectedAccountsRepository = connectedAccountsRepository(appContext)
        val storeLibraryRepository = storeLibraryRepository(appContext)
        val optimizationMemoryRepository: GameOptimizationMemoryStateRepository =
            GameOptimizationMemoryStore(appContext)
        val aiProfileProposalStore = AiProfileProposalStore(appContext)
        val sessionCoordinatorFactory = GameSessionLifecycleCoordinatorFactory { scope ->
            GameSessionLifecycleCoordinator(
                store = sessionRepository,
                scope = scope
            )
        }
        val viewModelDependencyFactory = GameHubViewModelDependencyFactory {
            GameHubViewModelDependencies(
                selectionRepository = preferencesRepository,
                libraryRepository = preferencesRepository,
                performanceHistoryRepository = preferencesRepository,
                sessionRepository = sessionRepository,
                sessionCoordinatorFactory = sessionCoordinatorFactory,
                playerIdentityRepository = preferencesRepository
            )
        }
        val memoryStore = UltraConversationMemoryStore.get(appContext)

        val ultraRuntime = UltraUiRuntimeDependencies(
            queryExecutor = UltraProductionQueryExecutor,
            assistant = GameHubAiAdvisor(
                modelAdapter = GeminiNanoLocalAiModelAdapter(),
                memoryGateway = memoryStore
            ),
            sessionMemory = UltraConversationSessionMemoryAdapter(memoryStore),
            agentRouter = UltraAgentRoutingGateway { request ->
                routeAgentRequest(request)
            },
            networkGaming = object : UltraNetworkGamingGateway {
                override fun execute(
                    intent: com.cardenaspiero255.gamehubultra.ai.UltraUtilityIntent.NetworkGamingControl
                ): String =
                    UltraNetworkGamingRuntimeController.execute(
                        intent = intent,
                        applyCompetitive = {
                            NetworkRuntimeOptimizer.apply(
                                appContext,
                                NetworkGameProfile.COMPETITIVE
                            )
                        }
                    )

                override fun applyProfile(
                    profile: NetworkGameProfile
                ) = NetworkRuntimeOptimizer.apply(appContext, profile)
            },
            aliasRepository = SharedPreferencesGameAliasStateRepository(appContext)
        )

        return GameHubProductionBootstrap(
            initialState = initialState,
            device = device,
            performanceController = performanceController,
            ultraRuntime = ultraRuntime,
            viewModelDependencyFactory = viewModelDependencyFactory,
            connectedAccountsRepository = connectedAccountsRepository,
            storeLibraryRepository = storeLibraryRepository,
            optimizationMemoryRepository = optimizationMemoryRepository,
            aiProfileProposalStore = aiProfileProposalStore
        )
    }

    fun configureUltraResearchPersistence(appContext: Context) {
        UltraProductionQueryExecutor.attachPersistentStore(
            SharedPreferencesUltraResearchPersistentStore(appContext)
        )
    }

    fun ultraConversationMemory(appContext: Context): UltraConversationMemoryStore =
        UltraConversationMemoryStore.get(appContext)

    fun aliasRepository(appContext: Context): GameAliasStateRepository =
        SharedPreferencesGameAliasStateRepository(appContext)

    fun voicePreferenceRepository(appContext: Context): UltraVoicePreferenceRepository =
        SharedPreferencesUltraVoicePreferenceRepository(appContext)

    fun selectionRepository(appContext: Context): GameSelectionStateRepository =
        GameHubPreferencesRepository(appContext)

    fun connectedAccountsRepository(appContext: Context): ConnectedGameAccountsStateRepository =
        ConnectedGameAccountsStore(appContext)

    fun storeLibraryRepository(appContext: Context): StoreLibraryStateRepository =
        StoreLibraryStore(appContext)

    fun resumeContinuousVoice(context: Context) {
        ContinuousVoiceController(
            AndroidContinuousVoiceGateway(context)
        ).resumeIfEnabled()
    }

    private fun routeAgentRequest(
        request: UltraAgentRoutingRequest
    ) = UltraUnifiedAgentRouter.route(
        transcript = request.transcript,
        optionalResolver = request.optionalResolver,
        telemetry = request.telemetry,
        knownGameAliases = request.knownGameAliases,
        conversationHistory = request.conversationHistory
    )
}

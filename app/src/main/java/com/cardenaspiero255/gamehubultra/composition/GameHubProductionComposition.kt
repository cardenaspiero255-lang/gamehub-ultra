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
import com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionStore
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

internal data class GameHubProductionBootstrap(
    val initialState: PerformanceState,
    val device: DeviceInfo,
    val performanceController: PerformanceController,
    val ultraRuntime: UltraUiRuntimeDependencies,
    val viewModelDependencyFactory: GameHubViewModelDependencyFactory
)

internal object GameHubProductionComposition {

    fun create(activity: ComponentActivity): GameHubProductionBootstrap {
        val capabilities = DeviceCapabilitiesProvider.get(activity)
        val performanceController = PerformanceController(capabilities)
        val initialState =
            performanceController.apply(PerformanceProfile.BALANCED, activity.window)
        val device = DeviceInfoProvider.get(activity)
        val appContext = activity.applicationContext
        val preferencesRepository = GameHubPreferencesRepository(appContext)
        val sessionRepository = GameSessionStore(appContext)
        val viewModelDependencyFactory = GameHubViewModelDependencyFactory {
            GameHubViewModelDependencies(
                selectionRepository = preferencesRepository,
                libraryRepository = preferencesRepository,
                performanceHistoryRepository = preferencesRepository,
                sessionRepository = sessionRepository
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
            }
        )

        return GameHubProductionBootstrap(
            initialState = initialState,
            device = device,
            performanceController = performanceController,
            ultraRuntime = ultraRuntime,
            viewModelDependencyFactory = viewModelDependencyFactory
        )
    }

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

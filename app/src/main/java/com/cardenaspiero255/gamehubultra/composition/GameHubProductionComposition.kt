package com.cardenaspiero255.gamehubultra.composition

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.GameHubUltraApp
import com.cardenaspiero255.gamehubultra.UltraConversationSessionMemoryAdapter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingRequest
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingGateway
import com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingRuntimeController
import com.cardenaspiero255.gamehubultra.ai.UltraProductionQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.network.NetworkGameProfile
import com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import com.cardenaspiero255.gamehubultra.voice.ContinuousVoiceController

internal data class GameHubProductionBootstrap(
    val initialState: PerformanceState,
    val device: DeviceInfo,
    val performanceController: PerformanceController,
    val ultraRuntime: UltraUiRuntimeDependencies
)

internal object GameHubProductionComposition {

    fun create(activity: ComponentActivity): GameHubProductionBootstrap {
        val capabilities = DeviceCapabilitiesProvider.get(activity)
        val performanceController = PerformanceController(capabilities)
        val initialState =
            performanceController.apply(PerformanceProfile.BALANCED, activity.window)
        val device = DeviceInfoProvider.get(activity)
        val appContext = activity.applicationContext
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
            networkGaming = UltraNetworkGamingGateway { intent ->
                UltraNetworkGamingRuntimeController.execute(
                    intent = intent,
                    applyCompetitive = {
                        NetworkRuntimeOptimizer.apply(
                            appContext,
                            NetworkGameProfile.COMPETITIVE
                        )
                    }
                )
            }
        )

        return GameHubProductionBootstrap(
            initialState = initialState,
            device = device,
            performanceController = performanceController,
            ultraRuntime = ultraRuntime
        )
    }

    fun resumeContinuousVoice(context: Context) {
        ContinuousVoiceController(
            AndroidContinuousVoiceGateway(context)
        ).resumeIfEnabled()
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    fun Content(
        activity: ComponentActivity,
        bootstrap: GameHubProductionBootstrap,
        deepLinkHost: String?
    ) {
        val initialTab = if (deepLinkHost == "library") 1 else 0
        val gameHubViewModel: GameHubViewModel = viewModel()

        GameHubUltraTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }
            ) {
                GameHubUltraApp(
                    initialState = bootstrap.initialState,
                    device = bootstrap.device,
                    viewModel = gameHubViewModel,
                    ultraRuntime = bootstrap.ultraRuntime,
                    initialTab = initialTab,
                    onProfileApplied = { profile ->
                        bootstrap.performanceController.apply(profile, activity.window)
                    }
                )
            }
        }
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

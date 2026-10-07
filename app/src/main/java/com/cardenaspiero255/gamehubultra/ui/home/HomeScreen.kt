package com.cardenaspiero255.gamehubultra.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cardenaspiero255.gamehubultra.*
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.ui.components.*
import com.cardenaspiero255.gamehubultra.ui.home.state.HomeUiEvent
import com.cardenaspiero255.gamehubultra.ui.home.state.rememberHomeUiStateHolder
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.ui.voice.VoiceAssistantCard
import com.cardenaspiero255.gamehubultra.ui.share.sharePerformanceTimeline
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val UltraHomeRed = Color(0xFFFF1630)
private val UltraHomeBlack = Color(0xFF030303)
private val UltraHomePanel = Color(0xFF0B0B0E)
private val UltraHomePanelAlt = Color(0xFF111116)
private val UltraHomeMuted = Color(0xFF9696A2)
private val UltraHomeLine = Color(0xFF2A2A31)

@Composable
internal fun HomeScreen(
    modifier: Modifier,
    state: PerformanceState,
    device: DeviceInfo,
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit,
    onPlaySelectedGame: () -> Unit,
    runtimeDiagnostics: RuntimeDiagnostics?,
    telemetryTrend: List<RuntimeDiagnostics>,
    performanceTimeline: PerformanceTimeline,
    sessionHistory: List<GameSessionRecord>,
    sessionCoachPreMessage: SessionCoachMessage?,
    sessionCoachSamples: List<SessionCoachSnapshot>,
    sessionCoachObservations: List<SessionCoachMessage>,
    lastSessionCoachReport: SessionCoachPostSessionReport?,
    onClearSessions: () -> Unit,
    onShareSessions: () -> Unit,
    adaptiveDecision: AdaptiveDecision?,
    smartRecommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation,
    canRevertSmartRecommendation: Boolean,
    onApplySmartRecommendation: () -> Unit,
    onRejectSmartRecommendation: () -> Unit,
    onRevertSmartRecommendation: () -> Unit,
    smartGameAssistantSuggestions: List<SmartGameAssistantSuggestion>,
    onApplySmartGameAssistant: (SmartGameAssistantSuggestion) -> Unit,
    optimizationObservations: List<OptimizationObservation>,
    onClearOptimizationMemory: () -> Unit,
    performanceHistory: List<PerformanceEvent>,
    onApplyAdaptiveProfile: () -> Unit,
    aiContext: GameHubAiContext,
    ultraRuntime: UltraUiRuntimeDependencies,
    queryRunner: UltraAssistantQueryRunner,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    assistantInputEnabled: Boolean,
    onVoiceSelectedGame: (String) -> Unit,
    onVoiceSelectedProfile: (PerformanceProfile) -> Unit,
    onVoiceSelectedGameWithProfile: (String, PerformanceProfile) -> Unit,
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
    manualGamePackages: Set<String>,
    storeGames: List<StoreLibraryGame>,
    gameCatalogRefreshToken: Int,
    onOpenLibrary: () -> Unit,
    assistantRevealRequest: Int,
    onAssistantRevealConsumed: () -> Unit,
    showAssistantCards: Boolean
) {
    val timelineContext = LocalContext.current
    val homeStateHolder = rememberHomeUiStateHolder()
    val homeUiState = homeStateHolder.state
    LaunchedEffect(assistantRevealRequest) {
        if (assistantRevealRequest > 0) {
            homeStateHolder.onEvent(HomeUiEvent.QuickVoiceRevealed)
            onAssistantRevealConsumed()
        }
    }
    val recentGameNames = remember(recentGamePackages) {
        recentGamePackages.map { packageName ->
            packageDisplayName(timelineContext, packageName)
        }
    }
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(homeUiState.quickVoiceRevealRequest) {
        if (homeUiState.quickVoiceRevealRequest > 0) {
            homeListState.animateScrollToItem(3)
        }
    }
    LaunchedEffect(timelineContext, manualGamePackages, gameCatalogRefreshToken) {
        val localGameCount = withContext(Dispatchers.IO) {
            GameLibrary.discover(
                context = timelineContext,
                additionalPackages = manualGamePackages
            ).games.size
        }
        homeStateHolder.onEvent(HomeUiEvent.GameCountLoaded(localGameCount))
    }
    val homeTypography = MaterialTheme.typography
    val homeShapes = MaterialTheme.shapes
    val homeColorScheme = darkColorScheme(
        primary = UltraHomeRed,
        onPrimary = Color.Black,
        background = UltraHomeBlack,
        onBackground = Color.White,
        surface = UltraHomePanel,
        onSurface = Color.White,
        surfaceVariant = UltraHomePanelAlt,
        onSurfaceVariant = UltraHomeMuted,
        outline = UltraHomeLine
    )

    MaterialTheme(
        colorScheme = homeColorScheme,
        typography = homeTypography,
        shapes = homeShapes
    ) {
        LazyColumn(
            state = homeListState,
            modifier = modifier
                .fillMaxSize()
                .background(UltraHomeBlack)
                .padding(horizontal = GameHubUiTokens.compactHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
        ) {
            item {
                Text(
                    stringResource(R.string.hero_subtitle).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        item {
            StoreLibrarySummary(
                games = storeGames,
                onOpenLibrary = onOpenLibrary
            )
        }
        item {
            UltraDashboard(
                device = device,
                diagnostics = runtimeDiagnostics,
                telemetryTrend = telemetryTrend,
                profile = state.selectedProfile,
                adaptiveDecision = adaptiveDecision,
                gameName = aiContext.selectedGamePackage?.let { packageDisplayName(timelineContext, it) }
                    ?: "Selecciona un juego",
                recentGames = recentGameNames,
                gameCount = homeUiState.localGameCount,
                sessionCount = sessionHistory.size,
                onProfileSelected = onProfileSelected,
                onPlay = onPlaySelectedGame,
                onVoiceClick = {
                    homeStateHolder.onEvent(HomeUiEvent.QuickVoiceStartRequested)
                }
            )
        }
        if (homeUiState.quickVoiceOpen && !showAssistantCards) {
            item {
                VoiceAssistantCard(
                    selectedProfileName = selectedProfileName,
                    onProfileSelected = onProfileSelected,
                    onGameSelected = onGameSelected,
                    onVoiceSelectedGame = onVoiceSelectedGame,
                    onVoiceSelectedProfile = onVoiceSelectedProfile,
                    onVoiceSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                    aiContext = aiContext,
                    ultraRuntime = ultraRuntime,
                    queryRunner = queryRunner,
                    conversation = conversation,
                    onConversationChanged = onConversationChanged,
                    assistantInputEnabled = assistantInputEnabled,
                    startListeningRequest = homeUiState.quickVoiceStartRequest
                )
            }
        }
        item { ActiveProfileCard(state) }
        item {
            SmartPerformanceCard(
                recommendation = smartRecommendation,
                observations = optimizationObservations,
                canRevert = canRevertSmartRecommendation,
                onApply = onApplySmartRecommendation,
                onReject = onRejectSmartRecommendation,
                onRevert = onRevertSmartRecommendation,
                onClearMemory = onClearOptimizationMemory
            )
        }
        item {
            SmartGameAssistantCard(
                suggestions = smartGameAssistantSuggestions,
                onApply = onApplySmartGameAssistant
            )
        }
        item {
            SessionCenterCard(
                context = LocalContext.current,
                sessions = sessionHistory,
                onClear = onClearSessions,
                onShare = onShareSessions
            )
        }
        item {
            AiSessionCoachCard(
                preSession = sessionCoachPreMessage,
                liveSamples = sessionCoachSamples,
                observations = sessionCoachObservations,
                postSession = lastSessionCoachReport,
                sessionActive = aiContext.sessionActive
            )
        }
        item {
            PerformanceTimelineCard(
                timeline = performanceTimeline,
                telemetryTrend = telemetryTrend,
                sessionActive = aiContext.sessionActive,
                onShare = {
                    sharePerformanceTimeline(
                        context = timelineContext,
                        gamePackage = aiContext.selectedGamePackage,
                        timeline = performanceTimeline
                    )
                }
            )
        }
        item {
            TusJuegosShelf(
                favoriteGames = favoriteGames,
                recentGamePackages = recentGamePackages,
                manualGamePackages = manualGamePackages,
                selectedGamePackage = aiContext.selectedGamePackage,
                onGameSelected = onGameSelected
            )
        }
        if (showAssistantCards) {
            item {
                VoiceAssistantCard(
                    selectedProfileName = selectedProfileName,
                    onProfileSelected = onProfileSelected,
                    onGameSelected = onGameSelected,
                    onVoiceSelectedGame = onVoiceSelectedGame,
                    onVoiceSelectedProfile = onVoiceSelectedProfile,
                    onVoiceSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                    aiContext = aiContext,
                    ultraRuntime = ultraRuntime,
                    queryRunner = queryRunner,
                    conversation = conversation,
                    onConversationChanged = onConversationChanged,
                    assistantInputEnabled = assistantInputEnabled,
                    startListeningRequest = homeUiState.quickVoiceStartRequest
                )
            }
        }
        item { DeviceStatusCard(device, state.capabilities) }
        item {
            RuntimeDiagnosticsCard(
                device = device,
                diagnostics = runtimeDiagnostics,
                adaptiveDecision = adaptiveDecision,
                performanceHistory = performanceHistory,
                onApplyAdaptiveProfile = onApplyAdaptiveProfile
            )
        }
            item {
                PeripheralsHubCard(peripherals = runtimeDiagnostics?.peripherals)
            }
        }
    }
}



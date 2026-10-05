package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import android.os.Build
import android.os.Trace
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.AdaptivePerformanceEngine
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.OptimizationFingerprint
import com.cardenaspiero255.gamehubultra.domain.OptimizationFeedbackDecision
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceAdvisor
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistant
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantInput
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceInput
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineBuilder
import com.cardenaspiero255.gamehubultra.domain.EmulatorBackendDetector
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import com.cardenaspiero255.gamehubultra.ui.components.GameHubWideNavigationRail
import com.cardenaspiero255.gamehubultra.ui.components.SettingsScreen
import com.cardenaspiero255.gamehubultra.ui.components.packageVersionName
import com.cardenaspiero255.gamehubultra.ui.runtime.DashboardTelemetryController
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeActions
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeCoordinator
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.ui.runtime.RuntimeSessionMetrics
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.ui.voice.VoiceAssistantCard
import com.cardenaspiero255.gamehubultra.ui.share.shareSessionHistory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import com.cardenaspiero255.gamehubultra.ui.layout.ResponsiveLayoutPolicy
import com.cardenaspiero255.gamehubultra.ui.layout.UltraLayoutMode
import com.cardenaspiero255.gamehubultra.ui.home.HomeScreen
import com.cardenaspiero255.gamehubultra.ui.library.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CHAT_HISTORY = 8

private val UltraHomeRed = Color(0xFFFF1630)
private val UltraHomeBlack = Color(0xFF030303)
private val UltraHomePanel = Color(0xFF0B0B0E)
private val UltraHomePanelAlt = Color(0xFF111116)
private val UltraHomeMuted = Color(0xFF9696A2)
private val UltraHomeLine = Color(0xFF2A2A31)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
internal fun GameHubUltraApp(
    initialState: PerformanceState,
    device: DeviceInfo,
    viewModel: GameHubViewModel,
    ultraRuntime: UltraUiRuntimeDependencies,
    connectedAccountsRepository: ConnectedGameAccountsStateRepository,
    storeLibraryRepository: StoreLibraryStateRepository,
    optimizationMemoryStore: GameOptimizationMemoryStateRepository,
    initialTab: Int,
    onProfileApplied: (PerformanceProfile) -> PerformanceState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val performanceHistory by viewModel.performanceHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var state by remember { mutableStateOf(initialState) }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var assistantRevealRequest by rememberSaveable { mutableIntStateOf(0) }
    val runtimeGameSession by viewModel.runtimeGameSession.collectAsStateWithLifecycle()
    val activeSessionPackage = runtimeGameSession?.packageName
    val activeSessionId = runtimeGameSession?.id
    var runtimeDiagnostics by remember { mutableStateOf<RuntimeDiagnostics?>(null) }
    var adaptiveDecision by remember { mutableStateOf<AdaptiveDecision?>(null) }
    var telemetryTrend by remember { mutableStateOf<List<RuntimeDiagnostics>>(emptyList()) }
    var performanceTimelineSamples by remember { mutableStateOf<List<com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineSample>>(emptyList()) }
    var storeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var appResumeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var storeGames by remember { mutableStateOf<List<StoreLibraryGame>>(emptyList()) }
    val sessionHistory by viewModel.sessionHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val aiAdvisor = ultraRuntime.assistant
    val ultraSessionMemory = ultraRuntime.sessionMemory

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appResumeRefreshToken += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val selectedGameForMemory = uiState.selectedGamePackage
    val selectedGameVersion = remember(selectedGameForMemory) {
        selectedGameForMemory?.let { packageVersionName(context, it) }
    }
    val currentOptimizationKey = remember(
        selectedGameForMemory,
        selectedGameVersion,
        device
    ) {
        val driverFingerprint = listOf(
            device.gpuVendor.orEmpty(),
            device.gpuRenderer.orEmpty()
        ).joinToString("|").takeIf(String::isNotBlank)
        OptimizationContextKey(
            deviceFingerprint = OptimizationFingerprint.from(
                device = device,
                gamePackage = selectedGameForMemory,
                gameVersion = selectedGameVersion,
                emulatorBackend = EmulatorBackendDetector.detect(),
                driverFingerprint = driverFingerprint
            ),
            gamePackage = selectedGameForMemory.orEmpty(),
            gameVersion = selectedGameVersion,
            emulatorBackend = EmulatorBackendDetector.detect(),
            driverFingerprint = driverFingerprint
        )
    }
    val optimizationObservations by optimizationMemoryStore
        .observationsFlow(currentOptimizationKey)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(storeRefreshToken) {
        storeGames = withContext(Dispatchers.IO) {
            storeLibraryRepository.getAll()
        }
    }
    val adaptiveEngine = remember(uiState.effectiveProfile) {
        AdaptivePerformanceEngine(initialProfile = uiState.effectiveProfile)
    }
    val adaptiveEngineState = rememberUpdatedState(adaptiveEngine)
    val dashboardTelemetryController = remember(context, viewModel) {
        DashboardTelemetryController(
            adaptiveEvaluator = { snapshot ->
                adaptiveEngineState.value.evaluate(snapshot)
            },
            latencyProbe = { networkHandle ->
                withContext(Dispatchers.IO) {
                    com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe.measure(
                        context = context,
                        expectedNetworkHandle = networkHandle
                    )
                }
            },
            recordPerformanceEvent = viewModel::recordPerformanceEvent
        )
    }
    var restorableUltraGamePackage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var restorableUltraConversation by rememberSaveable {
        mutableStateOf<List<String>?>(null)
    }
    var restorableUltraHistoryHydrated by rememberSaveable {
        mutableStateOf<Boolean?>(null)
    }
    val ultraSessionController = remember(scope, ultraSessionMemory) {
        UltraAssistantSessionController(
            ownerScope = scope,
            memory = ultraSessionMemory,
            maxHistory = MAX_CHAT_HISTORY,
            restoredGamePackage = restorableUltraGamePackage,
            restoredConversation = restorableUltraConversation,
            restoredHistoryHydrated = restorableUltraHistoryHydrated,
            onSnapshotChanged = { gamePackage, conversation ->
                restorableUltraGamePackage = gamePackage
                restorableUltraConversation = conversation
            },
            onHistoryHydrationChanged = { hydrated ->
                restorableUltraHistoryHydrated = hydrated
            }
        )
    }
    val ultraConversation by ultraSessionController.conversation.collectAsStateWithLifecycle()
    val ultraControllerGamePackage by ultraSessionController.selectedGamePackage.collectAsStateWithLifecycle()
    val ultraControllerScopeReady by ultraSessionController.scopeReady.collectAsStateWithLifecycle()
    val ultraQueryRunner = ultraSessionController.queryRunner
    val ultraHistoryRetryGate = remember(ultraSessionController) {
        UltraAssistantSessionRetryGate(maxRetriesPerScope = 1)
    }
    val ultraSessionScopeSelection = uiState.ultraSessionScopeSelection()
    val ultraAssistantInputReady = isUltraAssistantInputReady(
        selection = ultraSessionScopeSelection,
        controllerGamePackage = ultraControllerGamePackage,
        controllerScopeReady = ultraControllerScopeReady
    )

    LaunchedEffect(ultraSessionController, ultraSessionScopeSelection) {
        val selection = ultraSessionScopeSelection ?: return@LaunchedEffect
        ultraSessionController.selectGame(selection.gamePackage).join()
        if (
            ultraHistoryRetryGate.consumeRetry(
                gamePackage = selection.gamePackage,
                hasLoadError = ultraSessionController.loadError.value != null
            )
        ) {
            ultraSessionController.retryLoad().join()
        }
    }

    DisposableEffect(aiAdvisor) {
        onDispose { aiAdvisor.close() }
    }

    LaunchedEffect(
        lifecycleOwner,
        dashboardTelemetryController,
        activeSessionPackage,
        activeSessionId
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val selectedGame = activeSessionPackage
            val sessionId = activeSessionId
            val telemetryPlan = dashboardTelemetryPlan(sessionId)

            try {
                while (isActive && telemetryPlan.collectDashboardTelemetry) {
                    val diagnostics = withContext(Dispatchers.IO) {
                        RuntimeDiagnosticsProvider.get(context)
                    }
                    val update = dashboardTelemetryController.sample(
                        diagnostics = diagnostics,
                        activeGamePackage = selectedGame,
                        sessionId = sessionId,
                        sustainedPerformanceSupported =
                            initialState.capabilities?.sustainedPerformanceSupported == true,
                        performanceHintsAvailable =
                            initialState.capabilities?.performanceHintsAvailable == true
                    )
                    runtimeDiagnostics = update.diagnostics
                    telemetryTrend = update.telemetryTrend
                    performanceTimelineSamples = update.timelineSamples
                    adaptiveDecision = update.adaptiveDecision
                    delay(10_000)
                }
            } finally {
                // Lifecycle cancellation pauses dashboard telemetry until the app resumes.
            }
        }
    }

    LaunchedEffect(uiState.effectiveProfile) {
        state = onProfileApplied(uiState.effectiveProfile)
    }

    val runtimeActions = remember(viewModel) {
        object : GameHubRuntimeActions {
            override fun selectGlobalProfile(profile: PerformanceProfile) {
                viewModel.selectGlobalProfile(profile)
            }

            override fun selectGameProfile(
                packageName: String,
                profile: PerformanceProfile
            ) {
                viewModel.selectGameProfile(packageName, profile)
            }

            override fun applySmartGameAssistantSuggestion(
                packageName: String,
                suggestion: SmartGameAssistantSuggestion
            ) {
                viewModel.applySmartGameAssistantSuggestion(packageName, suggestion)
            }

            override fun finishRuntimeGameSession(
                metrics: SessionEndMetrics
            ) = viewModel.finishRuntimeGameSession(metrics)

            override fun beginRuntimeGameSession(record: GameSessionRecord) {
                viewModel.beginRuntimeGameSession(record)
            }

            override fun recordPerformanceEvent(event: PerformanceEvent) {
                viewModel.recordPerformanceEvent(event)
            }

            override fun recordRecentGame(packageName: String) {
                viewModel.recordRecentGame(packageName)
            }

            override fun selectGame(packageName: String) {
                viewModel.selectGame(packageName)
            }
        }
    }
    val runtimeCoordinator = remember(
        scope,
        runtimeActions,
        optimizationMemoryStore
    ) {
        GameHubRuntimeCoordinator(
            scope = scope,
            actions = runtimeActions,
            recordOptimization = optimizationMemoryStore::record
        )
    }

    fun runtimeSnapshot() =
        GameHubRuntimeSnapshot(
            selectedGamePackage = uiState.selectedGamePackage,
            effectiveProfile = uiState.effectiveProfile,
            activeSessionId = activeSessionId,
            metrics = RuntimeSessionMetrics(
                batteryPercent = runtimeDiagnostics?.battery?.percent,
                thermalStatus = runtimeDiagnostics?.thermal?.status,
                ramUsedPercent = runtimeDiagnostics?.memory?.usedPercent,
                diagnosticsAvailable = runtimeDiagnostics != null
            ),
            optimizationContextKey = currentOptimizationKey
        )

    fun selectProfile(profile: PerformanceProfile) {
        runtimeCoordinator.selectProfile(runtimeSnapshot(), profile)
    }

    fun applySmartGameAssistantSuggestion(
        suggestion: SmartGameAssistantSuggestion
    ) {
        runtimeCoordinator.applySmartGameAssistantSuggestion(
            runtimeSnapshot(),
            suggestion
        )
    }

    fun endGameSession() {
        runtimeCoordinator.endGameSession(runtimeSnapshot())
    }

    fun selectGame(packageName: String) {
        runtimeCoordinator.selectGame(runtimeSnapshot(), packageName)
    }

    fun recordGameOpened(packageName: String) {
        runtimeCoordinator.recordGameOpened(runtimeSnapshot(), packageName)
    }

    fun playSelectedGame() {
        val packageName = uiState.selectedGamePackage
        if (packageName.isNullOrBlank()) {
            settingsOpen = false
            profileOpen = false
            selectedTab = 1
            return
        }
        if (openGame(context, packageName)) {
            recordGameOpened(packageName)
        }
    }

    val selectedProfileName = uiState.effectiveProfile.name
    val selectedGamePackage = uiState.selectedGamePackage
    val favoriteGames = uiState.favoriteGames
    val recentGamePackages = uiState.recentGamePackages
    val manualGamePackages = uiState.manualGamePackages
    val performanceTimeline = PerformanceTimelineBuilder.build(
        samples = performanceTimelineSamples,
        events = performanceHistory,
        activeSessionId = activeSessionId
    )

    val smartRecommendation = SmartPerformanceAdvisor.recommend(
        SmartPerformanceInput(
            device = device,
            runtime = runtimeDiagnostics,
            gamePackage = selectedGamePackage,
            gameVersion = selectedGameVersion,
            emulatorBackend = EmulatorBackendDetector.detect(),
            currentProfile = uiState.effectiveProfile,
            historicalObservations = optimizationObservations
        )
    )
    val smartGameAssistantSuggestions = SmartGameAssistant.suggestAll(
        SmartGameAssistantInput(
            device = device,
            runtime = runtimeDiagnostics,
            gamePackage = selectedGamePackage,
            currentProfile = uiState.effectiveProfile,
            historicalObservations = optimizationObservations,
            existingRecommendation = smartRecommendation
        )
    )

    val aiContext = GameHubAiContext(
        selectedGamePackage = selectedGamePackage,
        sustainedPerformanceSupported =
            initialState.capabilities?.sustainedPerformanceSupported == true,
        cpuCores = device.cpuCores,
        totalRamMb = device.totalRamMb.toInt(),
        gpuAvailable = !device.gpuRenderer.isNullOrBlank() || !device.gpuVendor.isNullOrBlank(),
        thermalStatus = runtimeDiagnostics?.thermal?.status,
        thermalHeadroom = runtimeDiagnostics?.thermal?.headroom,
        batteryPercent = runtimeDiagnostics?.battery?.percent,
        charging = runtimeDiagnostics?.battery?.charging == true,
        refreshRateHz = runtimeDiagnostics?.refresh?.currentRefreshRateHz,
        networkValidated = runtimeDiagnostics?.connectivity?.validated == true,
        networkLatencyMs = runtimeDiagnostics?.connectivity?.latencyMs,
        downstreamBandwidthKbps = runtimeDiagnostics?.connectivity?.downstreamBandwidthKbps?.toLong(),
        storageFreePercent = runtimeDiagnostics?.storage?.freePercent ?: 100,
        inputDeviceCount = runtimeDiagnostics?.inputDeviceCount ?: 0,
        selectedProfile = uiState.effectiveProfile,
        sessionActive = activeSessionPackage != null,
        optimizationObservations = optimizationObservations
    )

    val tabs = listOf(
        stringResource(R.string.nav_inicio),
        stringResource(R.string.nav_biblioteca)
    )
    val tabTestTags = listOf("nav_inicio", "nav_biblioteca")

    val configuration = LocalConfiguration.current
    val layoutMode = remember(configuration.screenWidthDp) {
        ResponsiveLayoutPolicy.modeForWidthDp(configuration.screenWidthDp)
    }
    val wideLayout = layoutMode != UltraLayoutMode.COMPACT
    val ultraWideLayout = layoutMode == UltraLayoutMode.ULTRA_WIDE

    LaunchedEffect(wideLayout) {
        if (!wideLayout) {
            profileOpen = false
        }
    }

    val screenContent: @Composable (Modifier, Boolean) -> Unit = { contentModifier, showAssistantCards ->
        when {
            settingsOpen -> SettingsScreen(
                modifier = contentModifier,
                accountsRepository = connectedAccountsRepository,
                storeLibraryRepository = storeLibraryRepository,
                onStoreConnectionChanged = { storeRefreshToken += 1 },
                onClearOptimizationMemory = {
                    scope.launch(Dispatchers.IO) { optimizationMemoryStore.clearAll() }
                },
                playerName = uiState.playerName,
                onPlayerNameChanged = viewModel::updatePlayerName
            )
            wideLayout && profileOpen -> UltraProfileScreen(
                modifier = contentModifier,
                playerName = uiState.playerName,
                activeProfile = uiState.effectiveProfile,
                favoriteCount = favoriteGames.size,
                recentCount = recentGamePackages.distinct().size,
                sessionCount = sessionHistory.size,
                device = device,
                onPlayerNameChanged = viewModel::updatePlayerName
            )
            selectedTab == 0 -> HomeScreen(
                modifier = contentModifier,
                state = state,
                device = device,
                selectedProfileName = selectedProfileName,
                onProfileSelected = ::selectProfile,
                onGameSelected = ::selectGame,
                onPlaySelectedGame = ::playSelectedGame,
                runtimeDiagnostics = runtimeDiagnostics,
                telemetryTrend = telemetryTrend,
                performanceTimeline = performanceTimeline,
                sessionHistory = sessionHistory,
                onClearSessions = {
                    viewModel.clearSessionHistory()
                },
                onShareSessions = { shareSessionHistory(context, sessionHistory) },
                adaptiveDecision = adaptiveDecision,
                smartRecommendation = smartRecommendation,
                onApplySmartRecommendation = {
                    runtimeCoordinator.recordRecommendationFeedback(
                        runtimeSnapshot(),
                        smartRecommendation.profile,
                        OptimizationFeedbackDecision.ACCEPTED
                    )
                    selectProfile(smartRecommendation.profile)
                },
                onRejectSmartRecommendation = {
                    runtimeCoordinator.recordRecommendationFeedback(
                        runtimeSnapshot(),
                        smartRecommendation.profile,
                        OptimizationFeedbackDecision.REJECTED
                    )
                },
                onRevertSmartRecommendation = {
                    if (
                        uiState.effectiveProfile == smartRecommendation.profile &&
                        smartRecommendation.safeFallback != smartRecommendation.profile
                    ) {
                        runtimeCoordinator.recordRecommendationFeedback(
                            runtimeSnapshot(),
                            smartRecommendation.profile,
                            OptimizationFeedbackDecision.REVERTED
                        )
                        selectProfile(smartRecommendation.safeFallback)
                    }
                },
                smartGameAssistantSuggestions = smartGameAssistantSuggestions,
                onApplySmartGameAssistant = ::applySmartGameAssistantSuggestion,
                optimizationObservations = optimizationObservations,
                onClearOptimizationMemory = {
                    scope.launch(Dispatchers.IO) {
                        optimizationMemoryStore.clearGame(currentOptimizationKey)
                    }
                },
                performanceHistory = performanceHistory,
                onApplyAdaptiveProfile = {
                    adaptiveDecision?.let { selectProfile(it.profile) }
                },
                aiContext = aiContext,
                ultraRuntime = ultraRuntime,
                queryRunner = ultraQueryRunner,
                conversation = ultraConversation,
                onConversationChanged = ultraSessionController::updateConversation,
                assistantInputEnabled = ultraAssistantInputReady,
                onVoiceSelectedGame = viewModel::persistVoiceSelectedGame,
                onVoiceSelectedProfile = viewModel::persistVoiceSelectedProfile,
                onVoiceSelectedGameWithProfile = viewModel::persistVoiceSelectedGameWithProfile,
                favoriteGames = favoriteGames,
                recentGamePackages = recentGamePackages,
                manualGamePackages = manualGamePackages,
                storeGames = storeGames,
                gameCatalogRefreshToken = appResumeRefreshToken,
                onOpenLibrary = {
                    settingsOpen = false
                    profileOpen = false
                    selectedTab = 1
                },
                assistantRevealRequest = assistantRevealRequest,
                onAssistantRevealConsumed = { assistantRevealRequest = 0 },
                showAssistantCards = showAssistantCards
            )
            else -> LibraryScreen(
                modifier = contentModifier,
                selectedGamePackage = selectedGamePackage,
                favoriteGames = favoriteGames,
                recentGamePackages = recentGamePackages,
                manualGamePackages = manualGamePackages,
                storeGames = storeGames,
                selectedProfile = uiState.effectiveProfile,
                runtimeDiagnostics = runtimeDiagnostics,
                sessionHistory = sessionHistory,
                onGameSelected = ::selectGame,
                onProfileSelected = ::selectProfile,
                onToggleFavorite = viewModel::setFavoriteGame,
                onGameOpened = ::recordGameOpened,
                onToggleManualGame = viewModel::setManualGame,
                onOpenAssistant = {
                    settingsOpen = false
                    profileOpen = false
                    selectedTab = 0
                    if (!ultraWideLayout) {
                        assistantRevealRequest += 1
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            if (!wideLayout) {
                TopAppBar(
                    title = { Text("GAMEHUB ULTRA") },
                    actions = {
                        TextButton(
                            onClick = {
                                Trace.beginSection("GameHubUltra.Navigation.Settings")
                                try {
                                    profileOpen = false
                                    settingsOpen = !settingsOpen
                                } finally {
                                    Trace.endSection()
                                }
                            },
                            modifier = Modifier
                                .testTag("nav_ajustes")
                                .semantics(mergeDescendants = true) {
                                    testTagsAsResourceId = true
                                    contentDescription = "nav_ajustes"
                                }
                        ) {
                            Text("⚙")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (wideLayout) {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                GameHubWideNavigationRail(
                    selectedTab = selectedTab,
                    settingsOpen = settingsOpen,
                    profileOpen = profileOpen,
                    onHome = {
                        settingsOpen = false
                        profileOpen = false
                        selectedTab = 0
                    },
                    onLibrary = {
                        settingsOpen = false
                        profileOpen = false
                        selectedTab = 1
                    },
                    onProfile = {
                        settingsOpen = false
                        profileOpen = true
                    },
                    onSettings = {
                        profileOpen = false
                        settingsOpen = true
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    UltraShellHeader(
                        playerName = uiState.playerName
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        screenContent(
                            Modifier.fillMaxSize(),
                            !ultraWideLayout
                        )
                    }
                }

                if (ultraWideLayout && !settingsOpen && !profileOpen && selectedTab == 0) {
                    UltraAssistantSidePanel(
                        aiContext = aiContext,
                        ultraRuntime = ultraRuntime,
                        queryRunner = ultraQueryRunner,
                        conversation = ultraConversation,
                        onConversationChanged = ultraSessionController::updateConversation,
                        assistantInputEnabled = ultraAssistantInputReady,
                        selectedProfileName = selectedProfileName,
                        onProfileSelected = ::selectProfile,
                        onGameSelected = ::selectGame,
                        onVoiceSelectedGame = viewModel::persistVoiceSelectedGame,
                        onVoiceSelectedProfile = viewModel::persistVoiceSelectedProfile,
                        onVoiceSelectedGameWithProfile = viewModel::persistVoiceSelectedGameWithProfile
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (!settingsOpen) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                settingsOpen = false
                                profileOpen = false
                                selectedTab = 0
                            },
                            modifier = Modifier
                                .testTag(tabTestTags[0])
                                .semantics(mergeDescendants = true) {
                                    testTagsAsResourceId = true
                                    contentDescription = "nav_inicio"
                                },
                            text = { Text(tabs[0].uppercase()) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                Trace.beginSection("GameHubUltra.Navigation.Library")
                                try {
                                    settingsOpen = false
                                    profileOpen = false
                                    selectedTab = 1
                                } finally {
                                    Trace.endSection()
                                }
                            },
                            modifier = Modifier
                                .testTag(tabTestTags[1])
                                .semantics(mergeDescendants = true) {
                                    testTagsAsResourceId = true
                                    contentDescription = "nav_biblioteca"
                                },
                            text = { Text(tabs[1].uppercase()) }
                        )
                    }
                }
                screenContent(Modifier.fillMaxSize(), true)
            }
        }
    }
}

@Composable
private fun UltraAssistantSidePanel(
    aiContext: GameHubAiContext,
    ultraRuntime: UltraUiRuntimeDependencies,
    queryRunner: UltraAssistantQueryRunner,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    assistantInputEnabled: Boolean,
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit,
    onVoiceSelectedGame: (String) -> Unit,
    onVoiceSelectedProfile: (PerformanceProfile) -> Unit,
    onVoiceSelectedGameWithProfile: (String, PerformanceProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(GameHubUiTokens.compactHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
    ) {
        Text(
            "ULTRA ASSISTANT",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
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
            assistantInputEnabled = assistantInputEnabled
        )
    }
}





internal fun packageDisplayName(context: Context, packageName: String): String =
    runCatching {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
        context.packageManager.getApplicationLabel(appInfo).toString()
    }.getOrDefault(packageName)


private fun openGame(context: Context, packageName: String): Boolean =
    GameLauncher.launch(context, packageName)


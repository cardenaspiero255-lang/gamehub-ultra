package com.cardenaspiero255.gamehubultra

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvice
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoutingRequest
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraConversationPolicy
import com.cardenaspiero255.gamehubultra.ai.UltraConversationScopePolicy
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryTurnPersistencePolicy
import com.cardenaspiero255.gamehubultra.ai.UltraRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStateRepository
import com.cardenaspiero255.gamehubultra.store.StoreConnectionActivity
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.os.Trace
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.AdaptivePerformanceEngine
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessCalculator
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessInput
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import com.cardenaspiero255.gamehubultra.voice.ContinuousVoiceChange
import com.cardenaspiero255.gamehubultra.voice.ContinuousVoiceController
import com.cardenaspiero255.gamehubultra.voice.NetworkVoiceResponseText
import com.cardenaspiero255.gamehubultra.voice.VoiceNetworkSnapshotFactory
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
import com.cardenaspiero255.gamehubultra.voice.VoiceDeviceStatusProvider
import com.cardenaspiero255.gamehubultra.voice.VoiceResponseFormatter
import com.cardenaspiero255.gamehubultra.voice.VoiceAssistantController
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandEngine
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import com.cardenaspiero255.gamehubultra.voice.VoiceDeviceStatus
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.GamePlatform
import com.cardenaspiero255.gamehubultra.domain.OptimizationFingerprint
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceAdvisor
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistant
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantInput
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceInput
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimeline
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineActionPolicy
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineBuilder
import com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineReportFormatter
import com.cardenaspiero255.gamehubultra.domain.EmulatorBackendDetector
import com.cardenaspiero255.gamehubultra.domain.GameAccountValidation
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import com.cardenaspiero255.gamehubultra.ui.components.GameHubWideNavigationRail
import com.cardenaspiero255.gamehubultra.ui.components.PeripheralsHubCard
import com.cardenaspiero255.gamehubultra.ui.components.RuntimeDiagnosticsCard
import com.cardenaspiero255.gamehubultra.ui.components.SettingsScreen
import com.cardenaspiero255.gamehubultra.ui.components.ActiveProfileCard
import com.cardenaspiero255.gamehubultra.ui.components.DeviceRow
import com.cardenaspiero255.gamehubultra.ui.components.DeviceStatusCard
import com.cardenaspiero255.gamehubultra.ui.components.SessionCenterCard
import com.cardenaspiero255.gamehubultra.ui.components.SmartPerformanceCard
import com.cardenaspiero255.gamehubultra.ui.components.formatDuration
import com.cardenaspiero255.gamehubultra.ui.components.TusJuegosShelf
import com.cardenaspiero255.gamehubultra.ui.components.GameTile
import com.cardenaspiero255.gamehubultra.ui.components.SelectedGameCard
import com.cardenaspiero255.gamehubultra.ui.components.SelectedGameCompactBar
import com.cardenaspiero255.gamehubultra.ui.components.StoreLibrarySection
import com.cardenaspiero255.gamehubultra.ui.components.StoreLibrarySummary
import com.cardenaspiero255.gamehubultra.ui.runtime.DashboardTelemetryController
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeActions
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeCoordinator
import com.cardenaspiero255.gamehubultra.ui.runtime.GameHubRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.ui.runtime.RuntimeSessionMetrics
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.ui.share.sharePerformanceTimeline
import com.cardenaspiero255.gamehubultra.ui.share.shareSessionHistory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.PeripheralKind
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import com.cardenaspiero255.gamehubultra.platform.GamePlatformLinks
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import com.cardenaspiero255.gamehubultra.ui.layout.LibraryLayoutPolicy
import com.cardenaspiero255.gamehubultra.ui.layout.ResponsiveLayoutPolicy
import com.cardenaspiero255.gamehubultra.ui.layout.UltraLayoutMode
import com.cardenaspiero255.gamehubultra.ui.home.state.HomeUiEvent
import com.cardenaspiero255.gamehubultra.ui.home.state.rememberHomeUiStateHolder
import com.cardenaspiero255.gamehubultra.ui.library.state.LibraryUiEvent
import com.cardenaspiero255.gamehubultra.ui.library.state.rememberLibraryUiStateHolder
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
        sessionActive = activeSessionPackage != null
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
                }
            )
            wideLayout && profileOpen -> UltraProfileScreen(
                modifier = contentModifier,
                playerName = ULTRA_PLAYER_NAME,
                activeProfile = uiState.effectiveProfile,
                favoriteCount = favoriteGames.size,
                recentCount = recentGamePackages.distinct().size,
                sessionCount = sessionHistory.size,
                device = device
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
                    selectProfile(smartRecommendation.profile)
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
                onToggleManualGame = viewModel::setManualGame
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
                        playerName = ULTRA_PLAYER_NAME
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

@Composable
private fun HomeScreen(
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
    onClearSessions: () -> Unit,
    onShareSessions: () -> Unit,
    adaptiveDecision: AdaptiveDecision?,
    smartRecommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation,
    onApplySmartRecommendation: () -> Unit,
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
    showAssistantCards: Boolean
) {
    val timelineContext = LocalContext.current
    val homeStateHolder = rememberHomeUiStateHolder()
    val homeUiState = homeStateHolder.state
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
                    homeStateHolder.onEvent(HomeUiEvent.QuickVoiceToggled)
                }
            )
        }
        if (homeUiState.quickVoiceOpen) {
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
                    assistantInputEnabled = assistantInputEnabled
                )
            }
        }
        item { ActiveProfileCard(state) }
        item {
            SmartPerformanceCard(
                recommendation = smartRecommendation,
                observations = optimizationObservations,
                onApply = onApplySmartRecommendation,
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
            PerformanceTimelineCard(
                timeline = performanceTimeline,
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
                    assistantInputEnabled = assistantInputEnabled
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


@Composable
private fun VoiceAssistantCard(
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit,
    onVoiceSelectedGame: (String) -> Unit,
    onVoiceSelectedProfile: (PerformanceProfile) -> Unit,
    onVoiceSelectedGameWithProfile: (String, PerformanceProfile) -> Unit,
    aiContext: GameHubAiContext,
    ultraRuntime: UltraUiRuntimeDependencies,
    queryRunner: UltraAssistantQueryRunner,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    assistantInputEnabled: Boolean
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aliasRepository = ultraRuntime.aliasRepository
    val aiAdvisor = ultraRuntime.assistant
    val queryExecutor = ultraRuntime.queryExecutor
    val agentRouter = ultraRuntime.agentRouter
    val networkGaming = ultraRuntime.networkGaming
    val continuousVoiceController = remember(context) {
        ContinuousVoiceController(
            AndroidContinuousVoiceGateway(context)
        )
    }
    var continuousListeningEnabled by remember {
        mutableStateOf(continuousVoiceController.isEnabled())
    }
    val aiIntentResolver = remember(aiAdvisor) { aiAdvisor.intentResolver() }
    val latestAiContext by rememberUpdatedState(aiContext)
    val latestConversation by rememberUpdatedState(conversation)
    val latestOnConversationChanged by rememberUpdatedState(onConversationChanged)
    val latestAssistantInputEnabled by rememberUpdatedState(assistantInputEnabled)
    var listening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var response by rememberSaveable { mutableStateOf<String?>(null) }
    var recommendedProfile by remember(aiContext.selectedGamePackage) {
        mutableStateOf<UltraScopedProfileRecommendation?>(null)
    }
    var pendingContinuousListening by rememberSaveable { mutableStateOf(false) }
    var showTextChat by rememberSaveable { mutableStateOf(false) }
    var chatMessage by rememberSaveable { mutableStateOf("") }
    val chatSending by queryRunner.isRunning.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun sendTypedChatMessage() {
        val message = chatMessage.trim()
        if (!latestAssistantInputEnabled || message.isBlank() || chatSending) return

        val turnAiContext = latestAiContext
        val originatingGamePackage = turnAiContext.selectedGamePackage
        val conversationAtStart = latestConversation
        val previousConversation = conversationAtStart.takeLast(MAX_CHAT_HISTORY - 1)
        val resetConversationAfterCommand =
            UltraMemoryTurnPersistencePolicy.resetsConversationContext(message)
        val withUser = UltraConversationPolicy.append(
            history = conversationAtStart,
            entry = "Tú: " + message,
            maxEntries = MAX_CHAT_HISTORY
        )

        val failureAnswer = "No pude completar la consulta. Inténtalo de nuevo."
        val submission = queryRunner.launch(
            onAccepted = {
                chatMessage = ""
                latestOnConversationChanged(withUser)
            },
            onFailure = {
                val published = queryRunner.appendAssistantIfCurrentGame(
                    originatingGamePackage = originatingGamePackage,
                    assistantEntry = "Ultra: " + failureAnswer,
                    maxEntries = MAX_CHAT_HISTORY
                )
                if (published) {
                    withContext(Dispatchers.Main) {
                        response = failureAnswer
                    }
                }
            }
        ) {
            val deviceStatus = VoiceDeviceStatusProvider.read(context)
            val route = agentRouter.route(
                UltraAgentRoutingRequest(
                    transcript = message.trim(),
                    conversationHistory = previousConversation,
                    optionalResolver = aiIntentResolver,
                    telemetry = UltraRuntimeTelemetry(
                        batteryPercent = deviceStatus.batteryPercent,
                        thermalLabel = deviceStatus.thermalLabel,
                        refreshRateHz = turnAiContext.refreshRateHz
                    ),
                    knownGameAliases = aliasRepository.aliases().keys
                )
            )

            when (route) {
                is UltraAgentRoute.Utility -> {
                    val answer = route.answer.message
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    if (published) {
                        withContext(Dispatchers.Main) {
                            response = answer
                        }
                    }
                }

                is UltraAgentRoute.Chat -> {
                    val answer = queryExecutor.answer(
                        route = route,
                        stableKnowledgeFallback = {
                            aiAdvisor.generalKnowledgeChatOrNull(
                                message = route.message,
                                context = turnAiContext,
                                conversation = previousConversation
                            )
                        }
                    ) {
                        aiAdvisor.chat(
                            message = route.message,
                            context = turnAiContext,
                            conversation = previousConversation
                        )
                    }
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY,
                        resetConversation = resetConversationAfterCommand
                    )
                    if (published) {
                        withContext(Dispatchers.Main) {
                            response = answer
                        }
                    }
                }

                is UltraAgentRoute.Command -> {
                    val result = VoiceCommandEngine.execute(
                        command = route.command,
                        gamesProvider = { GameLibrary.discover(context).games },
                        launchGame = { packageName ->
                            GameLauncher.launch(context, packageName)
                        },
                        saveSelectedGame = onVoiceSelectedGame,
                        saveSelectedProfile = onVoiceSelectedProfile,
                        saveSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                        isProfileAvailable = { _ -> true },
                        statusProvider = { VoiceDeviceStatusProvider.read(context) },
                        aiAdvisor = { question ->
                            aiAdvisor.advise(question, latestAiContext)
                        },
                        aliasIntentResolver = aiIntentResolver,
                        gameAliasesProvider = { aliasRepository.aliases() },
                        saveGameAlias = { alias, packageName ->
                            aliasRepository.save(alias, packageName)
                        },
                        networkStatusProvider = {
                            VoiceNetworkSnapshotFactory.current(context)
                        },
                        applyNetworkProfile = networkGaming::applyProfile
                    )
                    val answer = VoiceResponseFormatter.format(context, result)
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY
                    )

                    if (published) {
                        withContext(Dispatchers.Main) {
                            if (result is VoiceActionResult.GameOpened) {
                                onGameSelected(result.game.packageName)
                            }
                            UltraCommandUiEffectPolicy
                                .profileForCurrentGameCallback(result)
                                ?.let(onProfileSelected)
                            recommendedProfile =
                                UltraCommandUiEffectPolicy.recommendationForUserApply(
                                    result = result,
                                    gamePackage = originatingGamePackage
                                )
                            response = answer
                        }
                    }
                }
            }
        }
        if (submission is UltraAssistantQuerySubmission.Rejected) {
            return
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted && pendingContinuousListening) {
            pendingContinuousListening = false
            when (continuousVoiceController.enableAfterPermissionGranted()) {
                ContinuousVoiceChange.ENABLED -> {
                    continuousListeningEnabled = true
                    response = "Escucha continua activada."
                }
                ContinuousVoiceChange.PERMISSION_REQUIRED -> {
                    continuousListeningEnabled = false
                    response = context.getString(R.string.voice_permission_required)
                }
                ContinuousVoiceChange.DISABLED -> Unit
            }
        } else if (!granted) {
            pendingContinuousListening = false
            continuousListeningEnabled = continuousVoiceController.isEnabled()
            response = context.getString(R.string.voice_permission_required)
        }
    }

    val voiceController = remember(context) {
        lateinit var controller: VoiceAssistantController
        controller = VoiceAssistantController(
            context = context,
            onListeningChanged = { listening = it },
            onTranscript = transcript@{ spokenText ->
                val turnAiContext = latestAiContext
                val capturedVoiceScope = captureUltraVoiceTurnScope(
                    assistantInputReady = latestAssistantInputEnabled,
                    gamePackage = turnAiContext.selectedGamePackage
                ) ?: return@transcript
                val originatingGamePackage = capturedVoiceScope.gamePackage
                transcript = spokenText
                scope.launch(Dispatchers.IO) {
                    if (
                        !isUltraVoiceTurnScopeCurrent(
                            captured = capturedVoiceScope,
                            assistantInputReady = latestAssistantInputEnabled,
                            currentGamePackage = latestAiContext.selectedGamePackage
                        )
                    ) {
                        return@launch
                    }
                    val conversationAtStart = latestConversation
                    val conversationBeforeTurn =
                        conversationAtStart.takeLast(MAX_CHAT_HISTORY - 1)
                    val withUser = UltraConversationPolicy.append(
                        history = conversationAtStart,
                        entry = "Tú: " + spokenText,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (
                            UltraConversationScopePolicy.isSameGame(
                                originatingGamePackage,
                                latestAiContext.selectedGamePackage
                            )
                        ) {
                            latestOnConversationChanged(withUser)
                        }
                    }

                    val voiceStatus = VoiceDeviceStatusProvider.read(context)
                    val route = agentRouter.route(
                        UltraAgentRoutingRequest(
                            transcript = spokenText,
                            optionalResolver = aiIntentResolver,
                            telemetry = UltraRuntimeTelemetry(
                                batteryPercent = voiceStatus.batteryPercent,
                                thermalLabel = voiceStatus.thermalLabel,
                                refreshRateHz = turnAiContext.refreshRateHz
                            ),
                            knownGameAliases = aliasRepository.aliases().keys,
                            conversationHistory = conversationBeforeTurn
                        )
                    )
                    when (route) {
                        is UltraAgentRoute.Utility -> {
                            val answer =
                                if (
                                    route.answer.intent is
                                        com.cardenaspiero255.gamehubultra.ai.UltraUtilityIntent.NetworkGamingControl
                                ) {
                                    networkGaming.execute(route.answer.intent)
                                } else {
                                    route.answer.message
                                }
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + answer,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    latestOnConversationChanged(withAnswer)
                                    response = answer
                                    controller.speak(answer)
                                }
                            }
                        }

                        is UltraAgentRoute.Chat -> {
                            val answer = queryExecutor.answer(
                                route = route,
                                stableKnowledgeFallback = {
                                    aiAdvisor.generalKnowledgeChatOrNull(
                                        message = route.message,
                                        context = turnAiContext,
                                        conversation = conversationBeforeTurn
                                    )
                                }
                            ) {
                                aiAdvisor.chat(
                                    message = route.message,
                                    context = turnAiContext,
                                    conversation = conversationBeforeTurn
                                )
                            }
                            val withAnswer =
                                if (
                                    UltraMemoryTurnPersistencePolicy
                                        .resetsConversationContext(route.message)
                                ) {
                                    listOf("Ultra: " + answer)
                                } else {
                                    UltraConversationPolicy.append(
                                        history = withUser,
                                        entry = "Ultra: " + answer,
                                        maxEntries = MAX_CHAT_HISTORY
                                    )
                                }
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    latestOnConversationChanged(withAnswer)
                                    response = answer
                                    controller.speak(answer)
                                }
                            }
                        }

                        is UltraAgentRoute.Command -> {
                            val result = VoiceCommandEngine.execute(
                                command = route.command,
                                gamesProvider = { GameLibrary.discover(context).games },
                                launchGame = { packageName ->
                                    GameLauncher.launch(context, packageName)
                                },
                                saveSelectedGame = onVoiceSelectedGame,
                                saveSelectedProfile = onVoiceSelectedProfile,
                                saveSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                                // X4 is an app profile. Hardware Sustained Performance Mode is
                                // applied opportunistically by PerformanceController when supported.
                                isProfileAvailable = { _ -> true },
                                statusProvider = { VoiceDeviceStatusProvider.read(context) },
                                aiAdvisor = { question ->
                                    aiAdvisor.advise(question, latestAiContext)
                                },
                                aliasIntentResolver = aiIntentResolver,
                                gameAliasesProvider = { aliasRepository.aliases() },
                                saveGameAlias = { alias, packageName ->
    aliasRepository.save(alias, packageName)
},
networkStatusProvider = { VoiceNetworkSnapshotFactory.current(context) },
applyNetworkProfile = networkGaming::applyProfile
                            )
                            val spokenResponse = VoiceResponseFormatter.format(context, result)
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + spokenResponse,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (result is VoiceActionResult.GameOpened) {
                                    onGameSelected(result.game.packageName)
                                }
                                UltraCommandUiEffectPolicy
                                    .profileForCurrentGameCallback(result)
                                    ?.let(onProfileSelected)
                                val recommended =
                                    UltraCommandUiEffectPolicy.recommendationForUserApply(
                                        result = result,
                                        gamePackage = originatingGamePackage
                                    )
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    recommendedProfile = recommended
                                    latestOnConversationChanged(withAnswer)
                                    response = spokenResponse
                                    controller.speak(spokenResponse)
                                }
                            }
                        }
                    }
                }
            },
            onError = {
                response = context.getString(R.string.voice_recognition_error)
            }
        )
        controller
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    if (showTextChat) {
        AlertDialog(
            onDismissRequest = { if (!chatSending) showTextChat = false },
            title = { Text("Ultra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        latestConversation.takeLast(MAX_CHAT_HISTORY).forEach { entry ->
                            Text(entry, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_chat_input_label)) },
                        placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                        enabled = assistantInputEnabled && !chatSending,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = ::sendTypedChatMessage,
                    enabled = assistantInputEnabled && chatMessage.isNotBlank() && !chatSending
                ) {
                    Text(
                        if (chatSending) {
                            stringResource(R.string.ai_chat_thinking)
                        } else {
                            stringResource(R.string.ai_chat_send)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        latestOnConversationChanged(emptyList())
                        chatMessage = ""
                    },
                    enabled = !chatSending
                ) {
                    Text(stringResource(R.string.ai_chat_clear))
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.voice_assistant_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.voice_assistant_subtitle))
            if (!assistantInputEnabled) {
                Text(
                    "Preparando el contexto de Ultra…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { showTextChat = true },
                enabled = assistantInputEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ai_chat_input_label))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Escucha continua", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Responde solo cuando digas “Ultra”.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = continuousListeningEnabled,
                    enabled = assistantInputEnabled,
                    onCheckedChange = { enabled ->
                        when (continuousVoiceController.setEnabled(enabled)) {
                            ContinuousVoiceChange.PERMISSION_REQUIRED -> {
                                pendingContinuousListening = true
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            ContinuousVoiceChange.ENABLED -> {
                                pendingContinuousListening = false
                                continuousListeningEnabled = true
                                response = "Escucha continua activada."
                            }
                            ContinuousVoiceChange.DISABLED -> {
                                pendingContinuousListening = false
                                continuousListeningEnabled = false
                                response = "Escucha continua desactivada."
                            }
                        }
                    }
                )
            }
            Button(
                onClick = {
                    if (permissionGranted) {
                        if (listening) {
                            voiceController.stopListening()
                        } else {
                            voiceController.startListening()
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = assistantInputEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        listening -> stringResource(R.string.voice_stop)
                        permissionGranted -> stringResource(R.string.voice_start)
                        else -> stringResource(R.string.voice_permission_button)
                    }
                )
            }
            Text(
                stringResource(
                    R.string.voice_selected_profile,
                    selectedProfileName
                )
            )
            if (transcript.isNotBlank()) {
                Text(stringResource(R.string.voice_transcript, transcript))
            }
            response?.let { Text(it) }
            UltraCommandUiEffectPolicy.profileForCurrentGame(
                recommendation = recommendedProfile,
                currentGamePackage = aiContext.selectedGamePackage
            )?.let { profile ->
                Button(
                    onClick = {
                        onProfileSelected(profile)
                        recommendedProfile = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar recomendación: ${profile.title}")
                }
            }
        }
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

internal fun packageVersionName(context: Context, packageName: String): String? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0L)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull()?.takeIf(String::isNotBlank)

internal fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }
    }.isSuccess

private fun openGame(context: Context, packageName: String): Boolean =
    GameLauncher.launch(context, packageName)

@Composable
internal fun localizedProfileTitle(
    profile: PerformanceProfile
): String =
    when (profile) {
        PerformanceProfile.BALANCED ->
            stringResource(R.string.profile_balanced_title)
        PerformanceProfile.FRAME_INTERPOLATION ->
            stringResource(R.string.profile_interpolation_title)
        PerformanceProfile.X4 ->
            stringResource(R.string.profile_x4_title)
    }

@Composable
internal fun localizedProfileDescription(
    profile: PerformanceProfile
): String =
    when (profile) {
        PerformanceProfile.BALANCED ->
            stringResource(R.string.profile_balanced_description)
        PerformanceProfile.FRAME_INTERPOLATION ->
            stringResource(R.string.profile_interpolation_description)
        PerformanceProfile.X4 ->
            stringResource(R.string.profile_x4_description)
    }

@Composable
internal fun thermalLabel(status: Int?): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE ->
            stringResource(R.string.normal)
        PowerManager.THERMAL_STATUS_LIGHT ->
            stringResource(R.string.thermal_light)
        PowerManager.THERMAL_STATUS_MODERATE ->
            stringResource(R.string.thermal_moderate)
        PowerManager.THERMAL_STATUS_SEVERE ->
            stringResource(R.string.thermal_severe)
        PowerManager.THERMAL_STATUS_CRITICAL ->
            stringResource(R.string.thermal_critical)
        PowerManager.THERMAL_STATUS_EMERGENCY ->
            stringResource(R.string.thermal_emergency)
        PowerManager.THERMAL_STATUS_SHUTDOWN ->
            stringResource(R.string.thermal_shutdown)
        null ->
            stringResource(R.string.not_available)
        else ->
            stringResource(R.string.thermal_unknown)
    }


private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()

@Composable
internal fun eventLabel(event: PerformanceEvent): String =
    when (event.type) {
        PerformanceEventType.SESSION_STARTED ->
            "• " + stringResource(R.string.event_session_started)
        PerformanceEventType.SESSION_ENDED ->
            "• " + stringResource(R.string.event_session_ended)
        PerformanceEventType.THERMAL_CHANGED ->
            "• " + stringResource(R.string.event_thermal_changed) +
                (event.detail.takeIf(String::isNotBlank)?.let { ": $it" } ?: "")
        PerformanceEventType.POLICY_CHANGED ->
            "• " + stringResource(R.string.event_policy_changed) +
                (event.profile?.title?.let { ": $it" } ?: "") +
                (event.score?.let { " ($it/100)" } ?: "")
    }

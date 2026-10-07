package com.cardenaspiero255.gamehubultra

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStateRepository
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKeyFactory
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.data.AppliedAiProfileProposalState
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
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.AiProfileBuilder
import com.cardenaspiero255.gamehubultra.domain.AiProfileCapabilities
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OptimizationObservation
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.AdaptivePerformanceEngine
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.data.PerGameAdaptiveStatePreferencesStore
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
import com.cardenaspiero255.gamehubultra.session.installedGameVersionKey
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
import com.cardenaspiero255.gamehubultra.ui.home.SmartRecommendationActions
import com.cardenaspiero255.gamehubultra.ui.home.SmartRecommendationRevertPolicy
import com.cardenaspiero255.gamehubultra.ui.home.SmartRecommendationRevertTarget
import com.cardenaspiero255.gamehubultra.ui.library.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CHAT_HISTORY = 8
private const val COACH_RUNTIME_START_TOLERANCE_MS = 5_000L

internal fun completedCoachBelongsToRuntimeSession(
    completed: com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession,
    active: GameSessionRecord?
): Boolean {
    val runtime = active ?: return false
    if (completed.packageName != runtime.packageName) return false
    val startDelta = kotlin.math.abs(completed.startedAtMillis - runtime.startedAtMillis)
    if (startDelta > COACH_RUNTIME_START_TOLERANCE_MS) return false
    val completedAt = completed.endedAtMillis ?: return false
    return completedAt >= runtime.startedAtMillis
}

internal data class CompletedCoachHydration(
    val report: com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport,
    val observations: List<com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage>,
    val shouldMarkHydrated: Boolean,
    val shouldEndRuntimeSession: Boolean
)

internal fun buildCompletedCoachHydration(
    completed: com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession,
    hydratedSessionId: String?,
    activeRuntimeRecord: GameSessionRecord?,
    selectionHydrated: Boolean = true
): CompletedCoachHydration {
    val shouldMarkHydrated =
        selectionHydrated && completed.sessionId != hydratedSessionId
    return CompletedCoachHydration(
        report = com.cardenaspiero255.gamehubultra.domain.AiSessionCoach.postSession(
            completed.samples
        ),
        observations = listOfNotNull(completed.latestObservation),
        shouldMarkHydrated = shouldMarkHydrated,
        shouldEndRuntimeSession =
            shouldMarkHydrated &&
                completedCoachBelongsToRuntimeSession(completed, activeRuntimeRecord)
    )
}

internal fun chooseCoachReport(
    storedReport: com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport?,
    dashboardReport: com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport?
): com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport? =
    storedReport ?: dashboardReport


internal fun aiProfileSamplesForSelectedGame(
    completed: com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession?,
    selectedPackage: String?,
    activeSessionPackage: String?,
    dashboardSamples: List<SessionCoachSnapshot>
): List<SessionCoachSnapshot> {
    val selected = selectedPackage?.trim()?.takeIf(String::isNotEmpty)
        ?: return emptyList()
    if (completed?.packageName == selected && completed.samples.isNotEmpty()) {
        return completed.samples
    }
    return if (activeSessionPackage == selected) dashboardSamples else emptyList()
}


internal fun buildAiProfileProposalForSelectedGame(
    packageName: String?,
    selectedConfig: GameProfileConfig?,
    effectiveProfile: PerformanceProfile,
    observations: List<OptimizationObservation>,
    sessionSamples: List<SessionCoachSnapshot>,
    supportedRefreshRatesHz: Set<Int>?,
    supportsSustainedPerformance: Boolean,
    nextVersion: (String) -> Int
): AiProfileProposal? {
    if (packageName.isNullOrBlank() || supportedRefreshRatesHz == null) return null
    val currentConfig = selectedConfig
        ?: GameProfileConfig(performanceProfile = effectiveProfile)
    return AiProfileBuilder.propose(
        currentConfig = currentConfig,
        observations = observations,
        sessionSamples = sessionSamples,
        capabilities = AiProfileCapabilities(
            supportsSustainedPerformance = supportsSustainedPerformance,
            supportsFrameInterpolation = false,
            supportedRefreshRatesHz = supportedRefreshRatesHz,
            supportedResolutions = emptySet()
        ),
        version = nextVersion(packageName)
    ).takeIf { it.requiresExplicitApply }
}

internal fun applyAiProfileProposalForSelectedGame(
    packageName: String?,
    proposal: AiProfileProposal?,
    save: (String, GameProfileConfig, () -> Unit) -> Unit,
    recordApplied: (String, AiProfileProposal) -> Unit,
    onApplied: () -> Unit
): Boolean {
    if (packageName.isNullOrBlank() || proposal == null) return false
    save(packageName, proposal.proposedConfig) {
        recordApplied(packageName, proposal)
        onApplied()
    }
    return true
}

internal fun rollbackAiProfileProposalForSelectedGame(
    packageName: String?,
    rollback: AppliedAiProfileProposalState?,
    save: (String, GameProfileConfig, () -> Unit) -> Unit,
    clearRollback: (String) -> Unit,
    onRolledBack: () -> Unit
): Boolean {
    if (packageName.isNullOrBlank() || rollback == null) return false
    save(packageName, rollback.previousKnownGoodConfig) {
        clearRollback(packageName)
        onRolledBack()
    }
    return true
}

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
    aiProfileProposalStore: AiProfileProposalStore,
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
    var sessionCoachSamples by remember { mutableStateOf<List<com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot>>(emptyList()) }
    var sessionCoachObservations by remember { mutableStateOf<List<com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage>>(emptyList()) }
    var lastSessionCoachReport by remember { mutableStateOf<com.cardenaspiero255.gamehubultra.domain.SessionCoachPostSessionReport?>(null) }
    var lastCompletedCoachSession by remember {
        mutableStateOf<com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession?>(null)
    }
    var hydratedCoachSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var storeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var appResumeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var aiProfileRevision by rememberSaveable { mutableIntStateOf(0) }
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
    val currentOptimizationKey = remember(selectedGameForMemory, selectedGameVersion, device) {
        OptimizationContextKeyFactory.from(device, selectedGameForMemory, selectedGameVersion, EmulatorBackendDetector.detect())
    }
    val optimizationObservations by optimizationMemoryStore
        .observationsFlow(currentOptimizationKey)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val aiProfileSessionSamples = remember(
        lastCompletedCoachSession,
        selectedGameForMemory,
        activeSessionPackage,
        sessionCoachSamples
    ) {
        aiProfileSamplesForSelectedGame(
            completed = lastCompletedCoachSession,
            selectedPackage = selectedGameForMemory,
            activeSessionPackage = activeSessionPackage,
            dashboardSamples = sessionCoachSamples
        )
    }
    val aiProfileProposal = remember(
        selectedGameForMemory,
        uiState.selectedGameConfig,
        optimizationObservations,
        aiProfileSessionSamples,
        runtimeDiagnostics,
        initialState.capabilities,
        aiProfileRevision
    ) {
        buildAiProfileProposalForSelectedGame(
            packageName = selectedGameForMemory,
            selectedConfig = uiState.selectedGameConfig,
            effectiveProfile = uiState.effectiveProfile,
            observations = optimizationObservations,
            sessionSamples = aiProfileSessionSamples,
            supportedRefreshRatesHz = runtimeDiagnostics?.refresh?.supportedRefreshRatesHz,
            supportsSustainedPerformance =
                initialState.capabilities?.sustainedPerformanceSupported == true,
            nextVersion = aiProfileProposalStore::nextVersion
        )
    }
    val aiProfileRollbackState = remember(
        selectedGameForMemory,
        aiProfileRevision
    ) {
        selectedGameForMemory?.let(aiProfileProposalStore::rollbackState)
    }

    LaunchedEffect(storeRefreshToken) {
        storeGames = withContext(Dispatchers.IO) {
            storeLibraryRepository.getAll()
        }
    }
    val adaptiveEngine = remember(uiState.effectiveProfile) {
        AdaptivePerformanceEngine(initialProfile = uiState.effectiveProfile)
    }
    val perGameAdaptiveStateStore = remember(context) {
        PerGameAdaptiveStatePreferencesStore(context)
    }
    val perGameAdaptiveOptimizer = remember(perGameAdaptiveStateStore) {
        PerGameAdaptiveOptimizer(stateStore = perGameAdaptiveStateStore)
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
                    sessionCoachSamples = update.coachSamples
                    if (sessionId != null) {
                        sessionCoachObservations = update.coachObservations
                    }
                    lastSessionCoachReport = chooseCoachReport(
                        storedReport = lastSessionCoachReport,
                        dashboardReport = update.lastCompletedCoachReport
                    )
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

    fun applyAiProfileProposal() {
        applyAiProfileProposalForSelectedGame(
            packageName = selectedGameForMemory,
            proposal = aiProfileProposal,
            save = viewModel::saveGameProfileConfig,
            recordApplied = aiProfileProposalStore::recordApplied,
            onApplied = { aiProfileRevision += 1 }
        )
    }

    fun rollbackAiProfileProposal() {
        rollbackAiProfileProposalForSelectedGame(
            packageName = selectedGameForMemory,
            rollback = aiProfileRollbackState,
            save = viewModel::saveGameProfileConfig,
            clearRollback = aiProfileProposalStore::clearRollback,
            onRolledBack = { aiProfileRevision += 1 }
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

    LaunchedEffect(
        appResumeRefreshToken,
        uiState.selectedGameHydrated,
        selectedGameForMemory
    ) {
        val completed = withContext(Dispatchers.IO) {
            com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore(context)
                .readLastCompletedSession()
        }
        lastCompletedCoachSession = completed
        if (completed != null) {
            val activeRuntimeRecord = sessionHistory.firstOrNull { session ->
                session.id == runtimeGameSession?.id
            }
            val hydration = buildCompletedCoachHydration(
                completed = completed,
                hydratedSessionId = hydratedCoachSessionId,
                activeRuntimeRecord = activeRuntimeRecord,
                selectionHydrated = uiState.selectedGameHydrated
            )
            lastSessionCoachReport = hydration.report
            sessionCoachObservations = hydration.observations
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = perGameAdaptiveOptimizer,
                nowMillis = System.currentTimeMillis(),
                wasSessionHandled = {
                    perGameAdaptiveStateStore.wasSessionHandled(completed.sessionId)
                },
                resolveActiveProfile = {
                    viewModel.effectiveProfileForGame(completed.packageName)
                },
                persistProfile = viewModel::persistGameProfile,
                markSessionHandled = {
                    perGameAdaptiveStateStore.markSessionHandled(completed.sessionId)
                },
                recordPerformanceEvent = viewModel::recordPerformanceEvent
            )
            if (hydration.shouldMarkHydrated) {
                hydratedCoachSessionId = completed.sessionId
            }
            if (hydration.shouldEndRuntimeSession) {
                val last = completed.samples.lastOrNull()
                runtimeCoordinator.endGameSession(
                    runtimeSnapshot().copy(
                        metrics = RuntimeSessionMetrics(
                            batteryPercent = last?.batteryPercent
                                ?: runtimeDiagnostics?.battery?.percent,
                            thermalStatus = last?.thermalStatus
                                ?: runtimeDiagnostics?.thermal?.status,
                            ramUsedPercent = runtimeDiagnostics?.memory?.usedPercent,
                            diagnosticsAvailable = last != null || runtimeDiagnostics != null
                        )
                    )
                )
            }
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

    val sessionCoachPreMessage = runtimeDiagnostics?.let { diagnostics ->
        com.cardenaspiero255.gamehubultra.domain.AiSessionCoach.preSession(
            readiness =
                com.cardenaspiero255.gamehubultra.session.SessionCoachTelemetryMapper.readiness(
                    device,
                    diagnostics
                ),
            snapshot =
                com.cardenaspiero255.gamehubultra.session.SessionCoachTelemetryMapper.snapshot(
                    timestampMillis = 0L,
                    diagnostics = diagnostics
                )
        )
    }

    var smartRecommendationRevertTarget by remember(selectedGamePackage) { mutableStateOf<SmartRecommendationRevertTarget?>(null) }

    LaunchedEffect(selectedGamePackage, uiState.effectiveProfile) {
        smartRecommendationRevertTarget = SmartRecommendationActions.onProfileChanged(
            target = smartRecommendationRevertTarget,
            gamePackage = selectedGamePackage,
            currentProfile = uiState.effectiveProfile
        )
    }

    fun clearSmartRecommendationRevertForExternalProfileChange() {
        smartRecommendationRevertTarget =
            SmartRecommendationActions.onExternalProfileSelection(
                smartRecommendationRevertTarget
            )
    }

    fun recordExplicitAdaptiveProfileSelection(
        packageName: String?,
        profile: PerformanceProfile
    ) {
        val cleanPackage = packageName?.trim()?.takeIf(String::isNotEmpty) ?: return
        val version = installedGameVersionKey(context, cleanPackage) ?: "unknown"
        perGameAdaptiveOptimizer.recordExplicitProfileSelection(
            key = AdaptiveGameKey(cleanPackage, version),
            profile = profile
        )
    }

    fun selectExternalProfile(profile: PerformanceProfile) {
        clearSmartRecommendationRevertForExternalProfileChange()
        recordExplicitAdaptiveProfileSelection(selectedGameForMemory, profile)
        selectProfile(profile)
    }

    fun applyExternalSmartGameAssistantSuggestion(
        suggestion: SmartGameAssistantSuggestion
    ) {
        clearSmartRecommendationRevertForExternalProfileChange()
        recordExplicitAdaptiveProfileSelection(
            selectedGameForMemory,
            suggestion.profile
        )
        applySmartGameAssistantSuggestion(suggestion)
    }

    fun persistExternalVoiceProfile(profile: PerformanceProfile) {
        clearSmartRecommendationRevertForExternalProfileChange()
        viewModel.persistVoiceSelectedProfile(profile)
    }

    fun persistExternalVoiceGameWithProfile(
        packageName: String,
        profile: PerformanceProfile
    ) {
        clearSmartRecommendationRevertForExternalProfileChange()
        recordExplicitAdaptiveProfileSelection(packageName, profile)
        viewModel.persistVoiceSelectedGameWithProfile(packageName, profile)
    }

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
    val smartRecommendationRejectionKey = SmartRecommendationRevertPolicy.rejectionKey(currentOptimizationKey, smartRecommendation.profile, uiState.effectiveProfile, optimizationObservations)
    var rejectedSmartRecommendation by remember(smartRecommendationRejectionKey) { mutableStateOf(false) }
    val canRevertSmartRecommendation = SmartRecommendationRevertPolicy.canRevert(smartRecommendationRevertTarget, selectedGamePackage, uiState.effectiveProfile)

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

    val recordSmartRecommendationFeedback: (PerformanceProfile, OptimizationFeedbackDecision) -> Unit =
        { profile, feedback -> runtimeCoordinator.recordRecommendationFeedback(runtimeSnapshot(), profile, feedback) }

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
                onProfileSelected = ::selectExternalProfile,
                onGameSelected = ::selectGame,
                onPlaySelectedGame = ::playSelectedGame,
                runtimeDiagnostics = runtimeDiagnostics,
                telemetryTrend = telemetryTrend,
                performanceTimeline = performanceTimeline,
                sessionHistory = sessionHistory,
                sessionCoachPreMessage = sessionCoachPreMessage,
                sessionCoachSamples = sessionCoachSamples,
                sessionCoachObservations = sessionCoachObservations,
                lastSessionCoachReport = lastSessionCoachReport,
                onClearSessions = {
                    viewModel.clearSessionHistory()
                },
                onShareSessions = { shareSessionHistory(context, sessionHistory) },
                adaptiveDecision = adaptiveDecision,
                smartRecommendation = smartRecommendation,
                canRevertSmartRecommendation = canRevertSmartRecommendation,
                onApplySmartRecommendation = {
                    smartRecommendationRevertTarget = SmartRecommendationActions.apply(selectedGamePackage, uiState.effectiveProfile, smartRecommendation.profile, recordSmartRecommendationFeedback, ::selectProfile, smartRecommendationRevertTarget)
                },
                onRejectSmartRecommendation = {
                    rejectedSmartRecommendation = SmartRecommendationActions.reject(rejectedSmartRecommendation, smartRecommendation.profile, recordSmartRecommendationFeedback)
                },
                onRevertSmartRecommendation = {
                    smartRecommendationRevertTarget = SmartRecommendationActions.revert(smartRecommendationRevertTarget, selectedGamePackage, uiState.effectiveProfile, recordSmartRecommendationFeedback, ::selectProfile)
                },
                smartGameAssistantSuggestions = smartGameAssistantSuggestions,
                onApplySmartGameAssistant = ::applyExternalSmartGameAssistantSuggestion,
                aiProfileProposal = aiProfileProposal,
                canRollbackAiProfileProposal = aiProfileRollbackState != null,
                onApplyAiProfileProposal = ::applyAiProfileProposal,
                onRollbackAiProfileProposal = ::rollbackAiProfileProposal,
                optimizationObservations = optimizationObservations,
                onClearOptimizationMemory = {
                    scope.launch(Dispatchers.IO) {
                        optimizationMemoryStore.clearGame(currentOptimizationKey)
                    }
                },
                performanceHistory = performanceHistory,
                onApplyAdaptiveProfile = {
                    adaptiveDecision?.let { selectExternalProfile(it.profile) }
                },
                aiContext = aiContext,
                ultraRuntime = ultraRuntime,
                queryRunner = ultraQueryRunner,
                conversation = ultraConversation,
                onConversationChanged = ultraSessionController::updateConversation,
                assistantInputEnabled = ultraAssistantInputReady,
                onVoiceSelectedGame = viewModel::persistVoiceSelectedGame,
                onVoiceSelectedProfile = ::persistExternalVoiceProfile,
                onVoiceSelectedGameWithProfile = ::persistExternalVoiceGameWithProfile,
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
                onProfileSelected = ::selectExternalProfile,
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
                        onProfileSelected = ::selectExternalProfile,
                        onGameSelected = ::selectGame,
                        onVoiceSelectedGame = viewModel::persistVoiceSelectedGame,
                        onVoiceSelectedProfile = ::persistExternalVoiceProfile,
                        onVoiceSelectedGameWithProfile = ::persistExternalVoiceGameWithProfile
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


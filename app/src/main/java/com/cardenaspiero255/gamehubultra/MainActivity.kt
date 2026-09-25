package com.cardenaspiero255.gamehubultra

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvice
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraConversationPolicy
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import com.cardenaspiero255.gamehubultra.ai.UltraRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.SessionEndMetrics
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStore
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStore
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.data.StoreLibraryStore
import com.cardenaspiero255.gamehubultra.store.StoreConnectionActivity
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.os.PowerManager
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import com.cardenaspiero255.gamehubultra.domain.AdaptiveRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessCalculator
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessInput
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceEventType
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAX_CHAT_HISTORY = 8

internal fun shouldRevealQuickVoiceControls(wasOpen: Boolean, isOpen: Boolean): Boolean =
    !wasOpen && isOpen


class MainActivity : ComponentActivity() {
    private lateinit var performanceController: PerformanceController

    override fun onStart() {
        super.onStart()
        if (
            continuousListeningEnabled(this) &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceWakeService(this)
        }
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTab = when (intent?.data?.host) {
            "library" -> 1
            else -> 0
        }

        val capabilities = DeviceCapabilitiesProvider.get(this)
        performanceController = PerformanceController(capabilities)
        val initialState = performanceController.apply(PerformanceProfile.BALANCED, window)
        val device = DeviceInfoProvider.get(this)

        setContent {
            val gameHubViewModel: GameHubViewModel = viewModel()
            GameHubUltraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    GameHubUltraApp(
                        initialState = initialState,
                        device = device,
                        viewModel = gameHubViewModel,
                        initialTab = initialTab,
                        onProfileApplied = { profile ->
                            performanceController.apply(profile, window)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
private fun GameHubUltraApp(
    initialState: PerformanceState,
    device: DeviceInfo,
    viewModel: GameHubViewModel,
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
    var latencyMs by remember { mutableStateOf<Long?>(null) }
    var telemetryTrend by remember { mutableStateOf<List<RuntimeDiagnostics>>(emptyList()) }
    var performanceTimelineSamples by remember { mutableStateOf<List<com.cardenaspiero255.gamehubultra.domain.PerformanceTimelineSample>>(emptyList()) }
    var storeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var appResumeRefreshToken by rememberSaveable { mutableIntStateOf(0) }
    var storeGames by remember { mutableStateOf<List<StoreLibraryGame>>(emptyList()) }
    val sessionHistory by viewModel.sessionHistory.collectAsStateWithLifecycle(initialValue = emptyList())

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
    val optimizationMemoryStore = remember(context) { GameOptimizationMemoryStore(context) }
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
            StoreLibraryStore(context).getAll()
        }
    }
    val adaptiveEngine = remember(uiState.effectiveProfile) {
        AdaptivePerformanceEngine(initialProfile = uiState.effectiveProfile)
    }
    val ultraMemoryStore = remember {
        UltraConversationMemoryStore.get(context.applicationContext)
    }
    val aiAdvisor = remember(ultraMemoryStore) {
        GameHubAiAdvisor(
            modelAdapter = GeminiNanoLocalAiModelAdapter(),
            memoryGateway = ultraMemoryStore
        )
    }
    var ultraConversation by rememberSaveable { mutableStateOf(listOf<String>()) }

    LaunchedEffect(ultraMemoryStore, uiState.selectedGamePackage) {
        if (ultraConversation.isNotEmpty()) return@LaunchedEffect
        val memoryScope = UltraMemoryScope(
            userId = "local",
            gamePackage = uiState.selectedGamePackage
        )
        val loaded = withContext(Dispatchers.IO) {
            ultraMemoryStore.warmUp()
            ultraMemoryStore.recentConversationLines(
                limit = MAX_CHAT_HISTORY,
                scope = memoryScope
            )
        }
        if (ultraConversation.isEmpty()) {
            ultraConversation = loaded
        }
    }

    fun updateUltraConversation(next: List<String>) {
        val previous = ultraConversation
        ultraConversation = next
        val memoryScope = UltraMemoryScope(
            userId = "local",
            gamePackage = uiState.selectedGamePackage
        )
        val timestampMillis = System.currentTimeMillis()
        if (next.isEmpty()) {
            ultraMemoryStore.enqueueClearConversationHistory(userId = memoryScope.userId)
        } else {
            ultraMemoryStore.enqueueSyncConversation(
                previous = previous,
                next = next,
                scope = memoryScope,
                timestampMillis = timestampMillis
            )
        }
    }

    DisposableEffect(aiAdvisor) {
        onDispose { aiAdvisor.close() }
    }

    LaunchedEffect(lifecycleOwner, adaptiveEngine, activeSessionPackage, activeSessionId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val selectedGame = activeSessionPackage
            val sessionId = activeSessionId
            val telemetryPlan = dashboardTelemetryPlan(sessionId)
            var lastThermalStatus: Int? = null
            var initializedThermalStatus = false
            var lastLatencyCheckAt = 0L
            var lastLatencyNetworkHandle: Long? = null

            try {
                while (isActive && telemetryPlan.collectDashboardTelemetry) {
                    val diagnostics = withContext(Dispatchers.IO) {
                        RuntimeDiagnosticsProvider.get(context)
                    }
                    val now = System.currentTimeMillis()
                    val network = diagnostics.connectivity
                    if (!network.connected ||
                        !network.validated ||
                        network.networkHandle == null ||
                        network.metered
                    ) {
                        latencyMs = null
                        lastLatencyNetworkHandle = null
                        lastLatencyCheckAt = 0L
                    } else if (
                        network.networkHandle != lastLatencyNetworkHandle ||
                        now - lastLatencyCheckAt >= 30_000L
                    ) {
                        latencyMs = withContext(Dispatchers.IO) {
                            com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe.measure(
                                context = context,
                                expectedNetworkHandle = network.networkHandle
                            )
                        }
                        lastLatencyNetworkHandle = network.networkHandle
                        lastLatencyCheckAt = now
                    }

                    val enrichedDiagnostics = diagnostics.copy(
                        connectivity = network.copy(latencyMs = latencyMs)
                    )
                    runtimeDiagnostics = enrichedDiagnostics
                    telemetryTrend = (telemetryTrend + enrichedDiagnostics).takeLast(12)

                    if (telemetryPlan.recordSessionEvents && sessionId != null) {
                        performanceTimelineSamples = (
                            performanceTimelineSamples + PerformanceTimelineBuilder.sample(
                                timestampMillis = now,
                                batteryPercent = enrichedDiagnostics.battery.percent,
                                thermalStatus = enrichedDiagnostics.thermal.status,
                                thermalHeadroom = enrichedDiagnostics.thermal.headroom,
                                refreshRateHz = enrichedDiagnostics.refresh.currentRefreshRateHz,
                                ramUsedPercent = enrichedDiagnostics.memory.usedPercent
                            )
                        ).takeLast(24)

                        if (
                            initializedThermalStatus &&
                            diagnostics.thermal.status != lastThermalStatus
                        ) {
                            viewModel.recordPerformanceEvent(
                                PerformanceEvent(
                                    timestampMillis = now,
                                    type = PerformanceEventType.THERMAL_CHANGED,
                                    sessionId = sessionId,
                                    detail = diagnostics.thermal.status?.toString() ?: "unavailable"
                                )
                            )
                        }
                    } else {
                        performanceTimelineSamples = emptyList()
                    }

                    lastThermalStatus = diagnostics.thermal.status
                    initializedThermalStatus = true

                    val decision = adaptiveEngine.evaluate(
                        AdaptiveRuntimeSnapshot(
                            thermalStatus = diagnostics.thermal.status,
                            thermalHeadroom = diagnostics.thermal.headroom,
                            batteryPercent = diagnostics.battery.percent,
                            charging = diagnostics.battery.charging,
                            powerSaveMode = diagnostics.battery.powerSaveMode,
                            sessionActive = selectedGame != null,
                            sustainedPerformanceSupported =
                                initialState.capabilities?.sustainedPerformanceSupported == true,
                            performanceHintsAvailable =
                                initialState.capabilities?.performanceHintsAvailable == true
                        )
                    )
                    adaptiveDecision = decision

                    if (
                        decision.changed &&
                        telemetryPlan.recordSessionEvents &&
                        sessionId != null
                    ) {
                        viewModel.recordPerformanceEvent(
                            PerformanceEvent(
                                timestampMillis = now,
                                type = PerformanceEventType.POLICY_CHANGED,
                                sessionId = sessionId,
                                profile = decision.profile,
                                score = decision.score,
                                detail = decision.reason
                            )
                        )
                    }

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

    fun selectProfile(profile: PerformanceProfile) {
        val previous = uiState.effectiveProfile
        uiState.selectedGamePackage?.let { packageName ->
            viewModel.selectGameProfile(packageName, profile)
        } ?: viewModel.selectGlobalProfile(profile)
        if (previous != profile) {
            viewModel.recordPerformanceEvent(
                PerformanceEvent(
                    timestampMillis = System.currentTimeMillis(),
                    type = PerformanceEventType.POLICY_CHANGED,
                    sessionId = activeSessionId ?: "ui",
                    profile = profile,
                    detail = "manual_profile_selection"
                )
            )
        }
    }

    fun applySmartGameAssistantSuggestion(suggestion: SmartGameAssistantSuggestion) {
        uiState.selectedGamePackage?.let { packageName ->
            viewModel.applySmartGameAssistantSuggestion(packageName, suggestion)
        } ?: viewModel.selectGlobalProfile(suggestion.profile)
    }

    fun endGameSession() {
        val endedAt = System.currentTimeMillis()
        val diagnosticsAtEnd = runtimeDiagnostics
        val profileAtEnd = uiState.effectiveProfile
        val optimizationKeyAtEnd = currentOptimizationKey
        val finishHandle = viewModel.finishRuntimeGameSession(
            SessionEndMetrics(
                endedAtMillis = endedAt,
                endBatteryPercent = diagnosticsAtEnd?.battery?.percent,
                endThermalStatus = diagnosticsAtEnd?.thermal?.status,
                endRamUsedPercent = diagnosticsAtEnd?.memory?.usedPercent
            )
        ) ?: return

        viewModel.recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = endedAt,
                type = PerformanceEventType.SESSION_ENDED,
                sessionId = finishHandle.session.id,
                detail = finishHandle.session.packageName
            )
        )

        scope.launch {
            finishHandle.job.join()
            if (!finishHandle.job.isCancelled) {
                withContext(Dispatchers.IO) {
                    val thermalStatus = diagnosticsAtEnd?.thermal?.status
                    val highTemperature = thermalStatus != null && thermalStatus >= 4
                    optimizationMemoryStore.record(
                        optimizationKeyAtEnd,
                        OptimizationObservation(
                            contextKey = optimizationKeyAtEnd.serialized,
                            profile = profileAtEnd,
                            measuredFps = null,
                            stable = diagnosticsAtEnd?.let {
                                !highTemperature &&
                                    (it.thermal.status == null || it.thermal.status <= 2)
                            } == true,
                            failed = highTemperature,
                            highTemperature = highTemperature,
                            thermalStatus = thermalStatus,
                            batteryPercent = diagnosticsAtEnd?.battery?.percent,
                            errorReason = if (highTemperature) "thermal_pressure" else null,
                            timestampMillis = endedAt
                        )
                    )
                }
            }
        }
    }

    fun selectGame(packageName: String) {
        endGameSession()
        viewModel.selectGame(packageName)
    }

    fun recordGameOpened(packageName: String) {
        endGameSession()
        val sessionId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val record = GameSessionRecord(
            id = sessionId,
            packageName = packageName,
            profileName = uiState.effectiveProfile.name,
            startedAtMillis = startedAt,
            startBatteryPercent = runtimeDiagnostics?.battery?.percent
        )
        viewModel.beginRuntimeGameSession(record)
        viewModel.recordPerformanceEvent(
            PerformanceEvent(
                timestampMillis = startedAt,
                type = PerformanceEventType.SESSION_STARTED,
                sessionId = sessionId,
                detail = packageName
            )
        )
        viewModel.recordRecentGame(packageName)
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
                aiAdvisor = aiAdvisor,
                conversation = ultraConversation,
                onConversationChanged = ::updateUltraConversation,
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
                WideNavigationRail(
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
                        aiAdvisor = aiAdvisor,
                        conversation = ultraConversation,
                        onConversationChanged = ::updateUltraConversation,
                        selectedProfileName = selectedProfileName,
                        onProfileSelected = ::selectProfile,
                        onGameSelected = ::selectGame
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
private fun WideNavigationRail(
    selectedTab: Int,
    settingsOpen: Boolean,
    profileOpen: Boolean,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background
    ) {
        NavigationRailItem(
            selected = !settingsOpen && !profileOpen && selectedTab == 0,
            onClick = onHome,
            icon = { Text("⌂") },
            label = { Text("Inicio") },
            modifier = Modifier
                .testTag("nav_inicio")
                .semantics { contentDescription = "nav_inicio" }
        )
        NavigationRailItem(
            selected = !settingsOpen && !profileOpen && selectedTab == 1,
            onClick = onLibrary,
            icon = { Text("▦") },
            label = { Text("Biblioteca") },
            modifier = Modifier
                .testTag("nav_biblioteca")
                .semantics { contentDescription = "nav_biblioteca" }
        )
        NavigationRailItem(
            selected = profileOpen,
            onClick = onProfile,
            icon = { Text("◎") },
            label = { Text("Perfil") },
            modifier = Modifier
                .testTag("nav_perfil")
                .semantics { contentDescription = "nav_perfil" }
        )
        NavigationRailItem(
            selected = settingsOpen,
            onClick = onSettings,
            icon = { Text("⚙") },
            label = { Text("Ajustes") },
            modifier = Modifier
                .testTag("nav_ajustes")
                .semantics { contentDescription = "nav_ajustes" }
        )
    }
}

@Composable
private fun UltraAssistantSidePanel(
    aiContext: GameHubAiContext,
    aiAdvisor: GameHubAiAdvisor,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit
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
        AiAdvisorCard(
            context = aiContext,
            advisor = aiAdvisor,
            conversation = conversation,
            onConversationChanged = onConversationChanged,
            onProfileSelected = onProfileSelected
        )
        VoiceAssistantCard(
            selectedProfileName = selectedProfileName,
            onProfileSelected = onProfileSelected,
            onGameSelected = onGameSelected,
            aiContext = aiContext,
            aiAdvisor = aiAdvisor,
            conversation = conversation,
            onConversationChanged = onConversationChanged
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
    aiAdvisor: GameHubAiAdvisor,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
    manualGamePackages: Set<String>,
    storeGames: List<StoreLibraryGame>,
    gameCatalogRefreshToken: Int,
    onOpenLibrary: () -> Unit,
    showAssistantCards: Boolean
) {
    val timelineContext = LocalContext.current
    val recentGameNames = remember(recentGamePackages) {
        recentGamePackages.map { packageName ->
            packageDisplayName(timelineContext, packageName)
        }
    }
    var localGameCount by remember { mutableIntStateOf(0) }
    var quickVoiceOpen by rememberSaveable { mutableStateOf(false) }
    var quickVoiceRevealRequest by remember { mutableIntStateOf(0) }
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(quickVoiceRevealRequest) {
        if (quickVoiceRevealRequest > 0) {
            homeListState.animateScrollToItem(3)
        }
    }
    LaunchedEffect(timelineContext, manualGamePackages, gameCatalogRefreshToken) {
        localGameCount = withContext(Dispatchers.IO) {
            GameLibrary.discover(
                context = timelineContext,
                additionalPackages = manualGamePackages
            ).games.size
        }
    }
    LazyColumn(
        state = homeListState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GameHubUiTokens.compactHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
    ) {
        item {
            Text(
                stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.titleMedium
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
                gameCount = localGameCount,
                sessionCount = sessionHistory.size,
                onProfileSelected = onProfileSelected,
                onPlay = onPlaySelectedGame,
                onVoiceClick = {
                    val next = !quickVoiceOpen
                    if (shouldRevealQuickVoiceControls(quickVoiceOpen, next)) {
                        quickVoiceRevealRequest += 1
                    }
                    quickVoiceOpen = next
                }
            )
        }
        if (quickVoiceOpen) {
            item {
                VoiceAssistantCard(
                    selectedProfileName = selectedProfileName,
                    onProfileSelected = onProfileSelected,
                    onGameSelected = onGameSelected,
                    aiContext = aiContext,
                    aiAdvisor = aiAdvisor,
                    conversation = conversation,
                    onConversationChanged = onConversationChanged
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
                AiAdvisorCard(
                    context = aiContext,
                    advisor = aiAdvisor,
                    conversation = conversation,
                    onConversationChanged = onConversationChanged,
                    onProfileSelected = onProfileSelected
                )
            }
            item {
                VoiceAssistantCard(
                    selectedProfileName = selectedProfileName,
                    onProfileSelected = onProfileSelected,
                    onGameSelected = onGameSelected,
                    aiContext = aiContext,
                    aiAdvisor = aiAdvisor,
                    conversation = conversation,
                    onConversationChanged = onConversationChanged
                )
            }
        }
        item {
            BoosterOptions(
                selectedProfileName = selectedProfileName,
                onProfileSelected = onProfileSelected
            )
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


@Composable
private fun GameHubStyleHeader(
    selectedProfileName: String,
    onOpenLibrary: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = GameHubUiTokens.compactCardPadding,
                vertical = GameHubUiTokens.compactControlSpacing
            ),
            verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactControlSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "GAMEHUB ULTRA",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        selectedProfileName,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                "PC • STEAM • EPIC • RETRO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                onClick = onOpenLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Abrir biblioteca y buscar juegos"
                    },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "⌕  Buscar juegos, aplicaciones o comandos…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "BIBLIOTECA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TusJuegosShelf(
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
    manualGamePackages: Set<String>,
    selectedGamePackage: String?,
    onGameSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val games = remember {
        GameLibrary.discover(context).games.associateBy { it.packageName }
    }
    val packageOrder = buildList {
        recentGamePackages.forEach { add(it) }
        favoriteGames.forEach { add(it) }
        manualGamePackages.forEach { add(it) }
        selectedGamePackage?.let { add(it) }
    }.distinct()
    val visible = packageOrder.mapNotNull { games[it] }.take(10)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactControlSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tus juegos", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${visible.size}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (visible.isEmpty()) {
                Text(
                    "Añade juegos desde Biblioteca para verlos aquí.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.packageName }) { game ->
                        Surface(
                            onClick = { onGameSelected(game.packageName) },
                            color = if (game.packageName == selectedGamePackage) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    game.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1
                                )
                                Text(
                                    "Jugar",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PeripheralsHubCard(peripherals: PeripheralDiagnostics?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PERIFÉRICOS", style = MaterialTheme.typography.titleLarge)
            if (peripherals == null) {
                Text(stringResource(R.string.peripherals_loading), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            DeviceRow("Gamepad", "${peripherals.gamepadCount} conectado(s)")
            DeviceRow("Teclado", "${peripherals.keyboardCount} conectado(s)")
            DeviceRow("Ratón", "${peripherals.mouseCount} conectado(s)")
            DeviceRow("Audio externo", "${peripherals.externalAudioCount} conectado(s)")
            if (peripherals.inputDevices.isEmpty()) {
                Text(stringResource(R.string.peripherals_none_detected), style = MaterialTheme.typography.bodySmall)
            } else {
                peripherals.inputDevices.take(5).forEach { entry ->
                    val kinds = entry.kinds.joinToString(" · ") { kind ->
                        when (kind) {
                            PeripheralKind.GAMEPAD -> "GAMEPAD"
                            PeripheralKind.KEYBOARD -> "TECLADO"
                            PeripheralKind.MOUSE -> "RATÓN"
                        }
                    }
                    Text("${entry.name} · $kinds", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (peripherals.externalAudioDevices.isNotEmpty()) {
                Text(stringResource(R.string.peripherals_audio_label), style = MaterialTheme.typography.labelLarge)
                peripherals.externalAudioDevices.take(3).forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RuntimeDiagnosticsCard(
    device: DeviceInfo,
    diagnostics: RuntimeDiagnostics?,
    adaptiveDecision: AdaptiveDecision?,
    performanceHistory: List<PerformanceEvent>,
    onApplyAdaptiveProfile: () -> Unit
) {
    val readiness = diagnostics?.let { telemetry ->
        GamingReadinessCalculator.calculate(
            GamingReadinessInput(
                cpuCores = device.cpuCores,
                totalRamMb = device.totalRamMb,
                gpuAvailable = !device.gpuRenderer.isNullOrBlank() ||
                    !device.gpuVendor.isNullOrBlank(),
                thermalStatus = telemetry.thermal.status,
                thermalHeadroom = telemetry.thermal.headroom,
                batteryPercent = telemetry.battery.percent,
                charging = telemetry.battery.charging,
                refreshRateHz = telemetry.refresh.currentRefreshRateHz,
                networkValidated = telemetry.connectivity.validated,
                networkLatencyMs = telemetry.connectivity.latencyMs,
                downstreamBandwidthKbps = telemetry.connectivity.downstreamBandwidthKbps,
                storageFreePercent = telemetry.storage.freePercent,
                inputDeviceCount = telemetry.inputDeviceCount
            )
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.runtime_diagnostics_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (diagnostics == null || readiness == null) {
                Text(stringResource(R.string.runtime_diagnostics_loading))
            } else {
                Text(
                    stringResource(R.string.readiness_score, readiness.score, readiness.label),
                    style = MaterialTheme.typography.titleMedium
                )
                DeviceRow(
                    stringResource(R.string.runtime_thermal),
                    diagnostics.thermal.status?.let { thermalLabel(it) }
                        ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    stringResource(R.string.thermal_headroom),
                    diagnostics.thermal.headroom?.let {
                        stringResource(R.string.thermal_headroom_value, (it * 100).roundToInt())
                    } ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    "RAM usada",
                    diagnostics.memory.usedPercent.toString() + "% (" +
                        diagnostics.memory.usedRamMb + " / " + diagnostics.memory.totalRamMb + " MB)"
                )
                DeviceRow(
                    stringResource(R.string.runtime_battery),
                    diagnostics.battery.percent?.let {
                        if (diagnostics.battery.charging) {
                            stringResource(R.string.battery_charging, it)
                        } else {
                            stringResource(R.string.battery_level, it)
                        }
                    } ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    stringResource(R.string.runtime_network),
                    diagnostics.connectivity.transport
                        ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    stringResource(R.string.runtime_latency),
                    diagnostics.connectivity.latencyMs?.let {
                        stringResource(R.string.latency_value, it)
                    } ?: stringResource(R.string.not_measured)
                )
                DeviceRow(
                    stringResource(R.string.runtime_bandwidth),
                    diagnostics.connectivity.downstreamBandwidthKbps?.let {
                        stringResource(R.string.bandwidth_value, it)
                    } ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    stringResource(R.string.runtime_storage),
                    stringResource(R.string.storage_free_value, diagnostics.storage.freePercent)
                )
                DeviceRow(
                    stringResource(R.string.runtime_refresh),
                    diagnostics.refresh.currentRefreshRateHz?.let {
                        stringResource(R.string.refresh_value, it.roundToInt())
                    } ?: stringResource(R.string.not_available)
                )
                DeviceRow(
                    stringResource(R.string.runtime_inputs),
                    stringResource(
                        R.string.peripherals_summary,
                        diagnostics.peripherals.gamepadCount,
                        diagnostics.peripherals.keyboardCount,
                        diagnostics.peripherals.mouseCount,
                        diagnostics.peripherals.externalAudioCount
                    )
                )
                adaptiveDecision?.let { decision ->
                    Text(
                        stringResource(R.string.adaptive_recommendation, decision.profile.title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(decision.reason)
                    if (decision.profile != PerformanceProfile.BALANCED) {
                        Button(
                            onClick = onApplyAdaptiveProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.apply_adaptive))
                        }
                    }
                }
                readiness.reasons.take(4).forEach { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall)
                }
                if (performanceHistory.isNotEmpty()) {
                    Text(
                        stringResource(R.string.performance_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    performanceHistory.takeLast(5).asReversed().forEach { event ->
                        Text(
                            eventLabel(event),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceAssistantCard(
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit,
    aiContext: GameHubAiContext,
    aiAdvisor: GameHubAiAdvisor,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiIntentResolver = remember(aiAdvisor) { aiAdvisor.intentResolver() }
    val latestAiContext by rememberUpdatedState(aiContext)
    val latestConversation by rememberUpdatedState(conversation)
    var listening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var response by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingContinuousListening by rememberSaveable { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted && pendingContinuousListening) {
            pendingContinuousListening = false
            setContinuousListeningEnabled(context, true)
            startVoiceWakeService(context)
            response = "Escucha continua activada."
        } else if (!granted) {
            pendingContinuousListening = false
            response = context.getString(R.string.voice_permission_required)
        }
    }

    val voiceController = remember(context) {
        lateinit var controller: VoiceAssistantController
        controller = VoiceAssistantController(
            context = context,
            onListeningChanged = { listening = it },
            onTranscript = { spokenText ->
                transcript = spokenText
                scope.launch(Dispatchers.IO) {
                    val conversationBeforeTurn =
                        latestConversation.takeLast(MAX_CHAT_HISTORY - 1)
                    val withUser = UltraConversationPolicy.append(
                        history = latestConversation,
                        entry = "Tú: " + spokenText,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onConversationChanged(withUser)
                    }

                    val voiceStatus = VoiceDeviceStatusProvider.read(context)
                    val route = UltraUnifiedAgentRouter.route(
                        transcript = spokenText,
                        optionalResolver = aiIntentResolver,
                        telemetry = UltraRuntimeTelemetry(
                            batteryPercent = voiceStatus.batteryPercent,
                            thermalLabel = voiceStatus.thermalLabel,
                            refreshRateHz = latestAiContext.refreshRateHz
                        )
                    )
                    when (route) {
                        is UltraAgentRoute.Utility -> {
                            val answer = route.answer.message
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + answer,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                onConversationChanged(withAnswer)
                                response = answer
                                controller.speak(answer)
                            }
                        }

                        is UltraAgentRoute.Chat -> {
                            val answer = aiAdvisor.chat(
                                message = route.message,
                                context = latestAiContext,
                                conversation = conversationBeforeTurn
                            )
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + answer,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                onConversationChanged(withAnswer)
                                response = answer
                                controller.speak(answer)
                            }
                        }

                        is UltraAgentRoute.Command -> {
                            val result = VoiceCommandEngine.execute(
                                command = route.command,
                                gamesProvider = { GameLibrary.discover(context).games },
                                launchGame = { packageName ->
                                    GameLauncher.launch(context, packageName)
                                },
                                saveSelectedGame = { packageName ->
                                    GameSelectionStore.saveSelectedGame(context, packageName)
                                },
                                saveSelectedProfile = { profile ->
                                    ProfileSelectionStore.saveSelectedProfile(context, profile)
                                },
                                saveSelectedGameWithProfile = { packageName, profile ->
                                    GameSelectionStore.saveSelectedGameAndProfile(
                                        context,
                                        packageName,
                                        profile
                                    )
                                },
                                isProfileAvailable = { profile ->
                                    profile != PerformanceProfile.X4 ||
                                        DeviceCapabilitiesProvider.get(context)
                                            .sustainedPerformanceSupported
                                },
                                statusProvider = { VoiceDeviceStatusProvider.read(context) },
                                aiAdvisor = { question ->
                                    aiAdvisor.advise(question, latestAiContext)
                                }
                            )
                            val spokenResponse = VoiceResponseFormatter.format(context, result)
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + spokenResponse,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                when (result) {
                                    is VoiceActionResult.ProfileSelected ->
                                        onProfileSelected(result.profile)
                                    is VoiceActionResult.GameOpened -> {
                                        onGameSelected(result.game.packageName)
                                        if (!result.profileDeferred) {
                                            result.profile?.let(onProfileSelected)
                                        }
                                    }
                                    else -> Unit
                                }
                                onConversationChanged(withAnswer)
                                response = spokenResponse
                                controller.speak(spokenResponse)
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
                    checked = continuousListeningEnabled(context),
                    onCheckedChange = { enabled ->
                        if (enabled && !permissionGranted) {
                            pendingContinuousListening = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (enabled) {
                            setContinuousListeningEnabled(context, true)
                            startVoiceWakeService(context)
                            response = "Escucha continua activada."
                        } else {
                            pendingContinuousListening = false
                            setContinuousListeningEnabled(context, false)
                            stopVoiceWakeService(context)
                            response = "Escucha continua desactivada."
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
        }
    }
}


private fun shareSessionHistory(
    context: Context,
    sessions: List<GameSessionRecord>
) {
    if (sessions.isEmpty()) return
    val report = buildString {
        appendLine("GameHub Ultra — historial de sesiones")
        sessions.forEach { session ->
            appendLine("Juego: ${session.packageName}")
            appendLine("Perfil: ${session.profileName}")
            appendLine("Inicio: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(session.startedAtMillis))}")
            appendLine("Duración: ${session.durationMillis?.let { formatDuration(it) } ?: "activa"}")
            session.startBatteryPercent?.let { appendLine("Batería inicio: ${it}%") }
            session.endBatteryPercent?.let { appendLine("Batería fin: ${it}%") }
            session.endRamUsedPercent?.let { appendLine("RAM usada al final: ${it}%") }
            appendLine()
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GameHub Ultra — historial de sesiones")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir historial"))
}

private fun sharePerformanceTimeline(
    context: Context,
    gamePackage: String?,
    timeline: PerformanceTimeline
) {
    if (!PerformanceTimelineActionPolicy.canShare(timeline)) {
        return
    }
    val report = PerformanceTimelineReportFormatter.format(gamePackage, timeline)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "GameHub Ultra — Performance Timeline")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir timeline"))
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
private fun SessionCenterCard(
    context: Context,
    sessions: List<GameSessionRecord>,
    onClear: () -> Unit,
    onShare: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.session_center_title), style = MaterialTheme.typography.titleLarge)
                Text(sessions.size.toString(), color = MaterialTheme.colorScheme.primary)
            }
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.session_center_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                sessions.take(5).forEach { session ->
                    val duration = session.durationMillis?.let(::formatDuration)
                        ?: stringResource(R.string.session_active)
                    DeviceRow(
                        label = session.packageName,
                        value = session.profileName + " · " + duration
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_share))
                    }
                    TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.session_clear))
                    }
                }
            }
        }
    }
}

private object VoiceDeviceStatusProvider {
    fun read(context: Context): VoiceDeviceStatus {
        val batteryManager = context.getSystemService(android.os.BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val battery = BatteryTelemetry.sanitizePercentage(
            batteryManager?.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
            )        )
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> context.getString(R.string.normal)
                PowerManager.THERMAL_STATUS_LIGHT -> context.getString(R.string.thermal_light)
                PowerManager.THERMAL_STATUS_MODERATE -> context.getString(R.string.thermal_moderate)
                PowerManager.THERMAL_STATUS_SEVERE -> context.getString(R.string.thermal_severe)
                PowerManager.THERMAL_STATUS_CRITICAL -> context.getString(R.string.thermal_critical)
                PowerManager.THERMAL_STATUS_EMERGENCY -> context.getString(R.string.thermal_emergency)
                PowerManager.THERMAL_STATUS_SHUTDOWN -> context.getString(R.string.thermal_shutdown)
                else -> context.getString(R.string.thermal_unknown)
            }
        } else {
            context.getString(R.string.not_available)
        }
        return VoiceDeviceStatus(battery, thermal)
    }
}

private object VoiceResponseFormatter {
    fun format(context: Context, result: VoiceActionResult): String =
        when (result) {
            is VoiceActionResult.ProfileSelected ->
                context.getString(
                    if (result.deferred) {
                        R.string.voice_result_profile_deferred
                    } else {
                        R.string.voice_result_profile_applied
                    },
                    result.profile.title
                )
            is VoiceActionResult.GameOpened -> {
                val base = context.getString(
                    R.string.voice_result_game_opened,
                    result.game.label
                )
                when {
                    result.profileDeferred && result.profile != null ->
                        base + " " + context.getString(
                            R.string.voice_result_profile_deferred_short,
                            result.profile.title
                        )
                    result.profileUnavailable ->
                        base + " " + context.getString(
                            R.string.voice_result_profile_unavailable
                        )
                    result.profile != null ->
                        base + " " + context.getString(
                            R.string.voice_result_profile_applied_short,
                            result.profile.title
                        )
                    else -> base
                }
            }
            is VoiceActionResult.DeviceStatus ->
                context.getString(
                    R.string.voice_result_status,
                    result.status.batteryPercent?.toString()
                        ?: context.getString(R.string.not_available),
                    result.status.thermalLabel
                )
            is VoiceActionResult.AiAdvice ->
                AiAdviceFormatter.fullResponse(context, result.advice)
            VoiceActionResult.Help ->
                context.getString(R.string.voice_result_help)
            is VoiceActionResult.NotAvailable ->
                context.getString(R.string.voice_status_unavailable) +
                    " " + result.detail
            VoiceActionResult.RequiresPermission ->
                context.getString(R.string.voice_permission_required)
            is VoiceActionResult.Failed ->
                context.getString(R.string.voice_status_failed) +
                    " " + result.detail
        }
}

@Composable
private fun AiAdvisorCard(
    context: GameHubAiContext,
    advisor: GameHubAiAdvisor,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    onProfileSelected: (PerformanceProfile) -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var advice by remember { mutableStateOf<GameHubAiAdvice?>(null) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    var chatMessage by rememberSaveable { mutableStateOf("") }
    var chatSending by remember { mutableStateOf(false) }

    fun sendChatMessage() {
        val message = chatMessage.trim()
        if (message.isBlank() || chatSending) return
        val previousConversation = conversation.takeLast(MAX_CHAT_HISTORY - 1)
        val withUser = UltraConversationPolicy.append(
            history = conversation,
            entry = "Tú: " + message,
            maxEntries = MAX_CHAT_HISTORY
        )
        chatMessage = ""
        onConversationChanged(withUser)
        chatSending = true
        scope.launch(Dispatchers.IO) {
            val answer = advisor.chat(message, context, previousConversation)
            withContext(Dispatchers.Main) {
                onConversationChanged(
                    UltraConversationPolicy.append(
                        history = withUser,
                        entry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                )
                chatSending = false
            }
        }
    }

    if (showChat) {
        AlertDialog(
            onDismissRequest = { if (!chatSending) showChat = false },
            title = { Text("Ultra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conversation.takeLast(MAX_CHAT_HISTORY).forEach { entry ->
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_chat_input_label)) },
                        placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                        enabled = !chatSending,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = ::sendChatMessage,
                    enabled = chatMessage.isNotBlank() && !chatSending
                ) {
                    Text(if (chatSending) stringResource(R.string.ai_chat_thinking) else stringResource(R.string.ai_chat_send))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onConversationChanged(emptyList()); chatMessage = "" },
                    enabled = !chatSending
                ) { Text(stringResource(R.string.ai_chat_clear)) }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.ai_advisor_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.ai_advisor_subtitle))
            Button(onClick = { showChat = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_chat_input_label))
            }
            Text(
                stringResource(R.string.ai_local_model_configured),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val result = advisor.advise(
                            question = "que modo me recomiendas",
                            context = context
                        )
                        withContext(Dispatchers.Main) {
                            advice = result
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ai_analyze))
            }
            advice?.let { result ->
                Text(
                    AiAdviceFormatter.title(LocalContext.current, result),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(AiAdviceFormatter.explanation(LocalContext.current, result))
                Button(
                    onClick = { onProfileSelected(result.suggestedProfile) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ai_apply_profile))
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(state: PerformanceState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.active_profile),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                localizedProfileTitle(state.selectedProfile),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(localizedProfileDescription(state.selectedProfile))
            Text(
                if (state.sustainedModeApplied) {
                    stringResource(R.string.sustained_applied)
                } else {
                    stringResource(R.string.sustained_not_applied)
                }
            )
            Text(
                stringResource(R.string.interpolation_intent) + ": " +
                    if (state.selectedProfile.frameInterpolationIntent) {
                        stringResource(R.string.interpolation_prioritized)
                    } else {
                        stringResource(R.string.interpolation_not_requested)
                    }
            )
            Text(
                stringResource(R.string.thermal_tradeoff) + ": " +
                    if (state.selectedProfile.acceptsHigherTemperature) {
                        stringResource(R.string.accepts_higher_temperature)
                    } else {
                        stringResource(R.string.no_extra_thermal)
                    }
            )
        }
    }
}

@Composable
private fun SmartPerformanceCard(
    recommendation: com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation,
    observations: List<OptimizationObservation>,
    onApply: () -> Unit,
    onClearMemory: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.smart_performance_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                localizedProfileTitle(recommendation.profile) +
                    " · " + recommendation.score + "/100"
            )
            Text(recommendation.reason)
            if (recommendation.evidence.isNotEmpty()) {
                Text(
                    stringResource(R.string.smart_performance_evidence),
                    style = MaterialTheme.typography.labelLarge
                )
                recommendation.evidence.take(5).forEach { evidence ->
                    Text("• " + evidence, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(
                    R.string.smart_performance_driver,
                    recommendation.gpuFamily.name,
                    when (recommendation.driverStrategy) {
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.SYSTEM_ONLY ->
                            stringResource(R.string.driver_system_only)
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.TURNIP_CANDIDATE ->
                            stringResource(R.string.driver_turnip_candidate)
                        com.cardenaspiero255.gamehubultra.domain.DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE ->
                            stringResource(R.string.driver_native_candidate)
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.smart_performance_observation_count, observations.size),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_apply))
                }
                TextButton(onClick = onClearMemory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_performance_reset))
                }
            }
        }
    }
}

private fun packageDisplayName(context: Context, packageName: String): String =
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

private fun packageVersionName(context: Context, packageName: String): String? =
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

private fun isPackageInstalled(context: Context, packageName: String): Boolean =
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

@Composable
private fun BoosterOptions(
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.booster_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.booster_subtitle))
            PerformanceProfile.entries.forEach { profile ->
                Button(
                    onClick = { onProfileSelected(profile) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedProfileName == profile.name) {
                            stringResource(
                                R.string.booster_selected,
                                localizedProfileTitle(profile)
                            )
                        } else {
                            localizedProfileTitle(profile)
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
private fun DeviceStatusCard(
    device: DeviceInfo,
    capabilities: DeviceCapabilities?
) {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val batteryManager = remember(context) {
        context.getSystemService(android.os.BatteryManager::class.java)
    }

    var batteryPercent by remember {
        mutableStateOf(
            BatteryTelemetry.sanitizePercentage(
                batteryManager?.getIntProperty(
                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
                )
            )
        )
    }
    var thermalStatus by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager?.currentThermalStatus
            } else {
                null
            }
        )
    }

    DisposableEffect(context, batteryManager, powerManager) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val level = intent.getIntExtra(
                    android.os.BatteryManager.EXTRA_LEVEL,
                    -1
                )
                val scale = intent.getIntExtra(
                    android.os.BatteryManager.EXTRA_SCALE,
                    -1
                )
                batteryPercent = BatteryTelemetry.fromBroadcast(level, scale)
            }
        }

        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val thermalListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                PowerManager.OnThermalStatusChangedListener { status ->
                    thermalStatus = status
                }
            } else {
                null
            }

        if (thermalListener != null) {
            powerManager?.addThermalStatusListener(
                context.mainExecutor,
                thermalListener
            )
        }

        onDispose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            if (
                thermalListener != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                powerManager != null
            ) {
                powerManager.removeThermalStatusListener(thermalListener)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.device_status),
                style = MaterialTheme.typography.titleLarge
            )
            if (batteryPercent != null) {
                Text(
                    stringResource(R.string.battery) + ": " +
                        batteryPercent + "%"
                )
                LinearProgressIndicator(
                    progress = { (batteryPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.battery) + ": " +
                        stringResource(R.string.not_available)
                )
            }

            Text(
                stringResource(R.string.thermal_status) + ": " +
                    thermalLabel(thermalStatus)
            )
            DeviceRow(stringResource(R.string.manufacturer), device.manufacturer)
            DeviceRow(stringResource(R.string.model), device.model)
            DeviceRow(
                stringResource(R.string.android_version),
                device.androidVersion + " (API " + device.sdkInt + ")"
            )
            DeviceRow(
                stringResource(R.string.cpu),
                device.cpuModel.ifBlank {
                    stringResource(R.string.not_available)
                }
            )
            DeviceRow(
                stringResource(R.string.cores),
                device.cpuCores.toString()
            )
            DeviceRow(
                stringResource(R.string.ram),
                device.totalRamMb.toString() + " MB"
            )
            DeviceRow(
                stringResource(R.string.gpu_vendor),
                device.gpuVendor ?: stringResource(R.string.not_available)
            )
            DeviceRow(
                stringResource(R.string.gpu_renderer),
                device.gpuRenderer ?: stringResource(R.string.not_available)
            )
            DeviceRow(
                stringResource(R.string.abi),
                device.supportedAbis.joinToString().ifBlank {
                    stringResource(R.string.not_available)
                }
            )
            CapabilityRow(
                stringResource(R.string.sustained_performance),
                capabilities?.sustainedPerformanceSupported == true
            )
            CapabilityRow(
                stringResource(R.string.thermal_api),
                capabilities?.thermalStatusAvailable == true
            )
            CapabilityRow(
                stringResource(R.string.performance_hint_api),
                capabilities?.performanceHintsAvailable == true
            )
            Text(stringResource(R.string.capability_note))
        }
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    supported: Boolean
) {
    DeviceRow(
        label,
        if (supported) {
            stringResource(R.string.supported)
        } else {
            stringResource(R.string.not_supported)
        }
    )
}

@Composable
private fun DeviceRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, modifier = Modifier.weight(0.9f))
        Text(value, modifier = Modifier.weight(1.6f))
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    selectedGamePackage: String?,
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
    manualGamePackages: Set<String>,
    storeGames: List<StoreLibraryGame>,
    selectedProfile: PerformanceProfile,
    runtimeDiagnostics: RuntimeDiagnostics?,
    sessionHistory: List<GameSessionRecord>,
    onGameSelected: (String) -> Unit,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onGameOpened: (String) -> Unit,
    onToggleManualGame: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var discovery by remember { mutableStateOf<GameDiscoveryResult?>(null) }
    var launchFailed by rememberSaveable { mutableStateOf(false) }
    var showAddGameDialog by rememberSaveable { mutableStateOf(false) }
    var launchableApps by remember { mutableStateOf<List<GameInfo>>(emptyList()) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var showSelectedGameDetails by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(context, refreshToken, manualGamePackages) {
        discovery = withContext(Dispatchers.IO) {
            GameLibrary.discover(context, manualGamePackages)
        }
    }

    LaunchedEffect(context, showAddGameDialog) {
        if (showAddGameDialog) {
            launchableApps = withContext(Dispatchers.IO) {
                GameLibrary.discoverNonGameLaunchableApps(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val result = discovery
    val orderedGames = remember(result?.games, favoriteGames, recentGamePackages) {
        val recentOrder = recentGamePackages.withIndex()
            .associate { indexed -> indexed.value to indexed.index }
        result?.games.orEmpty().sortedWith(
            compareBy<GameInfo> {
                when {
                    favoriteGames.contains(it.packageName) -> 0
                    recentOrder.containsKey(it.packageName) -> 1
                    else -> 2
                }
            }.thenBy { recentOrder[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() }
        )
    }
    val visibleGames = remember(orderedGames, libraryQuery) {
        GameLibrary.filterGames(orderedGames, libraryQuery)
    }

    val visibleStoreGames = remember(storeGames, libraryQuery) {
        storeGames.filter {
            libraryQuery.isBlank() ||
                it.title.contains(libraryQuery, ignoreCase = true) ||
                it.platformGameId.contains(libraryQuery, ignoreCase = true)
        }
    }

    val configuration = LocalConfiguration.current
    val gridMetrics = remember(configuration.screenWidthDp) {
        LibraryLayoutPolicy.metricsForWidthDp(configuration.screenWidthDp)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridMetrics.minTileWidthDp.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = GameHubUiTokens.compactHorizontalPadding,
                vertical = GameHubUiTokens.compactControlSpacing
            ),
        horizontalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing),
        verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showAddGameDialog = true }
                ) {
                    Text(stringResource(R.string.add_game))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = libraryQuery,
                onValueChange = { libraryQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_search)) }
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            StoreLibrarySection(games = visibleStoreGames)
        }

        if (launchFailed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.library_open_error),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        when {
            result == null -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.library_loading),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            result.failed -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.library_error),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(stringResource(R.string.library_error_hint))
                        }
                    }
                }
            }
            result.games.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.library_empty),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(stringResource(R.string.library_empty_hint))
                        }
                    }
                }
            }
            else -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.library_count, result.games.size))
                }

                if (libraryQuery.isNotBlank() && result.games.isNotEmpty() && visibleGames.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.library_search_empty))
                    }
                }

                selectedGamePackage?.let { selected ->
                    visibleGames.firstOrNull {
                        it.packageName == selected
                    }?.let { game ->
                        val favorite = favoriteGames.contains(game.packageName)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SelectedGameCompactBar(
                                game = game,
                                favorite = favorite,
                                detailsVisible = showSelectedGameDetails,
                                onToggleDetails = {
                                    showSelectedGameDetails = !showSelectedGameDetails
                                },
                                onToggleFavorite = {
                                    onToggleFavorite(game.packageName, !favorite)
                                },
                                onOpen = {
                                    if (openGame(context, game.packageName)) {
                                        launchFailed = false
                                        onGameOpened(game.packageName)
                                    } else {
                                        launchFailed = true
                                    }
                                }
                            )
                        }
                        if (showSelectedGameDetails) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SelectedGameCard(
                                    game = game,
                                    favorite = favorite,
                                    recent = recentGamePackages.contains(game.packageName),
                                    selectedProfile = selectedProfile,
                                    diagnostics = runtimeDiagnostics,
                                    sessions = sessionHistory.filter { it.packageName == game.packageName },
                                    onProfileSelected = onProfileSelected,
                                    onToggleFavorite = {
                                        onToggleFavorite(game.packageName, !favorite)
                                    },
                                    onOpen = {
                                        if (openGame(context, game.packageName)) {
                                            launchFailed = false
                                            onGameOpened(game.packageName)
                                        } else {
                                            launchFailed = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                items(
                    items = visibleGames,
                    key = { it.packageName }
                ) { game ->
                    GameTile(
                        game = game,
                        selected = selectedGamePackage == game.packageName,
                        favorite = favoriteGames.contains(game.packageName),
                        minTileHeightDp = gridMetrics.minTileHeightDp,
                        onSelect = {
                            launchFailed = false
                            onGameSelected(game.packageName)
                        },
                        onToggleFavorite = {
                            onToggleFavorite(
                                game.packageName,
                                !favoriteGames.contains(game.packageName)
                            )
                        },
                        onOpen = {
                            if (openGame(context, game.packageName)) {
                                launchFailed = false
                                onGameOpened(game.packageName)
                            } else {
                                launchFailed = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddGameDialog) {
        val candidates = launchableApps
        AlertDialog(
            onDismissRequest = { showAddGameDialog = false },
            title = { Text(stringResource(R.string.add_game_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.packageName }) { app ->
                        val manuallyAdded = manualGamePackages.contains(app.packageName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onToggleManualGame(app.packageName, !manuallyAdded)
                                }
                            ) {
                                Text(
                                    if (manuallyAdded) {
                                        stringResource(R.string.remove_game)
                                    } else {
                                        stringResource(R.string.add_game)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddGameDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SelectedGameCompactBar(
    game: GameInfo,
    favorite: Boolean,
    detailsVisible: Boolean,
    onToggleDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(game.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(game.packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        }.getOrNull()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactControlSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconBitmap?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = stringResource(
                            R.string.game_icon_content_description,
                            game.label
                        ),
                        modifier = Modifier.size(42.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        "SELECCIONADO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onToggleFavorite) {
                    Text(if (favorite) "★" else "☆")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onToggleDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (detailsVisible) "MENOS" else "DETALLES")
                }
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JUGAR")
                }
            }
        }
    }
}

@Composable
private fun SelectedGameCard(
    game: GameInfo,
    favorite: Boolean,
    recent: Boolean,
    selectedProfile: PerformanceProfile,
    diagnostics: RuntimeDiagnostics?,
    sessions: List<GameSessionRecord>,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    var showProfiles by rememberSaveable(game.packageName) { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable(game.packageName) { mutableStateOf(false) }
    var showHistory by rememberSaveable(game.packageName) { mutableStateOf(false) }
    val installed = remember(game.packageName) {
        isPackageInstalled(context, game.packageName)
    }
    val versionName = remember(game.packageName) {
        packageVersionName(context, game.packageName)
    }
    val iconBitmap = remember(game.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(game.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }
    val recentSessions = remember(sessions) {
        sessions.sortedByDescending { it.startedAtMillis }.take(3)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.selected_game),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                iconBitmap?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = stringResource(
                            R.string.game_icon_content_description,
                            game.label
                        ),
                        modifier = Modifier.size(56.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(game.label, style = MaterialTheme.typography.titleMedium)
                    Text(game.packageName, style = MaterialTheme.typography.bodySmall)
                    versionName?.let {
                        Text(
                            stringResource(R.string.game_version, it),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                stringResource(
                    R.string.game_install_state,
                    stringResource(
                        if (installed) R.string.game_installed else R.string.game_not_installed
                    )
                )
            )
            Text(
                stringResource(
                    R.string.game_favorite_state,
                    stringResource(if (favorite) R.string.yes else R.string.no)
                )
            )
            Text(
                stringResource(
                    R.string.game_recent_state,
                    stringResource(if (recent) R.string.yes else R.string.no)
                )
            )
            Text(
                stringResource(
                    R.string.game_profile_state,
                    localizedProfileTitle(selectedProfile)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpen,
                    enabled = installed,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.open_game))
                }
                Button(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (favorite) {
                            stringResource(R.string.remove_favorite)
                        } else {
                            stringResource(R.string.add_favorite)
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { showProfiles = !showProfiles },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.game_quick_profile))
                }
                TextButton(
                    onClick = { showDiagnostics = !showDiagnostics },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.game_quick_diagnostics))
                }
                TextButton(
                    onClick = { showHistory = !showHistory },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.game_quick_history))
                }
            }

            if (showProfiles) {
                PerformanceProfile.entries.forEach { profile ->
                    TextButton(
                        onClick = {
                            onProfileSelected(profile)
                            showProfiles = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (profile == selectedProfile) {
                                stringResource(
                                    R.string.booster_selected,
                                    localizedProfileTitle(profile)
                                )
                            } else {
                                localizedProfileTitle(profile)
                            }
                        )
                    }
                }
            }

            if (showDiagnostics) {
                if (diagnostics == null) {
                    Text(
                        stringResource(R.string.game_diagnostics_unavailable),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    DeviceRow(
                        stringResource(R.string.runtime_thermal),
                        thermalLabel(diagnostics.thermal.status)
                    )
                    DeviceRow(
                        stringResource(R.string.runtime_battery),
                        diagnostics.battery.percent?.let { value -> value.toString() + "%" }
                            ?: stringResource(R.string.not_available)
                    )
                    DeviceRow(
                        stringResource(R.string.runtime_refresh),
                        diagnostics.refresh.currentRefreshRateHz?.let { value ->
                            value.toInt().toString() + " Hz"
                        } ?: stringResource(R.string.not_measured)
                    )
                    DeviceRow(
                        stringResource(R.string.runtime_latency),
                        diagnostics.connectivity.latencyMs?.let { value ->
                            value.toString() + " ms"
                        } ?: stringResource(R.string.not_measured)
                    )
                }
            }

            if (showHistory) {
                if (recentSessions.isEmpty()) {
                    Text(
                        stringResource(R.string.game_history_empty),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    recentSessions.forEach { session ->
                        val duration = session.durationMillis?.let(::formatDuration)
                            ?: stringResource(R.string.session_active)
                        DeviceRow(
                            session.profileName,
                            duration
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameInfo,
    selected: Boolean,
    favorite: Boolean,
    minTileHeightDp: Int,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(game.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(game.packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        }.getOrNull()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTileHeightDp.dp)
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                onClick = onSelect
            ) {
                Row(
                    modifier = Modifier.padding(7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    iconBitmap?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = stringResource(
                                R.string.game_icon_content_description,
                                game.label
                            ),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            game.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1
                        )
                        Text(
                            if (selected) "SELECCIONADO" else game.packageName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onToggleFavorite) {
                    Text(if (favorite) "★" else "☆")
                }
                TextButton(onClick = onOpen) {
                    Text("JUGAR")
                }
            }
        }
    }
}


private fun openGame(context: Context, packageName: String): Boolean =
    GameLauncher.launch(context, packageName)

@Composable
private fun ConnectedAccountsCard(
    onStoreConnectionChanged: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { ConnectedGameAccountsStore(context) }
    val accounts by store.accountsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeAccountId by store.activeAccountIdFlow().collectAsStateWithLifecycle(initialValue = null)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var platformName by rememberSaveable { mutableStateOf(GamePlatform.STEAM.name) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var publicId by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable { mutableStateOf("") }
    var browserError by rememberSaveable { mutableStateOf(false) }
    val connectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            browserError = false
            onStoreConnectionChanged()
        }
    }

    val platform = GamePlatform.valueOf(platformName)
    val profileIdSupported =
        GameAccountValidation.isValidPublicId(platform, publicId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connected_accounts")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.accounts_subtitle))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        browserError = false
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                GamePlatform.STEAM
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.accounts_steam_login))
                }
                Button(
                    onClick = {
                        browserError = false
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                GamePlatform.EPIC_GAMES
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.accounts_epic_login))
                }
            }
            TextButton(
                onClick = {
                    platformName = GamePlatform.STEAM.name
                    displayName = ""
                    publicId = ""
                    alias = ""
                    avatarUrl = ""
                    showAddDialog = true
                }
            ) {
                Text(stringResource(R.string.accounts_add))
            }

            accounts.forEach { account ->
                ConnectedAccountRow(
                    account = account,
                    active = account.id == activeAccountId,
                    onActivate = {
                        scope.launch { store.setActiveAccount(account.id) }
                    },
                    onRemove = {
                        scope.launch {
                            store.remove(account.id)
                            StoreLibraryStore(context).removeForAccount(account.id)
                            onStoreConnectionChanged()
                        }
                    },
                    onSync = {
                        connectionLauncher.launch(
                            StoreConnectionActivity.newIntent(
                                context,
                                account.platform
                            )
                        )
                    },
                    onOpen = {
                        if (!GamePlatformLinks.openPublicProfile(context, account)) {
                            browserError = true
                        }
                    }
                )
            }

            if (accounts.isEmpty()) {
                Text(
                    stringResource(R.string.accounts_empty),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (browserError) {
                Text(
                    stringResource(R.string.accounts_browser_failed),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(stringResource(R.string.accounts_add_title, platform.title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GamePlatform.entries.forEach { item ->
                            TextButton(
                                onClick = {
                                    platformName = item.name
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (platform == item) "✓ " + item.title else item.title
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_display_name)) }
                    )
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_alias)) }
                    )
                    OutlinedTextField(
                        value = avatarUrl,
                        onValueChange = { avatarUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_avatar_url)) }
                    )
                    OutlinedTextField(
                        value = publicId,
                        onValueChange = {
                            publicId = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_public_id)) }
                    )
                    if (!profileIdSupported) {
                        Text(
                            stringResource(R.string.accounts_public_id_invalid),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = displayName.isNotBlank() && publicId.isNotBlank() && profileIdSupported,
                    onClick = {
                        scope.launch {
                            store.add(platform, displayName, publicId, alias, avatarUrl)
                            displayName = ""
                            publicId = ""
                            showAddDialog = false
                        }                    }
                ) {
                    Text(stringResource(R.string.accounts_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ConnectedAccountRow(
    account: ConnectedGameAccount,
    active: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onSync: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text((account.alias?.takeIf(String::isNotBlank) ?: account.displayName) +
                    if (active) " · ACTIVA" else "")
                Text(
                    (account.platform.title + " · " + account.publicId) +
                        (account.avatarUrl?.let { " · Avatar público configurado" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onActivate) {
                Text(if (active) "ACTIVA" else "ACTIVAR")
            }
            TextButton(onClick = onSync) {
                Text("SYNC")
            }
            if (account.platform == GamePlatform.STEAM) {
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.accounts_open))
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.remove_game))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    onStoreConnectionChanged: () -> Unit,
    onClearOptimizationMemory: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_scroll")
            .padding(GameHubUiTokens.compactHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(GameHubUiTokens.compactSectionSpacing)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        ConnectedAccountsCard(onStoreConnectionChanged = onStoreConnectionChanged)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.optimization_memory_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.optimization_memory_description))
                TextButton(onClick = onClearOptimizationMemory) {
                    Text(stringResource(R.string.optimization_memory_clear))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.language_value))
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.about),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.about_text))
                Text(
                    stringResource(R.string.limitations),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.limitations_text))
            }
        }
    }
}

@Composable
private fun StoreLibrarySummary(
    games: List<StoreLibraryGame>,
    onOpenLibrary: () -> Unit
) {
    Card(
        onClick = onOpenLibrary,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Abrir Biblioteca de tiendas"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GameHubUiTokens.compactCardPadding),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "BIBLIOTECA DE TIENDAS",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (games.isEmpty()) {
                        "Conecta Steam o Epic para sincronizar tus juegos dentro de Ultra."
                    } else {
                        "Steam + Epic sincronizados · ${games.size} juego(s)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Text(
                "ABRIR  ›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StoreLibrarySection(
    games: List<StoreLibraryGame>
) {
    if (games.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(GameHubUiTokens.compactCardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "STEAM / EPIC · ${games.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    games.take(12),
                    key = { it.id }
                ) { game ->
                    Surface(
                        modifier = Modifier.size(width = 170.dp, height = 60.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                game.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                game.platform.title + " · " + game.platformGameId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                "CONECTADO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun localizedProfileTitle(
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
private fun localizedProfileDescription(
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
private fun thermalLabel(status: Int?): String =
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
private fun eventLabel(event: PerformanceEvent): String =
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

private const val VOICE_PREFS = "gamehub_ultra_voice"
private const val VOICE_CONTINUOUS_KEY = "continuous_enabled"

private fun continuousListeningEnabled(context: Context): Boolean =
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(VOICE_CONTINUOUS_KEY, false)

private fun setContinuousListeningEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(VOICE_CONTINUOUS_KEY, enabled)
        .apply()
}

private fun startVoiceWakeService(context: Context) {
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
    ) return

    val intent = Intent(context, com.cardenaspiero255.gamehubultra.voice.UltraWakeService::class.java)
        .setAction(com.cardenaspiero255.gamehubultra.voice.UltraWakeService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

private fun stopVoiceWakeService(context: Context) {
    val intent = Intent(context, com.cardenaspiero255.gamehubultra.voice.UltraWakeService::class.java)
        .setAction(com.cardenaspiero255.gamehubultra.voice.UltraWakeService.ACTION_STOP)
    context.stopService(intent)
}
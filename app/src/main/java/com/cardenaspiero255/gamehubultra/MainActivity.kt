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
import com.cardenaspiero255.gamehubultra.ai.GeminiNanoLocalAiModelAdapter
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccount
import com.cardenaspiero255.gamehubultra.data.ConnectedGameAccountsStore
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import com.cardenaspiero255.gamehubultra.platform.GamePlatformLinks
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAX_CHAT_HISTORY = 8

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
                Surface(modifier = Modifier.fillMaxSize()) {
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
    var state by remember { mutableStateOf(initialState) }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var activeSessionPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var runtimeDiagnostics by remember { mutableStateOf<RuntimeDiagnostics?>(null) }
    var adaptiveDecision by remember { mutableStateOf<AdaptiveDecision?>(null) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }
    val adaptiveEngine = remember(uiState.effectiveProfile) {
        AdaptivePerformanceEngine(initialProfile = uiState.effectiveProfile)
    }
    val aiAdvisor = remember { GameHubAiAdvisor(GeminiNanoLocalAiModelAdapter()) }

    DisposableEffect(aiAdvisor) {
        onDispose { aiAdvisor.close() }
    }

    LaunchedEffect(lifecycleOwner, adaptiveEngine, activeSessionPackage, activeSessionId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val selectedGame = activeSessionPackage
            val sessionId = activeSessionId
            if (selectedGame == null || sessionId == null) {
                runtimeDiagnostics = null
                latencyMs = null
                adaptiveDecision = adaptiveEngine.evaluate(
                    AdaptiveRuntimeSnapshot(
                        thermalStatus = null,
                        thermalHeadroom = null,
                        batteryPercent = null,
                        charging = false,
                        powerSaveMode = false,
                        sessionActive = false,
                        sustainedPerformanceSupported =
                            initialState.capabilities?.sustainedPerformanceSupported == true,
                        performanceHintsAvailable =
                            initialState.capabilities?.performanceHintsAvailable == true
                    )
                )
                return@repeatOnLifecycle
            }

            var lastThermalStatus: Int? = null
            var initializedThermalStatus = false
            var lastLatencyCheckAt = 0L
            var lastLatencyNetworkHandle: Long? = null

            try {
                while (isActive) {
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

                    if (initializedThermalStatus &&
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
                    lastThermalStatus = diagnostics.thermal.status
                    initializedThermalStatus = true

                    val decision = adaptiveEngine.evaluate(
                        AdaptiveRuntimeSnapshot(
                            thermalStatus = diagnostics.thermal.status,
                            thermalHeadroom = diagnostics.thermal.headroom,
                            batteryPercent = diagnostics.battery.percent,
                            charging = diagnostics.battery.charging,
                            powerSaveMode = diagnostics.battery.powerSaveMode,
                            sessionActive = activeSessionPackage != null,
                            sustainedPerformanceSupported =
                                initialState.capabilities?.sustainedPerformanceSupported == true,
                            performanceHintsAvailable =
                                initialState.capabilities?.performanceHintsAvailable == true
                        )
                    )
                    adaptiveDecision = decision

                    if (decision.changed) {
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
                // Lifecycle cancellation only pauses telemetry polling; it does not end the game session.
            }
        }
    }

    LaunchedEffect(uiState.effectiveProfile) {
        state = onProfileApplied(uiState.effectiveProfile)
    }

    fun selectProfile(profile: PerformanceProfile) {
        uiState.selectedGamePackage?.let { packageName ->
            viewModel.selectGameProfile(packageName, profile)
        } ?: viewModel.selectGlobalProfile(profile)
    }

    fun endGameSession() {
        val sessionId = activeSessionId
        val packageName = activeSessionPackage
        if (sessionId != null && packageName != null) {
            viewModel.recordPerformanceEvent(
                PerformanceEvent(
                    timestampMillis = System.currentTimeMillis(),
                    type = PerformanceEventType.SESSION_ENDED,
                    sessionId = sessionId,
                    detail = packageName
                )
            )
        }
        activeSessionId = null
        activeSessionPackage = null
    }

    fun selectGame(packageName: String) {
        endGameSession()
        viewModel.selectGame(packageName)
    }

    val selectedProfileName = uiState.effectiveProfile.name
    val selectedGamePackage = uiState.selectedGamePackage
    val favoriteGames = uiState.favoriteGames
    val recentGamePackages = uiState.recentGamePackages
    val manualGamePackages = uiState.manualGamePackages
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

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text("GAMEHUB ULTRA") },
                actions = {
                    TextButton(
                        onClick = { settingsOpen = !settingsOpen },
                        modifier = Modifier
                            .testTag("nav_ajustes")
                            .semantics {
                                contentDescription = "nav_ajustes"
                            }
                    ) {
                        Text("⚙")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!settingsOpen) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .testTag(tabTestTags[0])
                            .semantics {
                                contentDescription = "nav_inicio"
                            },
                        text = { Text(tabs[0].uppercase()) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .testTag(tabTestTags[1])
                            .semantics {
                                contentDescription = "nav_biblioteca"
                            },
                        text = { Text(tabs[1].uppercase()) }
                    )
                }
            }

            when {
                settingsOpen -> SettingsScreen(Modifier.fillMaxSize())
                selectedTab == 0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    device = device,
                    selectedProfileName = selectedProfileName,
                    onProfileSelected = ::selectProfile,
                    onGameSelected = ::selectGame,
                    runtimeDiagnostics = runtimeDiagnostics,
                    adaptiveDecision = adaptiveDecision,
                    performanceHistory = performanceHistory,
                    onApplyAdaptiveProfile = {
                        adaptiveDecision?.let { selectProfile(it.profile) }
                    },
                    aiContext = aiContext,
                    aiAdvisor = aiAdvisor,
                    favoriteGames = favoriteGames,
                    recentGamePackages = recentGamePackages,
                    manualGamePackages = manualGamePackages
                )
                else -> LibraryScreen(
                    modifier = Modifier.fillMaxSize(),
                    selectedGamePackage = selectedGamePackage,
                    favoriteGames = favoriteGames,
                    recentGamePackages = recentGamePackages,
                    manualGamePackages = manualGamePackages,
                    onGameSelected = ::selectGame,
                    onToggleFavorite = viewModel::setFavoriteGame,
                    onGameOpened = { packageName ->
                        endGameSession()
                        val sessionId = UUID.randomUUID().toString()
                        activeSessionPackage = packageName
                        activeSessionId = sessionId
                        viewModel.recordPerformanceEvent(
                            PerformanceEvent(
                                timestampMillis = System.currentTimeMillis(),
                                type = PerformanceEventType.SESSION_STARTED,
                                sessionId = sessionId,
                                detail = packageName
                            )
                        )
                        viewModel.recordRecentGame(packageName)
                    },
                    onToggleManualGame = viewModel::setManualGame
                )
            }
        }
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
    runtimeDiagnostics: RuntimeDiagnostics?,
    adaptiveDecision: AdaptiveDecision?,
    performanceHistory: List<PerformanceEvent>,
    onApplyAdaptiveProfile: () -> Unit,
    aiContext: GameHubAiContext,
    aiAdvisor: GameHubAiAdvisor,
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
    manualGamePackages: Set<String>
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GameHubStyleHeader(
                selectedProfileName = selectedProfileName
            )
        }
        item {
            Text(
                stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            UltraDashboard(
                device = device,
                diagnostics = runtimeDiagnostics,
                profile = state.selectedProfile,
                adaptiveDecision = adaptiveDecision
            )
        }
        item { ActiveProfileCard(state) }
        item {
            TusJuegosShelf(
                favoriteGames = favoriteGames,
                recentGamePackages = recentGamePackages,
                manualGamePackages = manualGamePackages,
                selectedGamePackage = aiContext.selectedGamePackage,
                onGameSelected = onGameSelected
            )
        }
        item {
            AiAdvisorCard(
                context = aiContext,
                advisor = aiAdvisor,
                onProfileSelected = onProfileSelected
            )
        }
        item {
            VoiceAssistantCard(
                selectedProfileName = selectedProfileName,
                onProfileSelected = onProfileSelected,
                onGameSelected = onGameSelected,
                aiContext = aiContext,
                aiAdvisor = aiAdvisor
            )
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
    }
}


@Composable
private fun GameHubStyleHeader(
    selectedProfileName: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "⌕  Buscar juegos, aplicaciones o comandos…",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
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
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            modifier = Modifier.padding(14.dp),
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
    aiAdvisor: GameHubAiAdvisor
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aiIntentResolver = remember(aiAdvisor) { aiAdvisor.intentResolver() }
    val latestAiContext by rememberUpdatedState(aiContext)
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
                    val result = VoiceCommandEngine.execute(
                        command = VoiceCommandParser.parse(spokenText, aiIntentResolver),
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
                        response = spokenResponse
                        controller.speak(spokenResponse)
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
    onProfileSelected: (PerformanceProfile) -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var advice by remember { mutableStateOf<GameHubAiAdvice?>(null) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    var chatMessage by rememberSaveable { mutableStateOf("") }
    var chatHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    var chatSending by remember { mutableStateOf(false) }

    fun sendChatMessage() {
        val message = chatMessage.trim()
        if (message.isBlank() || chatSending) return
        val previousConversation = chatHistory.takeLast(MAX_CHAT_HISTORY - 1)
        chatMessage = ""
        chatHistory = (previousConversation + ("Tú: " + message)).takeLast(MAX_CHAT_HISTORY)
        chatSending = true
        scope.launch(Dispatchers.IO) {
            val answer = advisor.chat(message, context, previousConversation)
            withContext(Dispatchers.Main) {
                chatHistory = (chatHistory + ("Ultra: " + answer)).takeLast(MAX_CHAT_HISTORY)
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
                    chatHistory.takeLast(MAX_CHAT_HISTORY).forEach { entry ->
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
                    onClick = { chatHistory = emptyList(); chatMessage = "" },
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
private fun ProfileCard(
    profile: PerformanceProfile,
    selected: Boolean,
    onClick: () -> Unit
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
                Text(
                    localizedProfileTitle(profile),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Text(
                        stringResource(R.string.selected),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
            Text(localizedProfileDescription(profile))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

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
    onGameSelected: (String) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        OutlinedTextField(
            value = libraryQuery,
            onValueChange = { libraryQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.library_search)) }
        )

        if (launchFailed) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.library_open_error),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        when {
            result == null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.library_loading),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            result.failed -> {
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
            result.games.isEmpty() -> {
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
            else -> {
                Text(
                    stringResource(R.string.library_count, result.games.size)
                )

                if (libraryQuery.isNotBlank() && result.games.isNotEmpty() && visibleGames.isEmpty()) {
                    Text(stringResource(R.string.library_search_empty))
                }

                selectedGamePackage?.let { selected ->
                    visibleGames.firstOrNull {
                        it.packageName == selected
                    }?.let { game ->
                        SelectedGameCard(
                            game = game,
                            favorite = favoriteGames.contains(game.packageName),
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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visibleGames,
                        key = { it.packageName }
                    ) { game ->
                        GameTile(
                            game = game,
                            selected = selectedGamePackage == game.packageName,
                            favorite = favoriteGames.contains(game.packageName),
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
private fun SelectedGameCard(
    game: GameInfo,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.selected_game),
                style = MaterialTheme.typography.labelLarge
            )
            Text(game.label, style = MaterialTheme.typography.titleMedium)
            Text(game.packageName, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onToggleFavorite,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (favorite) {
                        stringResource(R.string.remove_favorite)
                    } else {
                        stringResource(R.string.add_favorite)
                    }
                )
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_game))
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameInfo,
    selected: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                onClick = onSelect
            ) {
                Column(
                    modifier = Modifier.padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        game.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2
                    )
                    Text(
                        if (selected) "SELECCIONADO" else game.packageName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
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
private fun ConnectedAccountsCard() {
    val context = LocalContext.current
    val store = remember(context) { ConnectedGameAccountsStore(context) }
    val accounts by store.accountsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var platformName by rememberSaveable { mutableStateOf(GamePlatform.STEAM.name) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var publicId by rememberSaveable { mutableStateOf("") }
    var browserError by rememberSaveable { mutableStateOf(false) }

    val platform = GamePlatform.valueOf(platformName)

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
                        browserError = !GamePlatformLinks.openOfficialLogin(context, GamePlatform.STEAM)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.accounts_steam_login))
                }
                Button(
                    onClick = {
                        browserError = !GamePlatformLinks.openOfficialLogin(context, GamePlatform.EPIC_GAMES)
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
                    showAddDialog = true
                }
            ) {
                Text(stringResource(R.string.accounts_add))
            }

            accounts.forEach { account ->
                ConnectedAccountRow(
                    account = account,
                    onRemove = { scope.launch { store.remove(account.id) } },
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
                                onClick = { platformName = item.name },
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
                        value = publicId,
                        onValueChange = { publicId = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.accounts_public_id)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = displayName.isNotBlank() && publicId.isNotBlank(),
                    onClick = {
                        scope.launch {
                            store.add(platform, displayName, publicId)
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
    onRemove: () -> Unit,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.platform.title + " · " + account.displayName)
                Text(
                    account.publicId,
                    style = MaterialTheme.typography.bodySmall
                )
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
private fun SettingsScreen(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_scroll")
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        ConnectedAccountsCard()
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
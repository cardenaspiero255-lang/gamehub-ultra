package com.cardenaspiero255.gamehubultra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var performanceController: PerformanceController
    private var selectedProfileName: String = PerformanceProfile.BALANCED.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedProfileName = savedInstanceState?.getString(KEY_PROFILE)
            ?: PerformanceProfile.BALANCED.name
        val selectedProfile = PerformanceProfile.entries.firstOrNull { it.name == selectedProfileName }
            ?: PerformanceProfile.BALANCED

        val capabilities = DeviceCapabilitiesProvider.get(this)
        performanceController = PerformanceController(capabilities)
        val initialState = performanceController.apply(selectedProfile, window)
        val device = DeviceInfoProvider.get(this)

        setContent {
            GameHubUltraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameHubUltraApp(
                        initialState = initialState,
                        device = device,
                        onProfileSelected = { profile ->
                            selectedProfileName = profile.name
                            performanceController.apply(profile, window)
                        }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PROFILE, selectedProfileName)
        super.onSaveInstanceState(outState)
    }

    private companion object {
        const val KEY_PROFILE = "selected_profile"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GameHubUltraApp(
    initialState: PerformanceState,
    device: DeviceInfo,
    onProfileSelected: (PerformanceProfile) -> PerformanceState
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(initialState) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedProfileName by rememberSaveable { mutableStateOf(initialState.selectedProfile.name) }
    var selectedGamePackage by rememberSaveable {
        mutableStateOf(GameSelectionStore.getSelectedGame(context))
    }

    fun selectProfile(profile: PerformanceProfile) {
        selectedProfileName = profile.name
        state = onProfileSelected(profile)
    }

    val tabs = listOf(
        stringResource(R.string.nav_inicio),
        stringResource(R.string.nav_biblioteca),
        stringResource(R.string.nav_ajustes)
    )
    val tabIcons = listOf("⌂", "▦", "⚙")

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.hero_title)) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(
                                tabIcons[index],
                                modifier = Modifier.semantics {
                                    contentDescription = label
                                }
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                state = state,
                device = device,
                selectedProfileName = selectedProfileName,
                onProfileSelected = ::selectProfile
            )
            1 -> LibraryScreen(
                modifier = Modifier.padding(padding),
                selectedGamePackage = selectedGamePackage,
                onGameSelected = {
                    selectedGamePackage = it
                    GameSelectionStore.saveSelectedGame(context, it)
                }
            )
            else -> SettingsScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: PerformanceState,
    device: DeviceInfo,
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.titleMedium
            )
        }
        item { ActiveProfileCard(state) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.performance_modes),
                    style = MaterialTheme.typography.titleLarge
                )
                PerformanceProfile.entries.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        selected = selectedProfileName == profile.name,
                        onClick = { onProfileSelected(profile) }
                    )
                }
            }
        }
        item {
            BoosterOptions(
                selectedProfileName = selectedProfileName,
                onProfileSelected = onProfileSelected
            )
        }
        item { DeviceStatusCard(device, state.capabilities) }
    }
}

@Composable
private fun ActiveProfileCard(state: PerformanceState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
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
            modifier = Modifier.padding(16.dp),
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
            modifier = Modifier.padding(18.dp),
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
            modifier = Modifier.padding(18.dp),
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
    onGameSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var discovery by remember { mutableStateOf<GameDiscoveryResult?>(null) }
    LaunchedEffect(context, refreshToken) {
        discovery = withContext(Dispatchers.IO) {
            GameLibrary.discover(context)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall
        )

        when {
            result == null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.library_loading),
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
            result.failed -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
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
                        modifier = Modifier.padding(18.dp),
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

                selectedGamePackage?.let { selected ->
                    result.games.firstOrNull {
                        it.packageName == selected
                    }?.let { game ->
                        SelectedGameCard(
                            game = game,
                            context = context
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = result.games,
                        key = { it.packageName }
                    ) { game ->
                        GameRow(
                            game = game,
                            selected = selectedGamePackage == game.packageName,
                            onSelect = {
                                onGameSelected(game.packageName)
                            },
                            onOpen = {
                                openGame(context, game.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedGameCard(
    game: GameInfo,
    context: Context
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.selected_game),
                style = MaterialTheme.typography.labelLarge
            )
            Text(game.label, style = MaterialTheme.typography.titleMedium)
            Text(game.packageName, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { openGame(context, game.packageName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_game))
            }
        }
    }
}

@Composable
private fun GameRow(
    game: GameInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(game.label, style = MaterialTheme.typography.titleMedium)
            Text(game.packageName, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (selected) {
                        stringResource(R.string.game_selected)
                    } else {
                        stringResource(R.string.select_game)
                    }
                )
            }
            if (selected) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_game))
                }
            }
        }
    }
}

private fun openGame(context: Context, packageName: String) {
    context.packageManager
        .getLaunchIntentForPackage(packageName)
        ?.let(context::startActivity)
}

object GameSelectionStore {
    private const val PREFS_NAME = "gamehub_ultra"
    private const val KEY_SELECTED_GAME = "selected_game_package"

    fun getSelectedGame(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_GAME, null)

    fun saveSelectedGame(context: Context, packageName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_GAME, packageName)
            .apply()
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
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
                modifier = Modifier.padding(18.dp),
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

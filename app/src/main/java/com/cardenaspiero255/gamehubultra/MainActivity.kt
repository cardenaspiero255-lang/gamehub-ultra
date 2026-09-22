package com.cardenaspiero255.gamehubultra

import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme

class MainActivity : ComponentActivity() {
    private lateinit var performanceController: PerformanceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capabilities = DeviceCapabilitiesProvider.get(this)
        performanceController = PerformanceController(capabilities)
        val initialState = performanceController.apply(PerformanceProfile.BALANCED, window)
        val device = DeviceInfoProvider.get(this)

        setContent {
            GameHubUltraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameHubUltraApp(
                        initialState = initialState,
                        device = device,
                        onProfileSelected = { profile ->
                            performanceController.apply(profile, window)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubUltraApp(
    initialState: PerformanceState,
    device: DeviceInfo,
    onProfileSelected: (PerformanceProfile) -> PerformanceState
) {
    var state by remember { mutableStateOf(initialState) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.nav_inicio),
        stringResource(R.string.nav_biblioteca),
        stringResource(R.string.nav_ajustes)
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.hero_title)) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text((index + 1).toString()) },
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
                onProfileSelected = { profile -> state = onProfileSelected(profile) }
            )
            1 -> LibraryScreen(Modifier.padding(padding))
            else -> SettingsScreen(Modifier.padding(padding))
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: PerformanceState,
    device: DeviceInfo,
    onProfileSelected: (PerformanceProfile) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.hero_subtitle), style = MaterialTheme.typography.titleMedium)
        }
        item { ActiveProfileCard(state) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.performance_modes), style = MaterialTheme.typography.titleLarge)
                PerformanceProfile.entries.forEach { profile ->
                    ProfileCard(profile, state.selectedProfile == profile, { onProfileSelected(profile) })
                }
            }
        }
        item { DeviceStatusCard(device, state.capabilities) }
    }
}

@Composable
private fun ActiveProfileCard(state: PerformanceState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.active_profile), style = MaterialTheme.typography.labelLarge)
            Text(state.selectedProfile.title, style = MaterialTheme.typography.headlineSmall)
            Text(state.selectedProfile.description)
            Text(if (state.sustainedModeApplied) stringResource(R.string.sustained_applied) else stringResource(R.string.sustained_not_applied))
            Text(
                stringResource(R.string.interpolation_intent) + ": " +
                    if (state.selectedProfile.frameInterpolationIntent) stringResource(R.string.interpolation_prioritized)
                    else stringResource(R.string.interpolation_not_requested)
            )
            Text(
                stringResource(R.string.thermal_tradeoff) + ": " +
                    if (state.selectedProfile.acceptsHigherTemperature) stringResource(R.string.accepts_higher_temperature)
                    else stringResource(R.string.no_extra_thermal)
            )
        }
    }
}

@Composable
private fun ProfileCard(profile: PerformanceProfile, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(profile.title, style = MaterialTheme.typography.titleMedium)
                if (selected) Text(stringResource(R.string.selected))
            }
            Text(profile.description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(device: DeviceInfo, capabilities: DeviceCapabilities?) {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    val batteryManager = remember(context) { context.getSystemService(BatteryManager::class.java) }
    val batteryPercent = remember(batteryManager) {
        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    }
    val thermal = remember(powerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager?.currentThermalStatus else null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.device_status), style = MaterialTheme.typography.titleLarge)
            if (batteryPercent != null) {
                Text(stringResource(R.string.battery) + ": " + batteryPercent + "%")
                LinearProgressIndicator(
                    progress = { batteryPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(R.string.battery) + ": " + stringResource(R.string.not_available))
            }
            Text(stringResource(R.string.temperature) + ": " + thermalLabel(thermal))
            DeviceRow(stringResource(R.string.manufacturer), device.manufacturer)
            DeviceRow(stringResource(R.string.model), device.model)
            DeviceRow(stringResource(R.string.android_version), device.androidVersion + " (API " + device.sdkInt + ")")
            DeviceRow(stringResource(R.string.cpu), device.cpuModel)
            DeviceRow(stringResource(R.string.cores), device.cpuCores.toString())
            DeviceRow(stringResource(R.string.ram), device.totalRamMb.toString() + " MB")
            DeviceRow(stringResource(R.string.gpu_vendor), device.gpuVendor ?: stringResource(R.string.not_available))
            DeviceRow(stringResource(R.string.gpu_renderer), device.gpuRenderer ?: stringResource(R.string.not_available))
            DeviceRow(stringResource(R.string.abi), device.supportedAbis.joinToString().ifBlank { stringResource(R.string.not_available) })
            CapabilityRow(stringResource(R.string.sustained_performance), capabilities?.sustainedPerformanceSupported == true)
            CapabilityRow(stringResource(R.string.thermal_api), capabilities?.thermalStatusAvailable == true)
            CapabilityRow(stringResource(R.string.performance_hint_api), capabilities?.performanceHintsAvailable == true)
            Text(stringResource(R.string.capability_note))
        }
    }
}

@Composable
private fun CapabilityRow(label: String, supported: Boolean) {
    DeviceRow(label, if (supported) stringResource(R.string.supported) else stringResource(R.string.not_supported))
}

@Composable
private fun DeviceRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.library_empty_hint))
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.language_value))
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_text))
                Text(stringResource(R.string.limitations), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.limitations_text))
            }
        }
    }
}

@Composable
private fun thermalLabel(status: Int?): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> stringResource(R.string.normal)
        PowerManager.THERMAL_STATUS_LIGHT -> stringResource(R.string.thermal_light)
        PowerManager.THERMAL_STATUS_MODERATE -> stringResource(R.string.thermal_moderate)
        PowerManager.THERMAL_STATUS_SEVERE -> stringResource(R.string.thermal_severe)
        PowerManager.THERMAL_STATUS_CRITICAL -> stringResource(R.string.thermal_critical)
        PowerManager.THERMAL_STATUS_EMERGENCY -> stringResource(R.string.thermal_emergency)
        PowerManager.THERMAL_STATUS_SHUTDOWN -> stringResource(R.string.thermal_shutdown)
        null -> stringResource(R.string.not_available)
        else -> stringResource(R.string.thermal_unknown)
    }

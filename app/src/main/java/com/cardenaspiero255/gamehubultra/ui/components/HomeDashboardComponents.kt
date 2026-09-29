package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.eventLabel
import com.cardenaspiero255.gamehubultra.thermalLabel
import com.cardenaspiero255.gamehubultra.domain.AdaptiveDecision
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessCalculator
import com.cardenaspiero255.gamehubultra.domain.GamingReadinessInput
import com.cardenaspiero255.gamehubultra.domain.PerformanceEvent
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.PeripheralDiagnostics
import com.cardenaspiero255.gamehubultra.platform.PeripheralKind
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import kotlin.math.roundToInt

@Composable
internal fun GameHubStyleHeader(
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
internal fun TusJuegosShelf(
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
internal fun PeripheralsHubCard(peripherals: PeripheralDiagnostics?) {
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
internal fun RuntimeDiagnosticsCard(
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


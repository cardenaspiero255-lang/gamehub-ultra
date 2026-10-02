package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens

@Composable
internal fun SelectedGameCompactBar(
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

private enum class SelectedGameHeroPanel {
    NONE,
    PROFILE,
    DIAGNOSTICS,
    HISTORY
}

@Composable
internal fun InstalledGameCarouselCard(
    game: GameInfo,
    selected: Boolean,
    favorite: Boolean,
    cardWidthDp: Int,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(game.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(game.packageName)
                .toBitmap(width = 72, height = 72)
                .asImageBitmap()
        }.getOrNull()
    }
    val shape = MaterialTheme.shapes.large

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .width(cardWidthDp.dp)
            .heightIn(min = 126.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = shape
            ),
        shape = shape,
        color = if (selected) Color(0xFF16050A) else Color(0xFF0A0A0F)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                iconBitmap?.let { icon ->
                    Image(
                        bitmap = icon,
                        contentDescription = stringResource(
                            R.string.game_icon_content_description,
                            game.label
                        ),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        if (selected) {
                            stringResource(R.string.selected).uppercase()
                        } else {
                            stringResource(R.string.game_installed).uppercase()
                        },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall
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
                    Text(stringResource(R.string.game_play))
                }
            }
        }
    }
}

@Composable
internal fun SelectedGameHero(
    game: GameInfo,
    favorite: Boolean,
    recent: Boolean,
    selectedProfile: PerformanceProfile,
    diagnostics: RuntimeDiagnostics?,
    sessions: List<GameSessionRecord>,
    wideLayout: Boolean,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit,
    onAssistant: () -> Unit
) {
    val context = LocalContext.current
    var activePanelName by rememberSaveable(game.packageName) {
        mutableStateOf(SelectedGameHeroPanel.NONE.name)
    }
    val activePanel = runCatching {
        SelectedGameHeroPanel.valueOf(activePanelName)
    }.getOrDefault(SelectedGameHeroPanel.NONE)
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
                .toBitmap(width = 144, height = 144)
                .asImageBitmap()
        }.getOrNull()
    }
    val recentSessions = remember(sessions) {
        sessions.sortedByDescending { it.startedAtMillis }.take(3)
    }
    val heroShape = MaterialTheme.shapes.extraLarge

    fun togglePanel(panel: SelectedGameHeroPanel) {
        activePanelName =
            if (activePanel == panel) SelectedGameHeroPanel.NONE.name else panel.name
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                shape = heroShape
            ),
        shape = heroShape,
        color = Color(0xFF050508)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        stringResource(R.string.game_hero_kicker),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        stringResource(R.string.selected_game),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = onToggleFavorite) {
                    Text(
                        if (favorite) "★ " + stringResource(R.string.library_filter_favorites)
                        else "☆ " + stringResource(R.string.library_filter_favorites)
                    )
                }
            }

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroIdentityPanel(
                        game = game,
                        installed = installed,
                        recent = recent,
                        versionName = versionName,
                        selectedProfile = selectedProfile,
                        iconBitmap = iconBitmap,
                        onOpen = onOpen,
                        modifier = Modifier.weight(1.7f)
                    )
                    HeroRuntimePanel(
                        diagnostics = diagnostics,
                        modifier = Modifier.weight(0.9f),
                        compact = false
                    )
                }
            } else {
                HeroIdentityPanel(
                    game = game,
                    installed = installed,
                    recent = recent,
                    versionName = versionName,
                    selectedProfile = selectedProfile,
                    iconBitmap = iconBitmap,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroRuntimePanel(
                    diagnostics = diagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true
                )
            }

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_profile),
                        selected = activePanel == SelectedGameHeroPanel.PROFILE,
                        onClick = { togglePanel(SelectedGameHeroPanel.PROFILE) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_diagnostics),
                        selected = activePanel == SelectedGameHeroPanel.DIAGNOSTICS,
                        onClick = { togglePanel(SelectedGameHeroPanel.DIAGNOSTICS) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_history),
                        selected = activePanel == SelectedGameHeroPanel.HISTORY,
                        onClick = { togglePanel(SelectedGameHeroPanel.HISTORY) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_assistant),
                        selected = false,
                        onClick = onAssistant,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_profile),
                        selected = activePanel == SelectedGameHeroPanel.PROFILE,
                        onClick = { togglePanel(SelectedGameHeroPanel.PROFILE) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_diagnostics),
                        selected = activePanel == SelectedGameHeroPanel.DIAGNOSTICS,
                        onClick = { togglePanel(SelectedGameHeroPanel.DIAGNOSTICS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_history),
                        selected = activePanel == SelectedGameHeroPanel.HISTORY,
                        onClick = { togglePanel(SelectedGameHeroPanel.HISTORY) },
                        modifier = Modifier.weight(1f)
                    )
                    HeroQuickAction(
                        label = stringResource(R.string.game_quick_assistant),
                        selected = false,
                        onClick = onAssistant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (activePanel) {
                SelectedGameHeroPanel.NONE -> Unit
                SelectedGameHeroPanel.PROFILE -> {
                    PerformanceProfile.entries.forEach { profile ->
                        TextButton(
                            onClick = {
                                onProfileSelected(profile)
                                activePanelName = SelectedGameHeroPanel.NONE.name
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
                SelectedGameHeroPanel.DIAGNOSTICS -> {
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
                            diagnostics.battery.percent?.let { it.toString() + "%" }
                                ?: stringResource(R.string.not_available)
                        )
                        DeviceRow(
                            stringResource(R.string.runtime_refresh),
                            diagnostics.refresh.currentRefreshRateHz?.let {
                                it.toInt().toString() + " Hz"
                            } ?: stringResource(R.string.not_measured)
                        )
                        DeviceRow(
                            stringResource(R.string.runtime_latency),
                            diagnostics.connectivity.latencyMs?.let {
                                it.toString() + " ms"
                            } ?: stringResource(R.string.not_measured)
                        )
                        DeviceRow(
                            stringResource(R.string.game_metric_memory),
                            diagnostics.memory.usedPercent.toString() + "%"
                        )
                        DeviceRow(
                            stringResource(R.string.runtime_storage),
                            diagnostics.storage.freePercent.toString() + "%"
                        )
                    }
                }
                SelectedGameHeroPanel.HISTORY -> {
                    if (recentSessions.isEmpty()) {
                        Text(
                            stringResource(R.string.game_history_empty),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        recentSessions.forEach { session ->
                            val duration = session.durationMillis?.let(::formatDuration)
                                ?: stringResource(R.string.session_active)
                            DeviceRow(session.profileName, duration)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroIdentityPanel(
    game: GameInfo,
    installed: Boolean,
    recent: Boolean,
    versionName: String?,
    selectedProfile: PerformanceProfile,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onOpen: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF0B0B10)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        modifier = Modifier.size(104.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        game.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        game.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    versionName?.let {
                        Text(
                            stringResource(R.string.game_version, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HeroTag(
                    text = stringResource(
                        if (installed) R.string.game_installed else R.string.game_not_installed
                    )
                )
                HeroTag(text = localizedProfileTitle(selectedProfile))
                if (recent) {
                    HeroTag(text = stringResource(R.string.library_filter_recent))
                }
            }

            Button(
                onClick = onOpen,
                enabled = installed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.game_play),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun HeroRuntimePanel(
    diagnostics: RuntimeDiagnostics?,
    modifier: Modifier,
    compact: Boolean
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF09090D)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (diagnostics == null) {
                Text(
                    stringResource(R.string.game_diagnostics_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val refresh = diagnostics.refresh.currentRefreshRateHz
                    ?.let { it.toInt().toString() + " Hz" }
                    ?: stringResource(R.string.not_measured)
                val battery = diagnostics.battery.percent
                    ?.let { it.toString() + "%" }
                    ?: stringResource(R.string.not_available)
                val latency = diagnostics.connectivity.latencyMs
                    ?.let { it.toString() + " ms" }
                    ?: stringResource(R.string.not_measured)

                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        HeroMetric(
                            stringResource(R.string.game_metric_refresh),
                            refresh,
                            Modifier.weight(1f)
                        )
                        HeroMetric(
                            stringResource(R.string.game_metric_thermal),
                            thermalLabel(diagnostics.thermal.status),
                            Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        HeroMetric(
                            stringResource(R.string.game_metric_memory),
                            diagnostics.memory.usedPercent.toString() + "%",
                            Modifier.weight(1f)
                        )
                        HeroMetric(
                            stringResource(R.string.game_metric_battery),
                            battery,
                            Modifier.weight(1f)
                        )
                    }
                    HeroMetric(
                        stringResource(R.string.game_metric_latency),
                        latency,
                        Modifier.fillMaxWidth()
                    )
                } else {
                    HeroMetric(
                        stringResource(R.string.game_metric_refresh),
                        refresh,
                        Modifier.fillMaxWidth()
                    )
                    HeroMetric(
                        stringResource(R.string.game_metric_thermal),
                        thermalLabel(diagnostics.thermal.status),
                        Modifier.fillMaxWidth()
                    )
                    HeroMetric(
                        stringResource(R.string.game_metric_memory),
                        diagnostics.memory.usedPercent.toString() + "%",
                        Modifier.fillMaxWidth()
                    )
                    HeroMetric(
                        stringResource(R.string.game_metric_battery),
                        battery,
                        Modifier.fillMaxWidth()
                    )
                    HeroMetric(
                        stringResource(R.string.game_metric_latency),
                        latency,
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF111116)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                label.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color(0xFF18060B)
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroQuickAction(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) Color(0xFF24070D) else Color(0xFF0B0B10),
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)) {
            Text(
                label.uppercase(),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun GameTile(
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


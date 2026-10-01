package com.cardenaspiero255.gamehubultra.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

@Composable
internal fun SelectedGameCard(
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


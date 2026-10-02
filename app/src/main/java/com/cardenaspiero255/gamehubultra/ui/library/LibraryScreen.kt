package com.cardenaspiero255.gamehubultra.ui.library

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.GameLauncher
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.data.GameSessionRecord
import com.cardenaspiero255.gamehubultra.data.StoreLibraryGame
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.ui.components.GameTile
import com.cardenaspiero255.gamehubultra.ui.components.SelectedGameHero
import com.cardenaspiero255.gamehubultra.ui.components.StoreLibrarySection
import com.cardenaspiero255.gamehubultra.ui.layout.LibraryLayoutPolicy
import com.cardenaspiero255.gamehubultra.ui.library.state.LibraryLocalFilter
import com.cardenaspiero255.gamehubultra.ui.library.state.LibraryUiEvent
import com.cardenaspiero255.gamehubultra.ui.library.state.filterLibraryGames
import com.cardenaspiero255.gamehubultra.ui.library.state.rememberLibraryUiStateHolder
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryScreen(
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
    val libraryStateHolder = rememberLibraryUiStateHolder()
    val libraryUiState = libraryStateHolder.state
    LaunchedEffect(context, libraryUiState.refreshToken, manualGamePackages) {
        val discovery = withContext(Dispatchers.IO) {
            GameLibrary.discover(context, manualGamePackages)
        }
        libraryStateHolder.onEvent(LibraryUiEvent.DiscoveryLoaded(discovery))
    }

    LaunchedEffect(context, libraryUiState.addGameDialogVisible) {
        if (libraryUiState.addGameDialogVisible) {
            val launchableApps = withContext(Dispatchers.IO) {
                GameLibrary.discoverNonGameLaunchableApps(context)
            }
            libraryStateHolder.onEvent(
                LibraryUiEvent.LaunchableAppsLoaded(launchableApps)
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                libraryStateHolder.onEvent(LibraryUiEvent.Resumed)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val result = libraryUiState.discovery
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
    val visibleGames = remember(
        orderedGames,
        libraryUiState.query,
        libraryUiState.localFilter,
        favoriteGames,
        recentGamePackages
    ) {
        filterLibraryGames(
            games = orderedGames,
            query = libraryUiState.query,
            localFilter = libraryUiState.localFilter,
            favoriteGames = favoriteGames,
            recentGamePackages = recentGamePackages
        )
    }

    val visibleStoreGames = remember(storeGames, libraryUiState.query) {
        storeGames.filter {
            libraryUiState.query.isBlank() ||
                it.title.contains(libraryUiState.query, ignoreCase = true) ||
                it.platformGameId.contains(libraryUiState.query, ignoreCase = true)
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
                    onClick = {
                        libraryStateHolder.onEvent(
                            LibraryUiEvent.AddGameDialogVisibilityChanged(true)
                        )
                    }
                ) {
                    Text(stringResource(R.string.add_game))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = libraryUiState.query,
                onValueChange = { query ->
                    libraryStateHolder.onEvent(LibraryUiEvent.QueryChanged(query))
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.library_search)) }
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    LibraryLocalFilter.ALL to R.string.library_filter_all,
                    LibraryLocalFilter.FAVORITES to R.string.library_filter_favorites,
                    LibraryLocalFilter.RECENT to R.string.library_filter_recent
                ).forEach { (filter, labelRes) ->
                    FilterChip(
                        selected = libraryUiState.localFilter == filter,
                        onClick = {
                            libraryStateHolder.onEvent(
                                LibraryUiEvent.LocalFilterChanged(filter)
                            )
                        },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            StoreLibrarySection(games = visibleStoreGames)
        }

        if (libraryUiState.launchFailed) {
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

                if (result.games.isNotEmpty() && visibleGames.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(
                                if (libraryUiState.query.isNotBlank()) {
                                    R.string.library_search_empty
                                } else {
                                    R.string.library_filter_empty
                                }
                            )
                        )
                    }
                }

                selectedGamePackage?.let { selected ->
                    visibleGames.firstOrNull {
                        it.packageName == selected
                    }?.let { game ->
                        val favorite = favoriteGames.contains(game.packageName)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SelectedGameHero(
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
                                    val succeeded = openGame(context, game.packageName)
                                    libraryStateHolder.onEvent(
                                        LibraryUiEvent.GameLaunchResult(succeeded)
                                    )
                                    if (succeeded) {
                                        onGameOpened(game.packageName)
                                    }
                                }
                            )
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
                            libraryStateHolder.onEvent(LibraryUiEvent.GameSelected)
                            onGameSelected(game.packageName)
                        },
                        onToggleFavorite = {
                            onToggleFavorite(
                                game.packageName,
                                !favoriteGames.contains(game.packageName)
                            )
                        },
                        onOpen = {
                            val succeeded = openGame(context, game.packageName)
                            libraryStateHolder.onEvent(
                                LibraryUiEvent.GameLaunchResult(succeeded)
                            )
                            if (succeeded) {
                                onGameOpened(game.packageName)
                            }
                        }
                    )
                }
            }
        }
    }

    if (libraryUiState.addGameDialogVisible) {
        val candidates = libraryUiState.launchableApps
        AlertDialog(
            onDismissRequest = {
                libraryStateHolder.onEvent(
                    LibraryUiEvent.AddGameDialogVisibilityChanged(false)
                )
            },
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
                TextButton(
                    onClick = {
                        libraryStateHolder.onEvent(
                            LibraryUiEvent.AddGameDialogVisibilityChanged(false)
                        )
                    }
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}


private fun openGame(context: Context, packageName: String): Boolean =
    GameLauncher.launch(context, packageName)

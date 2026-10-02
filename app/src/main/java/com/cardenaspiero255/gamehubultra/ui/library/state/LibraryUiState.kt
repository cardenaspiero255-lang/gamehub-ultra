package com.cardenaspiero255.gamehubultra.ui.library.state

/**
 * Presentation-owned state for the Library screen.
 *
 * Runtime discovery results and add-game candidates live here as transient state.
 * The saver intentionally persists only user-facing presentation flags and query
 * text so runtime data is refreshed after restoration instead of being serialized.
 */
internal enum class LibraryLocalFilter { ALL, FAVORITES, RECENT }

internal fun filterLibraryGames(
    games: List<com.cardenaspiero255.gamehubultra.GameInfo>,
    query: String,
    localFilter: LibraryLocalFilter,
    favoriteGames: Set<String>,
    recentGamePackages: List<String>,
): List<com.cardenaspiero255.gamehubultra.GameInfo> {
    val locallyFiltered = when (localFilter) {
        LibraryLocalFilter.ALL -> games
        LibraryLocalFilter.FAVORITES ->
            games.filter { game -> favoriteGames.contains(game.packageName) }
        LibraryLocalFilter.RECENT -> {
            val gamesByPackage = games.associateBy { game -> game.packageName }
            recentGamePackages
                .mapNotNull(gamesByPackage::get)
                .distinctBy { game -> game.packageName }
        }
    }

    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return locallyFiltered

    return locallyFiltered.filter { game ->
        game.label.contains(normalizedQuery, ignoreCase = true) ||
            game.packageName.contains(normalizedQuery, ignoreCase = true)
    }
}

internal data class LibraryUiState(
    val refreshToken: Int = 0,
    val launchFailed: Boolean = false,
    val addGameDialogVisible: Boolean = false,
    val query: String = "",
    val localFilter: LibraryLocalFilter = LibraryLocalFilter.ALL,
    val selectedGameDetailsVisible: Boolean = false,
    val discovery: com.cardenaspiero255.gamehubultra.GameDiscoveryResult? = null,
    val launchableApps: List<com.cardenaspiero255.gamehubultra.GameInfo> = emptyList(),
)

internal sealed interface LibraryUiEvent {
    data class QueryChanged(val query: String) : LibraryUiEvent
    data object Resumed : LibraryUiEvent
    data class LocalFilterChanged(val filter: LibraryLocalFilter) : LibraryUiEvent
    data object GameSelected : LibraryUiEvent
    data class GameLaunchResult(val succeeded: Boolean) : LibraryUiEvent
    data class AddGameDialogVisibilityChanged(val visible: Boolean) : LibraryUiEvent
    data class SelectedGameDetailsVisibilityChanged(val visible: Boolean) : LibraryUiEvent
    data class DiscoveryLoaded(
        val result: com.cardenaspiero255.gamehubultra.GameDiscoveryResult
    ) : LibraryUiEvent
    data class LaunchableAppsLoaded(
        val apps: List<com.cardenaspiero255.gamehubultra.GameInfo>
    ) : LibraryUiEvent
}

internal fun LibraryUiState.reduce(event: LibraryUiEvent): LibraryUiState =
    when (event) {
        is LibraryUiEvent.QueryChanged -> copy(query = event.query)
        LibraryUiEvent.Resumed -> copy(refreshToken = refreshToken + 1)
        is LibraryUiEvent.LocalFilterChanged -> copy(localFilter = event.filter)
        LibraryUiEvent.GameSelected -> copy(launchFailed = false)
        is LibraryUiEvent.GameLaunchResult -> copy(launchFailed = !event.succeeded)
        is LibraryUiEvent.AddGameDialogVisibilityChanged ->
            copy(addGameDialogVisible = event.visible)
        is LibraryUiEvent.SelectedGameDetailsVisibilityChanged ->
            copy(selectedGameDetailsVisible = event.visible)
        is LibraryUiEvent.DiscoveryLoaded -> copy(discovery = event.result)
        is LibraryUiEvent.LaunchableAppsLoaded -> copy(launchableApps = event.apps)
    }

internal class LibraryUiStateHolder(initialState: LibraryUiState = LibraryUiState()) {
    private val mutableState = androidx.compose.runtime.mutableStateOf(initialState)

    val state: LibraryUiState
        get() = mutableState.value

    fun onEvent(event: LibraryUiEvent) {
        mutableState.value = mutableState.value.reduce(event)
    }
}

@androidx.compose.runtime.Composable
internal fun rememberLibraryUiStateHolder(): LibraryUiStateHolder {
    val saver = androidx.compose.runtime.saveable.Saver<LibraryUiStateHolder, List<Any?>>(
        save = { holder ->
            listOf(
                holder.state.refreshToken,
                holder.state.launchFailed,
                holder.state.addGameDialogVisible,
                holder.state.query,
                holder.state.localFilter.name,
                holder.state.selectedGameDetailsVisible,
            )
        },
        restore = { values ->
            LibraryUiStateHolder(
                LibraryUiState(
                    refreshToken = values[0] as Int,
                    launchFailed = values[1] as Boolean,
                    addGameDialogVisible = values[2] as Boolean,
                    query = values[3] as String,
                    localFilter = LibraryLocalFilter.valueOf(values[4] as String),
                    selectedGameDetailsVisible = values[5] as Boolean,
                )
            )
        },
    )
    return androidx.compose.runtime.saveable.rememberSaveable(saver = saver) {
        LibraryUiStateHolder()
    }
}

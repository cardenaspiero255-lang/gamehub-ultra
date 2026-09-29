package com.cardenaspiero255.gamehubultra.ui.library.state

/**
 * Presentation-owned state for the Library screen.
 *
 * Runtime discovery data remains outside this contract until its dedicated cut,
 * keeping this first extraction behavior-preserving and independently testable.
 */
internal data class LibraryUiState(
    val refreshToken: Int = 0,
    val launchFailed: Boolean = false,
    val addGameDialogVisible: Boolean = false,
    val query: String = "",
    val selectedGameDetailsVisible: Boolean = false,
    val discovery: com.cardenaspiero255.gamehubultra.data.GameDiscoveryResult? = null,
    val launchableApps: List<com.cardenaspiero255.gamehubultra.data.GameInfo> = emptyList(),
)

internal sealed interface LibraryUiEvent {
    data class QueryChanged(val query: String) : LibraryUiEvent
    data object Resumed : LibraryUiEvent
    data object GameSelected : LibraryUiEvent
    data class GameLaunchResult(val succeeded: Boolean) : LibraryUiEvent
    data class AddGameDialogVisibilityChanged(val visible: Boolean) : LibraryUiEvent
    data class SelectedGameDetailsVisibilityChanged(val visible: Boolean) : LibraryUiEvent
    data class DiscoveryLoaded(
        val result: com.cardenaspiero255.gamehubultra.data.GameDiscoveryResult
    ) : LibraryUiEvent
    data class LaunchableAppsLoaded(
        val apps: List<com.cardenaspiero255.gamehubultra.data.GameInfo>
    ) : LibraryUiEvent
}

internal fun LibraryUiState.reduce(event: LibraryUiEvent): LibraryUiState =
    when (event) {
        is LibraryUiEvent.QueryChanged -> copy(query = event.query)
        LibraryUiEvent.Resumed -> copy(refreshToken = refreshToken + 1)
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
                    selectedGameDetailsVisible = values[4] as Boolean,
                )
            )
        },
    )
    return androidx.compose.runtime.saveable.rememberSaveable(saver = saver) {
        LibraryUiStateHolder()
    }
}

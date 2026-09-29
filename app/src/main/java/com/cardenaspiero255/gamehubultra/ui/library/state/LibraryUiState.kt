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
)

internal sealed interface LibraryUiEvent {
    data class QueryChanged(val query: String) : LibraryUiEvent
    data object Resumed : LibraryUiEvent
    data object GameSelected : LibraryUiEvent
    data class GameLaunchResult(val succeeded: Boolean) : LibraryUiEvent
    data class AddGameDialogVisibilityChanged(val visible: Boolean) : LibraryUiEvent
    data class SelectedGameDetailsVisibilityChanged(val visible: Boolean) : LibraryUiEvent
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
    }

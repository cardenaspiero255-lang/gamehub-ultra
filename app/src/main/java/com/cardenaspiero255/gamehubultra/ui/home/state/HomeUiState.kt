package com.cardenaspiero255.gamehubultra.ui.home.state

/**
 * Presentation-owned state for the Home screen.
 *
 * The quick-voice visibility flag is saveable. Runtime discovery count and reveal requests are
 * transient so they can be recomputed without replaying a stale scroll request after restoration.
 */
internal data class HomeUiState(
    val localGameCount: Int = 0,
    val quickVoiceOpen: Boolean = false,
    val quickVoiceRevealRequest: Int = 0,
)

internal sealed interface HomeUiEvent {
    data class GameCountLoaded(val count: Int) : HomeUiEvent
    data object QuickVoiceToggled : HomeUiEvent
}

/** Applies one Home UI event without side effects. */
internal fun HomeUiState.reduce(event: HomeUiEvent): HomeUiState =
    when (event) {
        is HomeUiEvent.GameCountLoaded -> copy(localGameCount = event.count)
        HomeUiEvent.QuickVoiceToggled -> {
            if (quickVoiceOpen) {
                copy(quickVoiceOpen = false)
            } else {
                copy(
                    quickVoiceOpen = true,
                    quickVoiceRevealRequest = quickVoiceRevealRequest + 1,
                )
            }
        }
    }

/** Owns the current Home UI state and routes events through the pure reducer. */
internal class HomeUiStateHolder(initialState: HomeUiState = HomeUiState()) {
    private val mutableState = androidx.compose.runtime.mutableStateOf(initialState)

    val state: HomeUiState
        get() = mutableState.value

    fun onEvent(event: HomeUiEvent) {
        mutableState.value = mutableState.value.reduce(event)
    }
}

/** Remembers Home presentation state while leaving runtime-only values transient. */
@androidx.compose.runtime.Composable
internal fun rememberHomeUiStateHolder(): HomeUiStateHolder {
    val saver = androidx.compose.runtime.saveable.Saver<HomeUiStateHolder, Boolean>(
        save = { holder -> holder.state.quickVoiceOpen },
        restore = { quickVoiceOpen ->
            HomeUiStateHolder(HomeUiState(quickVoiceOpen = quickVoiceOpen))
        },
    )
    return androidx.compose.runtime.saveable.rememberSaveable(saver = saver) {
        HomeUiStateHolder()
    }
}

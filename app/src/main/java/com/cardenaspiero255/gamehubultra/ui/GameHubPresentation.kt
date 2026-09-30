package com.cardenaspiero255.gamehubultra.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cardenaspiero255.gamehubultra.GameHubUltraApp
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionBootstrap
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme

internal object GameHubPresentation {

    /**
     * Owns the Compose presentation boundary while runtime construction remains in production
     * composition. Deep links are reduced to presentation-only navigation state before rendering.
     */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    fun Content(
        activity: ComponentActivity,
        bootstrap: GameHubProductionBootstrap,
        deepLinkHost: String?
    ) {
        val initialTab = initialTabFor(deepLinkHost)
        val gameHubViewModel: GameHubViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    GameHubViewModel(
                        application = activity.application,
                        dependencies = bootstrap.viewModelDependencyFactory.create()
                    )
                }
            }
        )

        GameHubUltraTheme {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }
            ) {
                GameHubUltraApp(
                    initialState = bootstrap.initialState,
                    device = bootstrap.device,
                    viewModel = gameHubViewModel,
                    ultraRuntime = bootstrap.ultraRuntime,
                    storeLibraryRepository = bootstrap.storeLibraryRepository,
                    optimizationMemoryRepository = bootstrap.optimizationMemoryRepository,
                    initialTab = initialTab,
                    onProfileApplied = { profile ->
                        bootstrap.performanceController.apply(profile, activity.window)
                    }
                )
            }
        }
    }

    /** Maps the only supported presentation deep link to its initial tab. */
    internal fun initialTabFor(deepLinkHost: String?): Int =
        if (deepLinkHost == LIBRARY_DEEP_LINK_HOST) LIBRARY_TAB else DEFAULT_TAB

    private const val DEFAULT_TAB = 0
    private const val LIBRARY_TAB = 1
    private const val LIBRARY_DEEP_LINK_HOST = "library"
}

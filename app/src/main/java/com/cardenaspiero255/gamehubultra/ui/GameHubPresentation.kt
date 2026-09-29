package com.cardenaspiero255.gamehubultra.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.GameHubUltraApp
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionBootstrap
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme

internal object GameHubPresentation {

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    fun Content(
        activity: ComponentActivity,
        bootstrap: GameHubProductionBootstrap,
        deepLinkHost: String?
    ) {
        val initialTab = if (deepLinkHost == "library") 1 else 0
        val gameHubViewModel: GameHubViewModel = viewModel()

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
                    initialTab = initialTab,
                    onProfileApplied = { profile ->
                        bootstrap.performanceController.apply(profile, activity.window)
                    }
                )
            }
        }
    }
}

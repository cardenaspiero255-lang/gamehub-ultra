package com.cardenaspiero255.gamehubultra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardenaspiero255.gamehubultra.ai.UltraProductionQueryExecutor
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.GameHubViewModel
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme
import com.cardenaspiero255.gamehubultra.voice.AndroidContinuousVoiceGateway
import com.cardenaspiero255.gamehubultra.voice.ContinuousVoiceController

class MainActivity : ComponentActivity() {
    private lateinit var performanceController: PerformanceController

    override fun onStart() {
        super.onStart()
        ContinuousVoiceController(
            AndroidContinuousVoiceGateway(this)
        ).resumeIfEnabled()
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTab = when (intent?.data?.host) {
            "library" -> 1
            else -> 0
        }

        val capabilities = DeviceCapabilitiesProvider.get(this)
        performanceController = PerformanceController(capabilities)
        val initialState =
            performanceController.apply(PerformanceProfile.BALANCED, window)
        val device = DeviceInfoProvider.get(this)

        setContent {
            val gameHubViewModel: GameHubViewModel = viewModel()
            GameHubUltraTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    GameHubUltraApp(
                        initialState = initialState,
                        device = device,
                        viewModel = gameHubViewModel,
                        queryExecutor = UltraProductionQueryExecutor,
                        initialTab = initialTab,
                        onProfileApplied = { profile ->
                            performanceController.apply(profile, window)
                        }
                    )
                }
            }
        }
    }
}

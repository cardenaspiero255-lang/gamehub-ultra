package com.cardenaspiero255.gamehubultra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.ui.GameHubPresentation

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        GameHubProductionComposition.resumeContinuousVoice(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bootstrap = GameHubProductionComposition.create(this)

        setContent {
            GameHubPresentation.Content(
                activity = this,
                bootstrap = bootstrap,
                deepLinkHost = intent?.data?.host
            )
        }
    }
}

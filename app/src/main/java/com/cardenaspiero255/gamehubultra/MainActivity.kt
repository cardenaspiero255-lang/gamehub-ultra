package com.cardenaspiero255.gamehubultra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.session.SessionCoachMonitorService
import com.cardenaspiero255.gamehubultra.session.SessionCoachNotificationPermission
import com.cardenaspiero255.gamehubultra.ui.GameHubPresentation

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        SessionCoachMonitorService.finishOnReturn(this)
        GameHubProductionComposition.resumeContinuousVoice(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCoachNotificationPermissionIfNeeded()
        val bootstrap = GameHubProductionComposition.create(this)

        setContent {
            GameHubPresentation.Content(
                activity = this,
                bootstrap = bootstrap,
                deepLinkHost = intent?.data?.host
            )
        }
    }

    private fun requestCoachNotificationPermissionIfNeeded() {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = Build.VERSION.SDK_INT,
                granted = granted
            )
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_COACH_NOTIFICATIONS
            )
        }
    }

    private companion object {
        const val REQUEST_COACH_NOTIFICATIONS = 45
    }
}

package com.cardenaspiero255.gamehubultra.ui.components

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.cardenaspiero255.gamehubultra.domain.DriverStrategy
import com.cardenaspiero255.gamehubultra.domain.GpuFamily
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class SmartPerformanceCardRobolectricTest {
    @Test
    fun `smart performance actions render with real compose wiring`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()
        val recommendation = SmartPerformanceRecommendation(
            profile = PerformanceProfile.BALANCED,
            reason = "Mantener estabilidad térmica.",
            safeFallback = PerformanceProfile.BALANCED,
            evidence = listOf("battery=80", "thermal=normal"),
            score = 92,
            driverStrategy = DriverStrategy.SYSTEM_ONLY,
            gpuFamily = GpuFamily.MALI
        )

        activity.setContent {
            MaterialTheme {
                SmartPerformanceCard(
                    recommendation = recommendation,
                    observations = emptyList(),
                    canRevert = true,
                    onApply = {},
                    onReject = {},
                    onRevert = {},
                    onClearMemory = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

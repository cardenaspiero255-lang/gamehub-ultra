package com.cardenaspiero255.gamehubultra

import android.content.pm.ApplicationInfo
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameHubUltraSupportTest {
    @Test
    fun navigationLabelsAndProfilesRemainSelectable() {
        assertEquals(3, PerformanceProfile.entries.size)
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)
    }

    @Test
    fun gameDiscoveryAcceptsDeclaredGames() {
        assertTrue(
            GameLibrary.isGameApplication(
                category = ApplicationInfo.CATEGORY_GAME,
                flags = 0,
                sdkInt = 35
            )
        )
    }

    @Test
    fun gameDiscoveryAcceptsGameFlag() {
        assertTrue(
            GameLibrary.isGameApplication(
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                flags = ApplicationInfo.FLAG_IS_GAME,
                sdkInt = 35
            )
        )
    }

    @Test
    fun gameDiscoveryRejectsNonGameApplications() {
        assertFalse(
            GameLibrary.isGameApplication(
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                flags = 0,
                sdkInt = 35
            )
        )
    }

    @Test
    fun gameDiscoveryDoesNotReadGameFlagBeforeApi26() {
        assertFalse(
            GameLibrary.isGameApplication(
                category = ApplicationInfo.CATEGORY_UNDEFINED,
                flags = ApplicationInfo.FLAG_IS_GAME,
                sdkInt = 25
            )
        )
    }

    @Test
    fun batteryBroadcastUsesScaleCorrectly() {
        assertEquals(50, BatteryTelemetry.fromBroadcast(level = 50, scale = 100))
        assertEquals(50, BatteryTelemetry.fromBroadcast(level = 1, scale = 2))
        assertEquals(100, BatteryTelemetry.fromBroadcast(level = 150, scale = 100))
    }

    @Test
    fun invalidBatteryValuesBecomeUnavailable() {
        assertNull(BatteryTelemetry.fromBroadcast(level = -1, scale = 100))
        assertNull(BatteryTelemetry.fromBroadcast(level = 1, scale = 0))
        assertNull(BatteryTelemetry.sanitizePercentage(101))
    }
}

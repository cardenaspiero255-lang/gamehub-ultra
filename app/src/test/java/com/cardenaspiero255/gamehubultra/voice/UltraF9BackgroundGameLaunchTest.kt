package com.cardenaspiero255.gamehubultra.voice

import android.content.Intent
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runtime-independent regression of Android 14 background activity starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9BackgroundGameLaunchTest {
    @Test
    fun backgroundWithoutPrivilegeMustNotPretendThatAnActivityOpened() {
        assertEquals(
            UltraBackgroundLaunchPath.USER_TAP_NOTIFICATION,
            UltraBackgroundGameLaunchPolicy.decide(
                appVisible = false,
                overlayAllowed = false,
                notificationsAllowed = true
            )
        )
        assertEquals(
            UltraBackgroundLaunchPath.NOTIFICATION_PERMISSION_REQUIRED,
            UltraBackgroundGameLaunchPolicy.decide(
                appVisible = false,
                overlayAllowed = false,
                notificationsAllowed = false
            )
        )
    }

    @Test
    fun visibleGameHubOrExplicitlyGrantedOverlayCanDirectlyRequestLaunch() {
        assertEquals(
            UltraBackgroundLaunchPath.REQUEST_DIRECT,
            UltraBackgroundGameLaunchPolicy.decide(
                appVisible = true,
                overlayAllowed = false,
                notificationsAllowed = false
            )
        )
        assertEquals(
            UltraBackgroundLaunchPath.REQUEST_DIRECT,
            UltraBackgroundGameLaunchPolicy.decide(
                appVisible = false,
                overlayAllowed = true,
                notificationsAllowed = false
            )
        )
    }

    @Test
    fun backgroundLaunchDefersToNotificationAndDoesNotCallStartActivity() {
        var directLaunches = 0
        var notified = 0
        val dispatcher = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
            visible = { false },
            overlayAllowed = { false },
            notificationsAllowed = { true },
            startActivity = { directLaunches++ },
            postNotification = { _, _ -> notified++; true }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.TAP_REQUIRED,
            dispatcher.launch("com.example.brawlstars")
        )
        assertEquals(0, directLaunches)
        assertEquals(1, notified)
    }

    @Test
    fun visibleAppDirectlyRequestsOpenAndBackgroundPermissionIsNeverInferred() {
        var directLaunches = 0
        var notified = 0
        val dispatcher = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
            visible = { true },
            overlayAllowed = { false },
            notificationsAllowed = { false },
            startActivity = { directLaunches++ },
            postNotification = { _, _ -> notified++; true }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.REQUESTED,
            dispatcher.launch("com.example.brawlstars")
        )
        assertEquals(1, directLaunches)
        assertEquals(0, notified)
    }

    @Test
    fun denialOrFailedLaunchNeverSpeaksGameOpened() {
        val missingNotificationPermission = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
            visible = { false },
            overlayAllowed = { false },
            notificationsAllowed = { false },
            startActivity = { error("Must never call startActivity") },
            postNotification = { _, _ -> error("Notifications denied") }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED,
            missingNotificationPermission.launch("com.example.brawlstars")
        )
        val unexpectedStartFailure = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
            visible = { true },
            overlayAllowed = { false },
            notificationsAllowed = { true },
            startActivity = { throw SecurityException("Android blocked background launch") },
            postNotification = { _, _ -> true }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.TAP_REQUIRED,
            unexpectedStartFailure.launch("com.example.brawlstars")
        )
    }

    @Test
    fun nullLaunchIntentDoesNotCreateNotificationOrActivity() {
        val dispatcher = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { null },
            visible = { false },
            overlayAllowed = { false },
            notificationsAllowed = { true },
            startActivity = { error("Unexpected start") },
            postNotification = { _, _ -> error("Unexpected notification") }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.NOT_INSTALLED,
            dispatcher.launch("com.example.notinstalled")
        )
    }
}

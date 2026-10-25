package com.cardenaspiero255.gamehubultra.voice

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.Manifest
import android.os.Build
import org.robolectric.Shadows
import android.content.Intent
import android.os.Bundle
import com.cardenaspiero255.gamehubultra.MainActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * F9 regression: receiving a recognized command in the background is not
 * evidence that Android displayed a game. Test the user-tappable fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraF9BackgroundLaunchAndroidIntegrationTest {
    private val packageName = "com.example.brawlstars"

    @Test
    fun notificationContainsAnExplicitUserTapPendingIntentAndChannel() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val launch = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(UltraBackgroundGameLaunchNotification.show(app, packageName, launch))
        assertNotNull(manager.getNotificationChannel("ultra_voice_launch_actions"))
        val notificationId = 24100 + (packageName.hashCode() and 0x0FFF)
        val notification = assertNotNull(Shadows.shadowOf(manager).getNotification(notificationId))
        assertNotNull(notification.contentIntent)
        assertEquals(
            "GameHub Ultra: abrir juego",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun launchFactoryRequiresAnInstalledPackageBeforeAnySystemSideEffect() {
        val app = RuntimeEnvironment.getApplication()
        val dispatcher = UltraBackgroundGameLaunchDispatcher.from(app)
        assertEquals(
            UltraBackgroundGameLaunchOutcome.NOT_INSTALLED,
            dispatcher.launch("com.example.gamehub.absent.package")
        )
    }

    @Test
    fun activityVisibilityTracksActualStartsAndStopsWithoutNegativeCounts() {
        val activity = Mockito.mock(Activity::class.java)
        val tracker = UltraAppVisibilityTracker()
        // Repeated stops may be delivered during teardown; they must clamp at zero.
        tracker.onActivityStopped(activity)
        assertFalse(UltraAppVisibilityTracker.isVisible())
        tracker.onActivityStarted(activity)
        assertTrue(UltraAppVisibilityTracker.isVisible())
        tracker.onActivityStarted(activity)
        tracker.onActivitySaveInstanceState(activity, Bundle())
        tracker.onActivityStopped(activity)
        assertTrue(UltraAppVisibilityTracker.isVisible())
        tracker.onActivityStopped(activity)
        tracker.onActivityStopped(activity)
        assertFalse(UltraAppVisibilityTracker.isVisible())
    }

    @Test
    fun failedNotificationPostingNeverClaimsThatTheGameOpened() {
        for (post in listOf<(String, Intent) -> Boolean>(
            { _, _ -> false },
            { _, _ -> throw SecurityException("Notifications blocked") }
        )) {
            var starts = 0
            val dispatcher = UltraBackgroundGameLaunchDispatcher(
                launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
                visible = { false },
                overlayAllowed = { false },
                notificationsAllowed = { true },
                startActivity = { starts++ },
                postNotification = post
            )
            assertEquals(UltraBackgroundGameLaunchOutcome.FAILED, dispatcher.launch(packageName))
            assertEquals(0, starts)
        }
    }

    @Test
    fun directLaunchExceptionWithoutNotificationGrantReportsPermissionRequired() {
        val dispatcher = UltraBackgroundGameLaunchDispatcher(
            launchIntentForPackage = { Intent(Intent.ACTION_MAIN).setPackage(it) },
            visible = { true },
            overlayAllowed = { false },
            notificationsAllowed = { false },
            startActivity = { throw SecurityException("Android denied activity start") },
            postNotification = { _, _ -> error("Must not post without permission") }
        )
        assertEquals(
            UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED,
            dispatcher.launch(packageName)
        )
    }
    @Test
    fun installedGameLauncherInBackgroundProducesUserTappableNotification() {
        val app = RuntimeEnvironment.getApplication()
        val installedPackage = app.packageName
        assertNotNull(app.packageManager.getLaunchIntentForPackage(installedPackage))
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(UltraAppVisibilityTracker.isVisible())
        val outcome = UltraBackgroundGameLaunchDispatcher.from(app).launch(installedPackage)
        assertEquals(UltraBackgroundGameLaunchOutcome.TAP_REQUIRED, outcome)
        val id = 24100 + (installedPackage.hashCode() and 0x0FFF)
        val posted = assertNotNull(Shadows.shadowOf(manager).getNotification(id))
        assertNotNull(posted.contentIntent)
        assertEquals("GameHub Ultra: abrir juego",
            posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun realFactoryDoesNotCreateLaunchNotificationWithoutAndroid13Permission() {
        val app = RuntimeEnvironment.getApplication()
        val installedPackage = app.packageName
        assertNotNull(app.packageManager.getLaunchIntentForPackage(installedPackage))
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(UltraAppVisibilityTracker.isVisible())
        assertEquals(
            UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED,
            UltraBackgroundGameLaunchDispatcher.from(app).launch(installedPackage)
        )
        val id = 24100 + (installedPackage.hashCode() and 0x0FFF)
        assertEquals(null, Shadows.shadowOf(manager).getNotification(id))
    }

    @Test
    fun foregroundAppFactoryRequestsLauncherButNeverClaimsObservedOpening() {
        val app = RuntimeEnvironment.getApplication()
        val installedPackage = app.packageName
        assertNotNull(app.packageManager.getLaunchIntentForPackage(installedPackage))
        val tracker = UltraAppVisibilityTracker()
        val activity = Mockito.mock(Activity::class.java)
        tracker.onActivityStarted(activity)
        try {
            assertEquals(
                UltraBackgroundGameLaunchOutcome.REQUESTED,
                UltraBackgroundGameLaunchDispatcher.from(app).launch(installedPackage)
            )
        } finally {
            tracker.onActivityStopped(activity)
        }
        assertFalse(UltraAppVisibilityTracker.isVisible())
    }

}

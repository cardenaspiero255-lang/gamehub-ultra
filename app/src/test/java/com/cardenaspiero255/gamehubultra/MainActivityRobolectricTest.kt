package com.cardenaspiero255.gamehubultra

import android.content.Context
import android.os.Looper
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityRobolectricTest {
    @Before
    fun clearPermissionState() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        activity.getSharedPreferences("session_coach_permission", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstNotificationPromptIsPersistedAndNotRepeatedByTheActivityGate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val method = MainActivity::class.java.getDeclaredMethod(
            "requestCoachNotificationPermissionIfNeeded"
        ).apply { isAccessible = true }

        method.invoke(activity)

        val prefs = activity.getSharedPreferences(
            "session_coach_permission",
            Context.MODE_PRIVATE
        )
        assertTrue(prefs.getBoolean("notification_requested", false))

        method.invoke(activity)
        assertTrue(prefs.getBoolean("notification_requested", false))
    }

    @Test
    @Config(sdk = [32])
    fun preAndroid13DoesNotPersistNotificationPromptState() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val method = MainActivity::class.java.getDeclaredMethod(
            "requestCoachNotificationPermissionIfNeeded"
        ).apply { isAccessible = true }

        method.invoke(activity)

        assertFalse(
            activity.getSharedPreferences(
                "session_coach_permission",
                Context.MODE_PRIVATE
            ).getBoolean("notification_requested", false)
        )
    }

    @Test
    fun activityCreateAndStartExerciseProductionLifecycleWiring() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        assertNotNull(controller.get())
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        controller.pause().stop().destroy()
    }
}

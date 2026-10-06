package com.cardenaspiero255.gamehubultra.session

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCoachMonitorServiceTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetStore() {
        context.getSharedPreferences(
            "gamehub_ultra_session_coach",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    @Test
    fun startRejectsBlankPackageWithoutCreatingSession() {
        assertNull(
            SessionCoachMonitorService.start(
                context = context,
                packageName = "   ",
                nowMillis = 100L,
                sessionId = "session"
            )
        )
        assertFalse(SessionCoachSessionStore(context).hasActiveSession())
    }

    @Test
    fun finishOnReturnPersistsCompletedSessionAndPostsSummary() {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("session", "game.a", 1_000L))
        store.appendSnapshot(
            "session",
            SessionCoachSnapshot(
                timestampMillis = 1_000L,
                batteryPercent = 90,
                thermalStatus = 1,
                thermalHeadroom = 0.25f,
                refreshRateHz = 120f,
                latencyMs = 30L
            )
        )
        store.appendSnapshot(
            "session",
            SessionCoachSnapshot(
                timestampMillis = 2_000L,
                batteryPercent = 70,
                thermalStatus = 3,
                thermalHeadroom = 0.85f,
                refreshRateHz = 60f,
                latencyMs = 150L
            )
        )

        val finished = SessionCoachMonitorService.finishOnReturn(
            context = context,
            nowMillis = 3_000L
        )

        assertNotNull(finished)
        assertEquals(3_000L, finished.endedAtMillis)
        assertFalse(store.hasActiveSession())
        assertEquals("session", store.readLastCompletedSession()?.sessionId)

        val manager = context.getSystemService(NotificationManager::class.java)
        val summary = shadowOf(manager).getNotification(45_002)
        assertNotNull(summary)
        assertEquals(
            "Resumen de sesión · Ultra",
            summary.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
    }

    @Test
    fun finishOnReturnIsNoOpWithoutActiveSession() {
        assertNull(
            SessionCoachMonitorService.finishOnReturn(
                context = context,
                nowMillis = 3_000L
            )
        )
    }

    @Test
    fun cancelLaunchDiscardsProvisionalSession() {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("session", "game.a", 1_000L))

        SessionCoachMonitorService.cancelLaunch(context)

        assertFalse(store.hasActiveSession())
    }

    @Test
    fun foregroundNotificationContainsCoachContentAndStopAction() {
        val notification = SessionCoachNotifications.foreground(
            context = context,
            title = "Preparación 88/100",
            detail = "Listo para jugar.",
            important = true
        )

        assertEquals(
            "Preparación 88/100",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            "Listo para jugar.",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, notification.actions.size)
    }

    @Test
    fun serviceRejectsMalformedStartAndAcceptsExplicitStop() {
        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()

        val malformed = service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_START),
            0,
            1
        )
        val stopped = service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            0,
            2
        )

        assertEquals(Service.START_NOT_STICKY, malformed)
        assertEquals(Service.START_NOT_STICKY, stopped)
        assertNull(service.onBind(null))
        controller.destroy()
    }
}

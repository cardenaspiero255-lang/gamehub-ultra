package com.cardenaspiero255.gamehubultra.session

import android.app.Notification
import android.app.NotificationManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.PowerManager
import android.os.Process
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSignal
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
    fun startCreatesProvisionalSessionForValidPackage() {
        val id = SessionCoachMonitorService.start(
            context = context,
            packageName = "game.a",
            nowMillis = 1_000L,
            sessionId = "session-start"
        )

        assertEquals("session-start", id)
        val active = assertNotNull(SessionCoachSessionStore(context).readActiveSession())
        assertEquals("game.a", active.packageName)
        assertEquals(1_000L, active.startedAtMillis)

        SessionCoachMonitorService.cancelLaunch(context)
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
            detail = "Listo para jugar."
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
    fun validStartCollectsRealSnapshotAndPreSessionMessage() = runBlocking {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("live-session", "game.a", System.currentTimeMillis()))
        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()

        val result = service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_START)
                .putExtra("session_id", "live-session")
                .putExtra("package_name", "game.a"),
            0,
            1
        )

        assertEquals(Service.START_NOT_STICKY, result)
        withTimeout(5_000L) {
            while (store.readActiveSession()?.preSessionMessage == null) {
                delay(50)
            }
        }

        val active = assertNotNull(store.readActiveSession())
        assertTrue(active.samples.isNotEmpty())
        assertNotNull(active.preSessionMessage)

        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            0,
            2
        )
        controller.destroy()
        Unit
    }

    @Test
    fun defaultArgumentsStartAndFinishSession() {
        val id = assertNotNull(
            SessionCoachMonitorService.start(
                context = context,
                packageName = "game.default"
            )
        )

        val finished = assertNotNull(
            SessionCoachMonitorService.finishOnReturn(context)
        )

        assertEquals(id, finished.sessionId)
        assertFalse(SessionCoachSessionStore(context).hasActiveSession())
    }

    @Test
    fun startingNewSessionSummarizesPreviousActiveSession() {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("previous", "game.previous", 1_000L))
        listOf(
            SessionCoachSnapshot(1_000L, 90, 1, 0.25f, 120f, 30L),
            SessionCoachSnapshot(2_000L, 80, 3, 0.82f, 60f, 140L),
            SessionCoachSnapshot(3_000L, 70, 3, 0.86f, 60f, 155L),
            SessionCoachSnapshot(4_000L, 60, 3, 0.88f, 60f, 150L)
        ).forEach { store.appendSnapshot("previous", it) }

        val id = SessionCoachMonitorService.start(
            context = context,
            packageName = "game.current",
            nowMillis = 5_000L,
            sessionId = "current"
        )

        assertEquals("current", id)
        val summary = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(45_002)
        assertNotNull(summary)
        assertTrue(
            summary.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?.toString()
                .orEmpty()
                .contains("•")
        )
        SessionCoachMonitorService.cancelLaunch(context)
    }

    @Test
    fun foregroundServiceStartFailureDiscardsProvisionalSession() {
        val failingContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw IllegalStateException("foreground start blocked")
            }
        }

        assertNull(
            SessionCoachMonitorService.start(
                context = failingContext,
                packageName = "game.fail",
                nowMillis = 100L,
                sessionId = "failed-session"
            )
        )
        assertFalse(SessionCoachSessionStore(failingContext).hasActiveSession())
    }

    @Test
    fun latencyPolicyRequiresValidatedUnmeteredNetworkAndRespectsInterval() {
        val healthy = ConnectivityTelemetry(
            networkHandle = 7L,
            connected = true,
            validated = true,
            metered = false,
            transport = "wifi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = null
        )

        assertTrue(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy,
                lastLatencyCheckAt = 0L,
                nowMillis = 1_000L
            )
        )
        assertFalse(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy,
                lastNetworkHandle = 7L,
                lastLatencyCheckAt = 1_000L,
                nowMillis = 10_000L
            )
        )
        assertTrue(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy,
                lastNetworkHandle = 7L,
                lastLatencyCheckAt = 1_000L,
                nowMillis = 31_000L
            )
        )
        assertFalse(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy.copy(connected = false),
                lastLatencyCheckAt = 0L,
                nowMillis = 31_000L
            )
        )
        assertFalse(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy.copy(validated = false),
                lastLatencyCheckAt = 0L,
                nowMillis = 31_000L
            )
        )
        assertFalse(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy.copy(metered = true),
                lastLatencyCheckAt = 0L,
                nowMillis = 31_000L
            )
        )
        assertFalse(
            SessionCoachMonitorService.shouldProbeLatency(
                healthy.copy(networkHandle = null),
                lastLatencyCheckAt = 0L,
                nowMillis = 31_000L
            )
        )
        assertFalse(SessionCoachMonitorService.shouldResetLatency(healthy))
        assertTrue(SessionCoachMonitorService.shouldResetLatency(healthy.copy(connected = false)))
        assertTrue(SessionCoachMonitorService.shouldResetLatency(healthy.copy(validated = false)))
        assertTrue(SessionCoachMonitorService.shouldResetLatency(healthy.copy(metered = true)))
    }

    @Test
    fun unknownServiceActionStopsWithoutStartingMonitor() {
        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()

        val result = service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction("com.cardenaspiero255.gamehubultra.UNKNOWN"),
            0,
            1
        )

        assertEquals(Service.START_NOT_STICKY, result)
        controller.destroy()
    }

    @Test
    fun staleSessionTimesOutImmediatelyAndPostsSummary() = runBlocking {
        val now = System.currentTimeMillis()
        val store = SessionCoachSessionStore(context)
        assertTrue(
            store.beginSession(
                "timeout-session",
                "game.timeout",
                now - (4L * 60L * 60L * 1_000L) - 1_000L
            )
        )
        listOf(
            SessionCoachSnapshot(now - 4_000L, 90, 1, 0.25f, 120f, 30L),
            SessionCoachSnapshot(now - 3_000L, 80, 3, 0.82f, 60f, 140L),
            SessionCoachSnapshot(now - 2_000L, 70, 3, 0.86f, 60f, 155L),
            SessionCoachSnapshot(now - 1_000L, 60, 3, 0.88f, 60f, 150L)
        ).forEach { store.appendSnapshot("timeout-session", it) }

        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()
        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_START)
                .putExtra("session_id", "timeout-session")
                .putExtra("package_name", "game.timeout"),
            0,
            1
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        withTimeout(5_000L) {
            while (
                store.hasActiveSession() ||
                store.readLastCompletedSession() == null ||
                shadowOf(manager).getNotification(45_002) == null
            ) {
                delay(50)
            }
        }

        assertNotNull(store.readLastCompletedSession())
        assertNotNull(shadowOf(manager).getNotification(45_002))
        controller.destroy()
        Unit
    }

    @Test
    fun existingSampleCanProduceMidSessionObservation() = runBlocking {
        val store = SessionCoachSessionStore(context)
        assertTrue(
            store.beginSession(
                "observed-session",
                "game.observe",
                System.currentTimeMillis()
            )
        )
        store.appendSnapshot(
            "observed-session",
            SessionCoachSnapshot(
                timestampMillis = System.currentTimeMillis() - 1_000L,
                batteryPercent = 100,
                thermalStatus = 0,
                thermalHeadroom = 0.0f,
                refreshRateHz = 240f,
                latencyMs = 0L
            )
        )

        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()
        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_START)
                .putExtra("session_id", "observed-session")
                .putExtra("package_name", "game.observe"),
            0,
            1
        )

        withTimeout(5_000L) {
            while (store.readActiveSession()?.samples?.size ?: 0 < 2) {
                delay(50)
            }
        }
        delay(100)

        assertNotNull(store.readActiveSession()?.latestObservation)
        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            0,
            2
        )
        controller.destroy()
        Unit
    }

    @Test
    @Config(sdk = [33])
    fun preAndroid14StartUsesTwoArgumentForegroundPath() {
        val store = SessionCoachSessionStore(context)
        assertTrue(
            store.beginSession(
                "sdk33-session",
                "game.sdk33",
                System.currentTimeMillis()
            )
        )
        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()

        val result = service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_START)
                .putExtra("session_id", "sdk33-session")
                .putExtra("package_name", "game.sdk33"),
            0,
            1
        )

        assertEquals(Service.START_NOT_STICKY, result)
        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            0,
            2
        )
        controller.destroy()
    }

    @Test
    fun explicitStopFinalizesStoredSessionAtStopTime() = runBlocking {
        val store = SessionCoachSessionStore(context)
        assertTrue(store.beginSession("stop-session", "game.stop", 1_000L))
        store.appendSnapshot(
            "stop-session",
            SessionCoachSnapshot(1_500L, 80, 1, 0.2f, 120f, 40L)
        )
        val controller = Robolectric.buildService(SessionCoachMonitorService::class.java).create()
        val service = controller.get()

        service.onStartCommand(
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            0,
            1
        )

        withTimeout(2_000L) {
            while (store.hasActiveSession()) delay(20)
        }
        val completed = assertNotNull(store.readLastCompletedSession())
        assertEquals("stop-session", completed.sessionId)
        assertTrue((completed.endedAtMillis ?: 0L) >= 1_500L)
        controller.destroy()
        Unit
    }

    @Test
    fun networkHandleChangeForcesFreshLatencyProbe() {
        val switched = ConnectivityTelemetry(
            networkHandle = 8L,
            connected = true,
            validated = true,
            metered = false,
            transport = "wifi",
            downstreamBandwidthKbps = 100_000,
            latencyMs = null
        )

        assertTrue(
            SessionCoachMonitorService.shouldProbeLatency(
                network = switched,
                lastNetworkHandle = 7L,
                lastLatencyCheckAt = 1_000L,
                nowMillis = 2_000L
            )
        )
        assertTrue(
            SessionCoachMonitorService.shouldResetLatency(
                network = switched,
                lastNetworkHandle = 7L
            )
        )
    }

    @Test
    fun obsoleteMonitorCannotStopReplacementMonitor() {
        assertFalse(
            SessionCoachMonitorService.shouldStopOwnedMonitor(
                activeMonitorSessionId = "new-session",
                expectedSessionId = "old-session"
            )
        )
        assertTrue(
            SessionCoachMonitorService.shouldStopOwnedMonitor(
                activeMonitorSessionId = "new-session",
                expectedSessionId = "new-session"
            )
        )
    }

    @Test
    fun repeatedInactiveGameEvidenceEndsMonitoringButUnknownDoesNot() {
        var count = 0
        count = SessionCoachMonitorService.nextInactiveEvidenceCount(
            SessionCoachGamePresence.UNKNOWN,
            count
        )
        assertEquals(0, count)
        count = SessionCoachMonitorService.nextInactiveEvidenceCount(
            SessionCoachGamePresence.INACTIVE,
            count
        )
        assertFalse(SessionCoachMonitorService.shouldFinishForInactivity(count))
        count = SessionCoachMonitorService.nextInactiveEvidenceCount(
            SessionCoachGamePresence.INACTIVE,
            count
        )
        assertFalse(SessionCoachMonitorService.shouldFinishForInactivity(count))
        count = SessionCoachMonitorService.nextInactiveEvidenceCount(
            SessionCoachGamePresence.INACTIVE,
            count
        )
        assertTrue(SessionCoachMonitorService.shouldFinishForInactivity(count))
        assertEquals(
            0,
            SessionCoachMonitorService.nextInactiveEvidenceCount(
                SessionCoachGamePresence.ACTIVE,
                count
            )
        )
    }

    @Test
    fun actionNotificationPublishesExplicitActionAndFallsBackToDetail() {
        val manager = context.getSystemService(NotificationManager::class.java)

        SessionCoachNotifications.postAction(
            context,
            SessionCoachMessage(
                signal = SessionCoachSignal.LATENCY,
                priority = SessionCoachPriority.ACTION,
                title = "Pico de latencia",
                detail = "Detalle diagnóstico",
                action = "Revisa la conexión"
            )
        )

        val explicit = assertNotNull(shadowOf(manager).getNotification(45_003))
        assertEquals(
            "Pico de latencia",
            explicit.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            "Revisa la conexión",
            explicit.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )

        manager.cancel(45_003)
        SessionCoachNotifications.postAction(
            context,
            SessionCoachMessage(
                signal = SessionCoachSignal.GENERAL,
                priority = SessionCoachPriority.WATCH,
                title = "Observación",
                detail = "Solo detalle"
            )
        )

        val fallback = assertNotNull(shadowOf(manager).getNotification(45_003))
        assertEquals(
            "Solo detalle",
            fallback.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
    }

    @Test
    fun actionNotificationsUseDefaultImportanceChannelOnAndroidOPlus() {
        SessionCoachNotifications.ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val actionChannel = manager.notificationChannels
            .firstOrNull { it.id == "ultra_session_coach_alerts" }

        assertNotNull(actionChannel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, actionChannel.importance)
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

    @Test
    fun presenceResolutionCoversForegroundBackgroundAndUnknownTransitions() {
        assertEquals(
            SessionCoachGamePresence.ACTIVE,
            SessionCoachGamePresenceDetector.resolvePresence(
                latestForegroundPackage = "game.a",
                latestForegroundAt = 10L,
                targetBackgroundAt = Long.MIN_VALUE,
                previousPresence = SessionCoachGamePresence.UNKNOWN,
                targetPackage = "game.a"
            )
        )
        assertEquals(
            SessionCoachGamePresence.INACTIVE,
            SessionCoachGamePresenceDetector.resolvePresence(
                latestForegroundPackage = "other.game",
                latestForegroundAt = 20L,
                targetBackgroundAt = 10L,
                previousPresence = SessionCoachGamePresence.ACTIVE,
                targetPackage = "game.a"
            )
        )
        assertEquals(
            SessionCoachGamePresence.INACTIVE,
            SessionCoachGamePresenceDetector.resolvePresence(
                latestForegroundPackage = null,
                latestForegroundAt = Long.MIN_VALUE,
                targetBackgroundAt = 30L,
                previousPresence = SessionCoachGamePresence.ACTIVE,
                targetPackage = "game.a"
            )
        )
        assertEquals(
            SessionCoachGamePresence.UNKNOWN,
            SessionCoachGamePresenceDetector.resolvePresence(
                latestForegroundPackage = null,
                latestForegroundAt = Long.MIN_VALUE,
                targetBackgroundAt = Long.MIN_VALUE,
                previousPresence = SessionCoachGamePresence.UNKNOWN,
                targetPackage = "game.a"
            )
        )
    }

    @Test
    fun presenceObservationConsumesForegroundAndBackgroundUsageEvents() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val shadowUsage = shadowOf(usage)
        shadowUsage.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage("game.a")
                .setTimeStamp(100_000L)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )
        shadowUsage.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage("game.a")
                .setTimeStamp(110_000L)
                .setEventType(UsageEvents.Event.ACTIVITY_PAUSED)
                .build()
        )
        shadowUsage.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage("other.game")
                .setTimeStamp(115_000L)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )

        assertEquals(
            SessionCoachGamePresence.INACTIVE,
            SessionCoachGamePresenceDetector.observe(
                context = context,
                packageName = "game.a",
                nowMillis = 120_000L
            )
        )
    }

    @Test
    @Config(sdk = [28])
    fun legacyUsageAccessCheckUsesPreQAppOpsPath() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        assertEquals(
            SessionCoachGamePresence.UNKNOWN,
            SessionCoachGamePresenceDetector.observe(
                context = context,
                packageName = "game.a",
                nowMillis = 120_000L
            )
        )
    }

    @Test
    fun presenceObservationStopsImmediatelyWhenScreenIsNotInteractive() {
        val power = context.getSystemService(PowerManager::class.java)
        shadowOf(power).setIsInteractive(false)
        try {
            assertEquals(
                SessionCoachGamePresence.INACTIVE,
                SessionCoachGamePresenceDetector.observe(
                    context = context,
                    packageName = "game.a",
                    nowMillis = 120_000L
                )
            )
        } finally {
            shadowOf(power).setIsInteractive(true)
        }
    }

    @Test
    fun presenceObservationReturnsUnknownWithoutUsageAccess() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        assertEquals(
            SessionCoachGamePresence.UNKNOWN,
            SessionCoachGamePresenceDetector.observe(
                context = context,
                packageName = "game.a",
                nowMillis = 120_000L
            )
        )
    }

    @Test
    fun knownActivePresenceSurvivesAnEmptyUsageWindow() {
        assertEquals(
            SessionCoachGamePresence.ACTIVE,
            SessionCoachGamePresenceDetector.resolvePresence(
                latestForegroundPackage = null,
                latestForegroundAt = Long.MIN_VALUE,
                targetBackgroundAt = Long.MIN_VALUE,
                previousPresence = SessionCoachGamePresence.ACTIVE
            )
        )
    }

    @Test
    fun presenceFromPreviousGameDoesNotLeakIntoNewSession() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        val usage = context.getSystemService(UsageStatsManager::class.java)
        shadowOf(usage).addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage("game.a")
                .setTimeStamp(100_000L)
                .setEventType(UsageEvents.Event.ACTIVITY_RESUMED)
                .build()
        )

        assertEquals(
            SessionCoachGamePresence.ACTIVE,
            SessionCoachGamePresenceDetector.observe(
                context = context,
                packageName = "game.a",
                nowMillis = 110_000L
            )
        )
        assertEquals(
            SessionCoachGamePresence.UNKNOWN,
            SessionCoachGamePresenceDetector.observe(
                context = context,
                packageName = "game.b",
                nowMillis = 400_000L
            )
        )
    }

    @Test
    fun notificationPermissionIsNotRepeatedAfterARecordedDenial() {
        assertFalse(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 35,
                granted = false,
                hasRequestedBefore = true,
                shouldShowRationale = false
            )
        )
        assertFalse(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 35,
                granted = false,
                hasRequestedBefore = true,
                shouldShowRationale = true
            )
        )
        assertTrue(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 35,
                granted = false,
                hasRequestedBefore = false,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun unknownPresenceIsBoundedInsteadOfRunningForFourHours() {
        var count = 0
        repeat(SessionCoachMonitorService.UNKNOWN_EVIDENCE_REQUIRED) {
            count = SessionCoachMonitorService.nextUnknownEvidenceCount(
                SessionCoachGamePresence.UNKNOWN,
                count
            )
        }

        assertTrue(SessionCoachMonitorService.shouldFinishForUnknownPresence(count))
        assertEquals(
            0,
            SessionCoachMonitorService.nextUnknownEvidenceCount(
                SessionCoachGamePresence.ACTIVE,
                count
            )
        )
    }

    @Test
    fun notificationPermissionIsRequestedOnlyWhenAndroidRequiresItAndItIsMissing() {
        assertFalse(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 32,
                granted = false
            )
        )
        assertFalse(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 35,
                granted = true
            )
        )
        assertTrue(
            SessionCoachNotificationPermission.shouldRequest(
                sdkInt = 35,
                granted = false
            )
        )
    }


}

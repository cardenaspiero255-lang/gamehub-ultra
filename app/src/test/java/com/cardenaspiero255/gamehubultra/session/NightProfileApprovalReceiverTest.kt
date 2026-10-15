package com.cardenaspiero255.gamehubultra.session

import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import java.time.ZoneId
import java.time.ZonedDateTime
import org.robolectric.Shadows.shadowOf
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NightProfileApprovalReceiverTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetProposalState() {
        context.getSharedPreferences(
            "gamehub_ultra_session_coach", Context.MODE_PRIVATE
        ).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun applyIntent(packageName: String? = "com.test.game"): Intent =
        Intent(context, NightProfileApprovalReceiver::class.java)
            .setAction(NightProfileApprovalReceiver.ACTION_APPLY)
            .apply {
                if (packageName != null) {
                    putExtra(NightProfileApprovalReceiver.EXTRA_PACKAGE, packageName)
                }
            }

    @Test
    fun forgedNotificationAndMissingUserProposalNeverChangeProfile() {
        val store = SessionCoachSessionStore(context)
        val receiver = NightProfileApprovalReceiver()
        receiver.onReceive(context, Intent(context, NightProfileApprovalReceiver::class.java)
            .setAction("com.test.UNRELATED_ACTION"))
        receiver.onReceive(context, Intent(context, NightProfileApprovalReceiver::class.java))
        receiver.onReceive(context, applyIntent("  "))
        receiver.onReceive(context, applyIntent(null))
        receiver.onReceive(context, applyIntent("com.test.not.pending"))

        assertFalse(store.isNightProfileProposalPending("com.test.not.pending"))
        assertFalse(store.isNightProfileProposalPending("com.test.game"))
    }

    @Test
    fun onlyPersistedOptInCanApplyBalancedCoolerProfileOnce() = runBlocking {
        val game = "com.test.night.approved"
        val store = SessionCoachSessionStore(context)
        assertTrue(store.takeNightProfileProposalToShow(game))
        assertTrue(store.isNightProfileProposalPending(game))

        // The broadcast is an asynchronous Android lifecycle surface.
        // Exercise its suspend write boundary deterministically rather than
        // relying on Robolectric's broadcast/coroutine scheduling.
        withTimeout(10_000L) {
            assertTrue(applyPendingNightProfile(context, " $game "))
        }
        assertFalse(store.isNightProfileProposalPending(game))
        val repository = GameHubProductionComposition.selectionRepository(context)
        val saved = repository.gameProfileConfigFlow(game).first()
        assertEquals(PerformanceProfile.BALANCED, saved?.performanceProfile)
        assertEquals(ThermalPreference.COOLER, saved?.thermalPreference)

        // Even a valid notification action cannot be replayed once consumed.
        assertFalse(applyPendingNightProfile(context, game))
        assertFalse(applyPendingNightProfile(context, "com.test.other"))
        assertFalse(store.isNightProfileProposalPending(game))
    }
    @Test
    fun measuredNightSessionsProduceOneExplicitApprovalActionAndNeverRepeat() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        val store = SessionCoachSessionStore(context)
        val game = "com.test.night.evidence"
        val startAt = ZonedDateTime.of(
            2026, 10, 15, 22, 10, 0, 0, ZoneId.systemDefault()
        ).toInstant().toEpochMilli()
        var last: com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession? = null
        repeat(3) { index ->
            val start = startAt + index * 10L * 60_000L
            val id = "night-evidence-$index"
            assertTrue(store.beginSession(id, game, start))
            repeat(4) { frame ->
                store.appendSnapshot(id, SessionCoachSnapshot(
                    timestampMillis = start + frame * 5_000L,
                    batteryPercent = 18,
                    thermalStatus = 2,
                    thermalHeadroom = 0.8f,
                    refreshRateHz = 90f,
                    latencyMs = null
                ))
            }
            last = store.finishActiveSession(start + 30_000L, id)
            assertTrue(last != null)
        }
        assertEquals(3, store.readRecentGameSessions(packageName = game).size)
        SessionCoachNotifications.postSummary(context, checkNotNull(last))
        assertTrue(store.isNightProfileProposalPending(game))
        val posted = manager.activeNotifications
        assertTrue(posted.isNotEmpty(), "The summary notification should be posted")
        assertTrue(posted.any { status ->
            status.notification.actions?.any { action ->
                action.title.toString().contains("Aplicar ajustes Noche")
            } == true
        }, "The user must see an explicit apply action")
        // The persisted proposal is offered only once, even if a summary
        // refreshes or another valid game session finishes.
        SessionCoachNotifications.postSummary(context, checkNotNull(last))
        assertTrue(store.isNightProfileProposalPending(game))
        val after = manager.activeNotifications
        assertTrue(after.any { notification ->
            notification.notification.actions.isNullOrEmpty()
        }, "Repeated summaries must not re-offer approval")
    }

    @Test
    fun realBroadcastReceiverHandlesExplicitApprovedNightProfileAndPublishesNotification() = runBlocking {
        val game = "com.test.night.broadcast"
        val store = SessionCoachSessionStore(context)
        assertTrue(store.takeNightProfileProposalToShow(game))
        // Register a receiver and dispatch a real broadcast so goAsync()
        // gets lifecycle state while Dispatchers.IO performs the write.
        val receiver = NightProfileApprovalReceiver()
        context.registerReceiver(
            receiver,
            IntentFilter(NightProfileApprovalReceiver.ACTION_APPLY),
            Context.RECEIVER_NOT_EXPORTED
        )
        try {
            // Dynamic receivers match this implicit action; an explicit component
            // addresses only the manifest receiver and can bypass the registered one.
            context.sendBroadcast(
                Intent(NightProfileApprovalReceiver.ACTION_APPLY)
                    .putExtra(NightProfileApprovalReceiver.EXTRA_PACKAGE, game)
            )
            shadowOf(Looper.getMainLooper()).idle()
            withTimeout(12_000L) {
                while (store.isNightProfileProposalPending(game)) {
                    delay(40L)
                }
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
        val profile = GameHubProductionComposition.selectionRepository(context)
            .gameProfileConfigFlow(game).first()
        assertEquals(PerformanceProfile.BALANCED, profile?.performanceProfile)
        assertEquals(ThermalPreference.COOLER, profile?.thermalPreference)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any {
            it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)
                ?.toString()?.contains("Propuesta Noche aplicada") == true
        }, "Successful explicit approval must produce its visible receipt")
        assertFalse(store.isNightProfileProposalPending(game))
    }


    @Test
    fun mutedNotificationsCannotConsumeOneTimeNightApproval() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val store = SessionCoachSessionStore(context)
        val game = "com.test.night.muted"
        val startAt = ZonedDateTime.of(
            2026, 10, 15, 23, 15, 0, 0, ZoneId.systemDefault()
        ).toInstant().toEpochMilli()
        var last: com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession? = null
        repeat(3) { index ->
            val start = startAt + index * 10L * 60_000L
            val id = "night-muted-$index"
            assertTrue(store.beginSession(id, game, start))
            repeat(4) { frame ->
                store.appendSnapshot(id, SessionCoachSnapshot(
                    timestampMillis = start + frame * 5_000L,
                    batteryPercent = 18,
                    thermalStatus = 2,
                    thermalHeadroom = 0.8f,
                    refreshRateHz = 90f,
                    latencyMs = null
                ))
            }
            last = store.finishActiveSession(start + 30_000L, id)
            assertTrue(last != null)
        }
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(NotificationManagerCompat.from(context).areNotificationsEnabled())
        SessionCoachNotifications.postSummary(context, checkNotNull(last))
        assertFalse(store.isNightProfileProposalPending(game),
            "Approval must not be consumed when no notification can reach the user")

        shadowOf(manager).setNotificationsEnabled(true)
        assertTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())
        SessionCoachNotifications.postSummary(context, checkNotNull(last))
        assertTrue(store.isNightProfileProposalPending(game),
            "Enabling notifications must allow the previously unshown proposal")
    }

    @Test
    fun noMeasuredNightHistoryNeverInventsAnApprovalAction() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        val empty = com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession(
            sessionId = "night-empty",
            packageName = "com.test.empty",
            startedAtMillis = 1_000L,
            endedAtMillis = 2_000L,
            samples = emptyList(),
            preSessionMessage = null,
            latestObservation = null
        )
        SessionCoachNotifications.postSummary(context, empty)
        assertFalse(SessionCoachSessionStore(context)
            .isNightProfileProposalPending("com.test.empty"))
        assertTrue(manager.activeNotifications.any { notification ->
            notification.notification.actions.isNullOrEmpty()
        })
    }

}

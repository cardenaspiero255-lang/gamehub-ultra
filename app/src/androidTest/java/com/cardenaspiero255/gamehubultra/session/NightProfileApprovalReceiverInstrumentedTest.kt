package com.cardenaspiero255.gamehubultra.session

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android broadcast and goAsync lifecycle test. Robolectric does not
 * faithfully dispatch this manifest-declared non-exported receiver, so the
 * delivery contract must be checked on an emulator/device instead.
 */
@RunWith(AndroidJUnit4::class)
class NightProfileApprovalReceiverInstrumentedTest {
    @Test
    fun explicitNotificationApprovalPersistsAndAcknowledgesExactlyOnce() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
            ).close()
        }
        val prefs = context.getSharedPreferences(
            "gamehub_ultra_session_coach", Context.MODE_PRIVATE
        )
        assertTrue("Unable to reset proposal state", prefs.edit().clear().commit())
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val game = "com.test.night.instrumented"
        val store = SessionCoachSessionStore(context)
        assertTrue("Explicit approval proposal must exist", store.takeNightProfileProposalToShow(game))

        val intent = Intent(context, NightProfileApprovalReceiver::class.java)
            .setAction(NightProfileApprovalReceiver.ACTION_APPLY)
            .putExtra(NightProfileApprovalReceiver.EXTRA_PACKAGE, game)
        val approval = PendingIntent.getBroadcast(
            context, 439, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        approval.send()

        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (store.isNightProfileProposalPending(game) &&
            SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50L)
        }
        assertFalse("Broadcast never committed explicit approval", store.isNightProfileProposalPending(game))
        val profile = GameHubProductionComposition.selectionRepository(context)
            .gameProfileConfigFlow(game).first()
        assertEquals(PerformanceProfile.BALANCED, profile?.performanceProfile)
        assertEquals(ThermalPreference.COOLER, profile?.thermalPreference)

        val notificationDeadline = SystemClock.elapsedRealtime() + 5_000L
        while (manager.activeNotifications.none {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()?.contains("Propuesta Noche aplicada") == true
        } && SystemClock.elapsedRealtime() < notificationDeadline) {
            Thread.sleep(50L)
        }
        assertTrue("Applied proposal must produce a visible receipt",
            manager.activeNotifications.any {
                it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                    ?.toString()?.contains("Propuesta Noche aplicada") == true
            })
        approval.send()
        assertFalse("The explicit approval cannot be replayed", store.isNightProfileProposalPending(game))
    }
}

package com.cardenaspiero255.gamehubultra.session

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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

        context.sendBroadcast(applyIntent(" $game "))
        shadowOf(Looper.getMainLooper()).idle()

        withTimeout(10_000L) {
            while (store.isNightProfileProposalPending(game)) {
                delay(40L)
            }
        }
        assertFalse(store.isNightProfileProposalPending(game))
        val repository = GameHubProductionComposition.selectionRepository(context)
        val saved = repository.gameProfileConfigFlow(game).first()
        assertEquals(PerformanceProfile.BALANCED, saved?.performanceProfile)
        assertEquals(ThermalPreference.COOLER, saved?.thermalPreference)

        // Even a valid notification action cannot be replayed once consumed.
        context.sendBroadcast(applyIntent(game))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(store.isNightProfileProposalPending(game))
    }
}

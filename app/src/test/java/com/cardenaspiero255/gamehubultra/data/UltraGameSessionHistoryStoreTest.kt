package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UltraGameSessionHistoryStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        context.getSharedPreferences(
            "gamehub_ultra_session_coach", android.content.Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun remembersMultipleFinishedSessionsAcrossStoreRecreation() {
        val store = SessionCoachSessionStore(context)
        repeat(4) { index ->
            val start = 1_000_000L + index * 1_000_000L
            val id = "session-$index"
            assertTrue(store.beginSession(id, "com.example.cod", start))
            repeat(4) { i ->
                store.appendSnapshot(id, SessionCoachSnapshot(
                    timestampMillis = start + i * 15_000L,
                    batteryPercent = 19 - i,
                    thermalStatus = 3,
                    thermalHeadroom = null,
                    refreshRateHz = 90f,
                    latencyMs = null
                ))
            }
            assertNotNull(store.finishActiveSession(start + 60_000L, id))
        }
        val reloaded = SessionCoachSessionStore(context)
            .readRecentGameSessions(packageName = "com.example.cod")
        assertEquals(4, reloaded.size)
        assertTrue(reloaded.all { it.typicalRefreshRateHz == 90 })
        assertEquals(16, reloaded.last().minimumBatteryPercent)
        assertEquals(0, SessionCoachSessionStore(context)
            .readRecentGameSessions(packageName = "no.such.game").size)
    }

    @Test
    fun emptySessionDoesNotCreateInventedHistoricalEvidence() {
        val store = SessionCoachSessionStore(context)
        store.beginSession("empty", "com.test", 100L)
        store.finishActiveSession(300L)
        assertTrue(store.readRecentGameSessions().isEmpty())
    }

    @Test
    fun nightProposalIsPersistedAndIsOnlyShownOncePerGame() {
        val first = SessionCoachSessionStore(context)
        assertTrue(first.takeNightProfileProposalToShow("com.example.cod"))
        assertTrue(!first.takeNightProfileProposalToShow("com.example.cod"))
        val restored = SessionCoachSessionStore(context)
        assertTrue(restored.isNightProfileProposalPending("com.example.cod"))
        assertTrue(!restored.takeNightProfileProposalToShow("com.example.cod"))
        assertTrue(restored.takeNightProfileProposalToShow("com.example.other"))
        assertTrue(restored.markNightProfileProposalApplied("com.example.cod"))
        assertTrue(!restored.isNightProfileProposalPending("com.example.cod"))
        assertTrue(!restored.markNightProfileProposalApplied("com.example.cod"))
        assertTrue(!restored.takeNightProfileProposalToShow("com.example.cod"))
    }
}

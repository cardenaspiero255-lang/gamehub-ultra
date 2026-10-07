package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePersistedState
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePendingDecision
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PerGameAdaptiveStatePreferencesStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        context.getSharedPreferences(
            "gamehub_ultra_adaptive_optimizer",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun stateSurvivesStoreRecreationAndStaysVersionScoped() {
        val v1 = AdaptiveGameKey("game.a", "1")
        val v2 = AdaptiveGameKey("game.a", "2")
        val expected = PerGameAdaptivePersistedState(
            profile = PerformanceProfile.BALANCED,
            candidate = PerformanceProfile.X4,
            confirmations = 1,
            lastChangeMillis = 1234L,
            recoveryProfile = PerformanceProfile.FRAME_INTERPOLATION
        )

        PerGameAdaptiveStatePreferencesStore(context).write(v1, expected)

        val recreated = PerGameAdaptiveStatePreferencesStore(context)
        assertEquals(expected, recreated.read(v1))
        assertNull(recreated.read(v2))
    }

    @Test
    fun corruptPersistedStateFailsClosed() {
        val key = AdaptiveGameKey("game.a", "1")
        context.getSharedPreferences(
            "gamehub_ultra_adaptive_optimizer",
            Context.MODE_PRIVATE
        ).edit()
            .putString(PerGameAdaptiveStatePreferencesStore.preferenceKey(key), "broken")
            .commit()

        assertNull(PerGameAdaptiveStatePreferencesStore(context).read(key))
    }

    @Test
    fun deleteRemovesPersistedState() {
        val key = AdaptiveGameKey("game.delete", "1")
        val store = PerGameAdaptiveStatePreferencesStore(context)
        store.write(
            key,
            PerGameAdaptivePersistedState(
                profile = PerformanceProfile.BALANCED,
                candidate = null,
                confirmations = 0,
                lastChangeMillis = null,
                recoveryProfile = null
            )
        )
        assertEquals(PerformanceProfile.BALANCED, store.read(key)?.profile)

        store.delete(key)

        assertNull(store.read(key))
    }

    @Test
    fun malformedOptionalStateFieldsFailClosed() {
        val key = AdaptiveGameKey("game.corrupt", "1")
        val preferences = context.getSharedPreferences(
            "gamehub_ultra_adaptive_optimizer",
            Context.MODE_PRIVATE
        )
        val storageKey = PerGameAdaptiveStatePreferencesStore.preferenceKey(key)
        val store = PerGameAdaptiveStatePreferencesStore(context)

        preferences.edit().putString(storageKey, "BALANCED|BAD|0||").commit()
        assertNull(store.read(key))

        preferences.edit().putString(storageKey, "BALANCED||0|BAD|").commit()
        assertNull(store.read(key))

        preferences.edit().putString(storageKey, "BALANCED||0||BAD").commit()
        assertNull(store.read(key))
    }


    @Test
    fun ownedRecoveryIsFoundAcrossVersionsButNotForDifferentActiveProfile() {
        val store = PerGameAdaptiveStatePreferencesStore(context)
        store.write(
            AdaptiveGameKey("game.versioned", "1#10"),
            PerGameAdaptivePersistedState(
                profile = PerformanceProfile.BALANCED,
                candidate = null,
                confirmations = 0,
                lastChangeMillis = 500L,
                recoveryProfile = PerformanceProfile.X4
            )
        )
        store.write(
            AdaptiveGameKey("game.versioned", "2#20"),
            PerGameAdaptivePersistedState(
                profile = PerformanceProfile.BALANCED,
                candidate = null,
                confirmations = 0,
                lastChangeMillis = 900L,
                recoveryProfile = PerformanceProfile.FRAME_INTERPOLATION
            )
        )

        assertEquals(
            PerformanceProfile.X4,
            store.ownedRecoveryProfileForPackage(
                packageName = "game.versioned",
                excludingVersion = "2#20",
                activeProfile = PerformanceProfile.BALANCED
            )
        )
        assertNull(
            store.ownedRecoveryProfileForPackage(
                packageName = "game.versioned",
                excludingVersion = "2#20",
                activeProfile = PerformanceProfile.X4
            )
        )
    }

    @Test
    fun pendingDecisionRoundTripsAndClearsDurably() {
        val store = PerGameAdaptiveStatePreferencesStore(context)
        val pending = PerGameAdaptivePendingDecision(
            sessionId = "session-pending",
            key = AdaptiveGameKey("game.pending", "3#30"),
            previousProfile = PerformanceProfile.X4,
            targetProfile = PerformanceProfile.BALANCED,
            targetState = PerGameAdaptivePersistedState(
                profile = PerformanceProfile.BALANCED,
                candidate = null,
                confirmations = 0,
                lastChangeMillis = 123L,
                recoveryProfile = PerformanceProfile.X4
            ),
            eventTimestampMillis = 456L,
            reason = "memoria | térmica"
        )

        store.writePendingDecision(pending)

        assertEquals(
            pending,
            PerGameAdaptiveStatePreferencesStore(context)
                .readPendingDecision("session-pending")
        )
        assertNull(store.readPendingDecision("otra-sesion"))

        store.clearPendingDecision("otra-sesion")
        assertEquals(pending, store.readPendingDecision("session-pending"))

        store.clearPendingDecision("session-pending")
        assertNull(store.readPendingDecision("session-pending"))
    }

    @Test
    fun handledSessionIdSurvivesStoreRecreation() {
        val first = PerGameAdaptiveStatePreferencesStore(context)
        first.markSessionHandled("session-47")

        val recreated = PerGameAdaptiveStatePreferencesStore(context)
        assertTrue(recreated.wasSessionHandled("session-47"))
        assertFalse(recreated.wasSessionHandled("session-other"))
    }

}

package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import com.cardenaspiero255.gamehubultra.domain.AdaptiveGameKey
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptivePersistedState
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
    fun handledSessionIdSurvivesStoreRecreation() {
        val first = PerGameAdaptiveStatePreferencesStore(context)
        first.markSessionHandled("session-47")

        val recreated = PerGameAdaptiveStatePreferencesStore(context)
        assertTrue(recreated.wasSessionHandled("session-47"))
        assertFalse(recreated.wasSessionHandled("session-other"))
    }

}

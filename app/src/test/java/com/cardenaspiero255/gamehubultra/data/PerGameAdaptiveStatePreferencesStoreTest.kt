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
import kotlin.test.assertNull

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
}

package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.OrientationPreference
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.ResolutionTarget
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference
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
class AiProfileProposalStoreTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(
            "gamehub_ultra_ai_profile_builder",
            android.content.Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun appliedVersionAndRollbackConfigSurviveStoreRecreation() {
        val previous = GameProfileConfig(
            performanceProfile = PerformanceProfile.X4,
            thermalPreference = ThermalPreference.PERFORMANCE,
            refreshRateTargetHz = 120,
            resolutionTarget = ResolutionTarget(2400, 1080),
            orientationPreference = OrientationPreference.LANDSCAPE
        )
        val proposal = AiProfileProposal(
            version = 4,
            previousKnownGoodConfig = previous,
            proposedConfig = GameProfileConfig(),
            requiresExplicitApply = true,
            reasons = listOf("test"),
            disabledSettings = emptyList()
        )

        AiProfileProposalStore(context).recordApplied("com.example.game", proposal)

        val restored = AiProfileProposalStore(context)
        assertEquals(5, restored.nextVersion("com.example.game"))
        assertEquals(4, restored.rollbackState("com.example.game")?.version)
        assertEquals(previous, restored.rollbackState("com.example.game")?.previousKnownGoodConfig)

        restored.clearRollback("com.example.game")
        assertNull(restored.rollbackState("com.example.game"))
        assertEquals(5, restored.nextVersion("com.example.game"))
    }

    @Test
    fun versionsAreIsolatedPerGame() {
        val store = AiProfileProposalStore(context)
        val proposal = AiProfileProposal(
            version = 2,
            previousKnownGoodConfig = GameProfileConfig(),
            proposedConfig = GameProfileConfig(performanceProfile = PerformanceProfile.X4),
            requiresExplicitApply = true,
            reasons = emptyList(),
            disabledSettings = emptyList()
        )

        store.recordApplied("game.one", proposal)

        assertEquals(3, store.nextVersion("game.one"))
        assertEquals(1, store.nextVersion("game.two"))
    }
}

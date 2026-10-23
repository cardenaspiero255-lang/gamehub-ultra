package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * C2 #152:41 RED: a late, lower-version proposal must not replace the
 * persisted rollback target or rewind its monotonic version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiProfileProposalMonotonicVersionRegressionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetStore() {
        context.getSharedPreferences("gamehub_ultra_ai_profile_builder", 0)
            .edit().clear().commit()
    }

    private fun proposal(version: Int, previous: PerformanceProfile) =
        AiProfileProposal(
            version = version,
            previousKnownGoodConfig = GameProfileConfig(performanceProfile = previous),
            proposedConfig = GameProfileConfig(performanceProfile = PerformanceProfile.X4),
            requiresExplicitApply = true,
            reasons = listOf("E2E rollback version boundary"),
            disabledSettings = emptyList()
        )

    @Test
    fun lateOlderProposalCannotRewriteCurrentRollbackOrRewindNextVersion() {
        val store = AiProfileProposalStore(context)
        store.recordApplied("com.example.rollback.e2e", proposal(4, PerformanceProfile.BALANCED))
        store.recordApplied("com.example.rollback.e2e", proposal(3, PerformanceProfile.X4))

        val reopened = AiProfileProposalStore(context)
        assertEquals(4, reopened.rollbackState("com.example.rollback.e2e")?.version)
        assertEquals(
            PerformanceProfile.BALANCED,
            reopened.rollbackState("com.example.rollback.e2e")?.previousKnownGoodConfig?.performanceProfile
        )
        assertEquals(5, reopened.nextVersion("com.example.rollback.e2e"))
    }

    @Test
    fun lateProposalFromOtherGameDoesNotBlockForwardProgressForThisGame() {
        val store = AiProfileProposalStore(context)
        store.recordApplied("game.alpha", proposal(4, PerformanceProfile.BALANCED))
        store.recordApplied("game.beta", proposal(2, PerformanceProfile.X4))
        store.recordApplied("game.alpha", proposal(5, PerformanceProfile.X4))

        assertEquals(6, store.nextVersion("game.alpha"))
        assertEquals(3, store.nextVersion("game.beta"))
        assertEquals(
            PerformanceProfile.X4,
            store.rollbackState("game.alpha")?.previousKnownGoodConfig?.performanceProfile
        )
    }
}

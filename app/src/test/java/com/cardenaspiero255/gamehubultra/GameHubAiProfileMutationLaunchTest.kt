package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameHubAiProfileMutationLaunchTest {
    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test
    fun notifiesUIOnlyAfterSuccessfulDurableMutation() = runBlocking {
        val operationStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = {
            operationStarted.complete(Unit)
            allowCompletion.await()
            true
        }, onChanged = { updates++ })
        operationStarted.await()
        assertEquals(0, updates)
        allowCompletion.complete(Unit)
        job.join()
        assertEquals(1, updates)
        assertTrue(job.isCompleted)
    }

    @Test
    fun rejectedVersionDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = { false }) {
            updates++
        }
        job.join()
        assertEquals(0, updates)
        assertFalse(job.isCancelled)
    }

    @Test
    fun caughtPersistenceFailureDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "rollback", mutation = {
            error("deliberate DataStore failure")
        }) { updates++ }
        job.join()
        assertEquals(0, updates)
        assertFalse(job.isCancelled)
    }

    @Test
    fun cancellationIsPropagatedAndDoesNotRefreshUI() = runBlocking {
        var updates = 0
        val job = launchAiProfileMutation(scope(), "apply", mutation = {
            throw CancellationException("screen left")
        }) { updates++ }
        job.join()
        assertEquals(0, updates)
        assertTrue(job.isCancelled)
    }

    private fun fixture(version: Int, proposed: PerformanceProfile) = AiProfileProposal(
        version = version,
        previousKnownGoodConfig = GameProfileConfig(performanceProfile = PerformanceProfile.BALANCED),
        proposedConfig = GameProfileConfig(performanceProfile = proposed),
        requiresExplicitApply = true,
        reasons = listOf("UI integration"),
        disabledSettings = emptyList()
    )

    @Test
    fun selectedGameApprovalAndRollbackUseRealStoreAndSaveBoundary() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val game = "com.example.profile.ui.${java.util.UUID.randomUUID()}"
        val store = AiProfileProposalStore(context)
        var active = PerformanceProfile.BALANCED
        var refreshes = 0
        val persist: suspend (String, GameProfileConfig) -> Unit = { name, config ->
            assertEquals(game, name)
            active = config.performanceProfile
        }

        val applied = scheduleAiProfileApply(
            scope(), game, fixture(3, PerformanceProfile.X4), store, persist
        ) { refreshes++ }
        applied!!.join()
        assertEquals(PerformanceProfile.X4, active)
        assertEquals(1, refreshes)
        assertEquals(3, store.rollbackState(game)?.version)

        val rolledBack = scheduleAiProfileRollback(
            scope(), game, store.rollbackState(game), AiProfileProposalStore(context), persist
        ) { refreshes++ }
        rolledBack!!.join()
        assertEquals(PerformanceProfile.BALANCED, active)
        assertEquals(2, refreshes)
        assertNull(store.rollbackState(game))
    }

    @Test
    fun callbacksRejectMissingSelectionAndStaleVersionWithoutWritingProfile() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val game = "com.example.profile.stale.${java.util.UUID.randomUUID()}"
        val store = AiProfileProposalStore(context)
        val approved = fixture(5, PerformanceProfile.X4)
        store.recordApplied(game, approved)
        var writes = 0
        var refreshes = 0
        val persist: suspend (String, GameProfileConfig) -> Unit = { _, _ -> writes++ }

        assertNull(scheduleAiProfileApply(scope(), null, approved, store, persist) { refreshes++ })
        assertNull(scheduleAiProfileApply(scope(), game, null, store, persist) { refreshes++ })
        assertNull(scheduleAiProfileRollback(scope(), game, null, store, persist) { refreshes++ })
        assertNull(scheduleAiProfileRollback(scope(), " ", store.rollbackState(game), store, persist) { refreshes++ })

        val stale = scheduleAiProfileApply(
            scope(), game, fixture(4, PerformanceProfile.BALANCED), store, persist
        ) { refreshes++ }
        stale!!.join()
        assertEquals(0, writes)
        assertEquals(0, refreshes)

        val outdated = scheduleAiProfileRollback(
            scope(), game, store.rollbackState(game)?.copy(version = 4), store, persist
        ) { refreshes++ }
        outdated!!.join()
        assertEquals(0, writes)
        assertEquals(0, refreshes)
        assertEquals(5, store.rollbackState(game)?.version)
    }
}

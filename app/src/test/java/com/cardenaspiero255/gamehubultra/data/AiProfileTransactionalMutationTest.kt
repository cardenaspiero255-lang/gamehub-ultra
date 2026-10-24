package com.cardenaspiero255.gamehubultra.data

import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

/** Reproduces #152:41 with actual persistence-facing transaction boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiProfileTransactionalMutationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val game = "com.example.txn"

    @Before
    fun reset() {
        context.getSharedPreferences("gamehub_ultra_ai_profile_builder", 0).edit().clear().commit()
    }

    private fun proposal(version: Int, target: PerformanceProfile) = AiProfileProposal(
        version = version,
        previousKnownGoodConfig = GameProfileConfig(performanceProfile = PerformanceProfile.BALANCED),
        proposedConfig = GameProfileConfig(performanceProfile = target),
        requiresExplicitApply = true,
        reasons = listOf("transaction regression"),
        disabledSettings = emptyList()
    )

    @Test
    fun staleProposalCannotWriteActiveProfileBeforeItsVersionIsRejected() = runBlocking {
        AiProfileProposalStore(context).recordApplied(game, proposal(5, PerformanceProfile.X4))
        var active = PerformanceProfile.X4
        var writes = 0
        val accepted = AiProfileProposalStore(context).applyProposal(
            game, proposal(4, PerformanceProfile.BALANCED)
        ) { _, config ->
            writes++
            active = config.performanceProfile
        }
        assertFalse(accepted)
        assertEquals(0, writes)
        assertEquals(PerformanceProfile.X4, active)
        assertEquals(5, AiProfileProposalStore(context).rollbackState(game)?.version)
    }

    @Test
    fun rejectedOldRollbackCannotClearNewerCheckpoint() {
        val original = AiProfileProposalStore(context)
        original.recordApplied(game, proposal(4, PerformanceProfile.X4))
        AiProfileProposalStore(context).recordApplied(game, proposal(5, PerformanceProfile.BALANCED))

        assertFalse(original.clearRollbackIfCurrent(game, expectedVersion = 4))
        assertEquals(5, AiProfileProposalStore(context).rollbackState(game)?.version)
        assertTrue(original.clearRollbackIfCurrent(game, expectedVersion = 5))
        assertNull(AiProfileProposalStore(context).rollbackState(game))
    }

    @Test
    fun rollbackQueuedBehindNewerApplyCannotUndoNewVersion() = runBlocking {
        val original = AiProfileProposalStore(context)
        original.recordApplied(game, proposal(4, PerformanceProfile.BALANCED))
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        var active = PerformanceProfile.BALANCED

        val newApply = async(Dispatchers.Default) {
            AiProfileProposalStore(context).applyProposal(
                game, proposal(5, PerformanceProfile.X4)
            ) { _, config ->
                saveStarted.complete(Unit)
                releaseSave.await()
                active = config.performanceProfile
            }
        }
        saveStarted.await()
        val oldRollback = async(Dispatchers.Default) {
            AiProfileProposalStore(context).rollbackIfCurrent(game, 4) { _, config ->
                active = config.performanceProfile
            }
        }
        releaseSave.complete(Unit)
        assertTrue(newApply.await())
        assertFalse(oldRollback.await())
        assertEquals(PerformanceProfile.X4, active)
        assertEquals(5, AiProfileProposalStore(context).rollbackState(game)?.version)
    }

    @Test
    fun failedProfileSaveNeverAdvancesVersionOrRollback() = runBlocking {
        val store = AiProfileProposalStore(context)
        val err = runCatching {
            store.applyProposal(game, proposal(2, PerformanceProfile.X4)) { _, _ ->
                error("simulated DataStore failure")
            }
        }.exceptionOrNull()
        assertTrue(err is IllegalStateException)
        assertNull(AiProfileProposalStore(context).rollbackState(game))
        assertEquals(1, store.nextVersion(game))
    }
}

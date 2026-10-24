package com.cardenaspiero255.gamehubultra.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
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
        // Execute immediately until the shared mutex suspends; this proves
        // rollback was attempted during the in-flight version-5 save.
        val oldRollback = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            AiProfileProposalStore(context).rollbackIfCurrent(game, 4) { _, config ->
                active = config.performanceProfile
            }
        }
        assertFalse(oldRollback.isCompleted)
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
    @Test
    fun successfulApprovalAndMatchingRollbackPersistBothTransitions() = runBlocking {
        val firstStore = AiProfileProposalStore(context)
        var active = PerformanceProfile.BALANCED
        val saved = mutableListOf<PerformanceProfile>()
        val save: suspend (String, GameProfileConfig) -> Unit = { packageName, config ->
            assertEquals(game, packageName)
            active = config.performanceProfile
            saved += active
        }

        assertTrue(firstStore.applyProposal(game, proposal(2, PerformanceProfile.X4), save))
        assertEquals(PerformanceProfile.X4, active)
        assertEquals(2, AiProfileProposalStore(context).rollbackState(game)?.version)
        assertEquals(3, firstStore.nextVersion(game))

        val secondStore = AiProfileProposalStore(context)
        assertTrue(secondStore.rollbackIfCurrent(game, 2, save))
        assertEquals(PerformanceProfile.BALANCED, active)
        assertNull(firstStore.rollbackState(game))
        assertEquals(3, firstStore.nextVersion(game))
        assertEquals(listOf(PerformanceProfile.X4, PerformanceProfile.BALANCED), saved)
    }

    @Test
    fun sameVersionReplayAfterExplicitRollbackNeverWritesActiveProfile() = runBlocking {
        val store = AiProfileProposalStore(context)
        assertTrue(store.applyProposal(game, proposal(4, PerformanceProfile.X4)) { _, _ -> })
        assertTrue(store.rollbackIfCurrent(game, 4) { _, _ -> })
        var unexpectedWrites = 0
        assertFalse(AiProfileProposalStore(context).applyProposal(
            game, proposal(4, PerformanceProfile.BALANCED)
        ) { _, _ -> unexpectedWrites++ })
        assertEquals(0, unexpectedWrites)
        assertNull(store.rollbackState(game))
        assertEquals(5, store.nextVersion(game))
    }

    @Test
    fun rollbackWithoutMatchingCheckpointNeverCallsPersistence() = runBlocking {
        val store = AiProfileProposalStore(context)
        assertFalse(store.rollbackIfCurrent(game, 0) { _, _ -> error("must not write") })
        assertFalse(store.rollbackIfCurrent(" ", 5) { _, _ -> error("must not write") })
        assertFalse(store.rollbackIfCurrent(game, 5) { _, _ -> error("must not write") })
        store.recordApplied(game, proposal(6, PerformanceProfile.X4))
        assertFalse(store.rollbackIfCurrent(game, 5) { _, _ -> error("must not write") })
        assertEquals(6, store.rollbackState(game)?.version)
    }

    @Test
    fun failedRollbackWritePreservesDurableCheckpointForRetry() = runBlocking {
        val store = AiProfileProposalStore(context)
        store.recordApplied(game, proposal(3, PerformanceProfile.X4))
        val failed = runCatching {
            store.rollbackIfCurrent(game, 3) { _, _ -> error("DataStore could not persist rollback") }
        }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
        assertEquals(3, store.rollbackState(game)?.version)
        assertTrue(AiProfileProposalStore(context).rollbackIfCurrent(game, 3) { _, _ -> })
        assertNull(store.rollbackState(game))
    }

    @Test
    fun failedCheckpointCommitCompensatesActiveProfileAndLeavesNoRollback() = runBlocking {
        val backing = context.getSharedPreferences("gamehub_ultra_ai_profile_builder", 0)
        val broken = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                object : SharedPreferences by backing {
                    override fun edit(): SharedPreferences.Editor {
                        val original = backing.edit()
                        return object : SharedPreferences.Editor by original {
                            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                                original.putInt(key, value)
                                return this
                            }
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                original.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
        }
        var active = PerformanceProfile.BALANCED
        val writes = mutableListOf<PerformanceProfile>()
        val failure = runCatching {
            AiProfileProposalStore(broken).applyProposal(
                game, proposal(2, PerformanceProfile.X4)
            ) { _, config ->
                active = config.performanceProfile
                writes += active
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(PerformanceProfile.X4, PerformanceProfile.BALANCED), writes)
        assertEquals(PerformanceProfile.BALANCED, active)
        assertNull(AiProfileProposalStore(context).rollbackState(game))
        assertEquals(1, AiProfileProposalStore(context).nextVersion(game))
    }

    @Test
    fun failedCompensationRetainsOriginalCheckpointErrorWithSuppressedCause() = runBlocking {
        val backing = context.getSharedPreferences("gamehub_ultra_ai_profile_builder", 0)
        val broken = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                object : SharedPreferences by backing {
                    override fun edit(): SharedPreferences.Editor {
                        val original = backing.edit()
                        return object : SharedPreferences.Editor by original {
                            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                                original.putInt(key, value)
                                return this
                            }
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                original.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean = false
                        }
                    }
                }
        }
        var writes = 0
        val failed = runCatching {
            AiProfileProposalStore(broken).applyProposal(
                game, proposal(2, PerformanceProfile.X4)
            ) { _, _ ->
                writes++
                if (writes == 2) error("compensating DataStore write also failed")
            }
        }.exceptionOrNull()
        assertTrue(failed is IllegalStateException)
        assertTrue(failed.message.orEmpty().contains("checkpoint"))
        assertEquals(1, failed.suppressed.size)
        assertTrue(failed.suppressed.single().message.orEmpty().contains("compensating"))
        assertEquals(2, writes)
        assertNull(AiProfileProposalStore(context).rollbackState(game))
    }

    @Test
    fun invalidProposalIsRejectedWithoutWritingProfile() = runBlocking {
        val store = AiProfileProposalStore(context)
        val invalidPackage = runCatching {
            store.applyProposal(" ", proposal(1, PerformanceProfile.X4)) { _, _ ->
                error("save must not occur")
            }
        }.exceptionOrNull()
        assertTrue(invalidPackage is IllegalArgumentException)
        val invalidVersion = runCatching {
            store.applyProposal(game, proposal(0, PerformanceProfile.X4)) { _, _ ->
                error("save must not occur")
            }
        }.exceptionOrNull()
        assertTrue(invalidVersion is IllegalArgumentException)
        assertNull(store.rollbackState(game))
    }

}

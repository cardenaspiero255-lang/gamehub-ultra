package com.cardenaspiero255.gamehubultra

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.AiProfileProposalStore
import com.cardenaspiero255.gamehubultra.data.PerGameAdaptiveStatePreferencesStore
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerGameAdaptiveOptimizer
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * CAR-29 / Ultra Sentinel #153:71 × #152:41
 *
 * Android E2E persistence journey: user-approved proposal -> real SessionCoach
 * samples -> adaptive processing / injected event-storage failure -> rollback ->
 * real Android optimizer recreation -> replay -> late stale proposal -> user rollback
 * -> MainActivity recreation. The E2E uses production repositories, persistence and
 * coordinator code, rather than mock JVM stores. No real game or network required.
 *
 * Scope: API 35 emulator. Does not certify a Vivo device or the visual UI buttons.
 */
@RunWith(AndroidJUnit4::class)
class GameHubRollbackPersistenceE2ETest {
    @Test
    fun sessionReplayAndStaleProposalNeverCorruptRollbackAfterAppRestart() = runBlocking {
        val fingerprint = Build.FINGERPRINT.lowercase()
        assumeTrue(
            "Only execute destructive session-storage test on an isolated emulator",
            fingerprint.contains("generic") ||
                fingerprint.contains("emulator") ||
                Build.MODEL.lowercase().contains("sdk")
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val packageName = "com.example.gamehub.rollback.$suffix"
        val sessionId = "car29-rollback-$suffix"
        val gameVersion = "unknown"
        val selection = GameHubProductionComposition.selectionRepository(context)
        val history = com.cardenaspiero255.gamehubultra.data.GameHubPreferencesRepository(context)
        val proposals = AiProfileProposalStore(context)
        val coach = SessionCoachSessionStore(context)
        val adaptiveState = PerGameAdaptiveStatePreferencesStore(context)

        selection.saveSelectedGame(packageName)
        selection.saveGameProfileConfig(
            packageName, GameProfileConfig(performanceProfile = PerformanceProfile.BALANCED)
        )
        val approved = AiProfileProposal(
            version = 4,
            previousKnownGoodConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.BALANCED
            ),
            proposedConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.X4
            ),
            requiresExplicitApply = true,
            reasons = listOf("E2E approved"),
            disabledSettings = emptyList()
        )
        var applyCount = 0
        assertTrue(
            applyAiProfileProposalForSelectedGame(
                packageName = packageName,
                proposal = approved,
                save = { game, config, onSaved ->
                    runBlocking { selection.saveGameProfileConfig(game, config) }
                    onSaved()
                },
                recordApplied = proposals::recordApplied,
                onApplied = { applyCount++ }
            )
        )
        assertEquals(1, applyCount)
        assertEquals(PerformanceProfile.X4, selection.profileForGameFlow(packageName).first())
        assertEquals(4, proposals.rollbackState(packageName)?.version)

        assertTrue(coach.beginSession(sessionId, packageName, 1_000L, gameVersion))
        listOf(65, 78, 95).forEachIndexed { index, memory ->
            val timestamp = 1_000L + index * 2_000L
            coach.appendSnapshot(
                sessionId,
                SessionCoachSnapshot(
                    timestampMillis = timestamp,
                    batteryPercent = 80,
                    thermalStatus = 1,
                    thermalHeadroom = 0.2f,
                    refreshRateHz = 120f,
                    latencyMs = 30L,
                    memoryUsedPercent = memory
                )
            )
        }
        assertNotNull(coach.finishActiveSession(6_000L, expectedSessionId = sessionId))
        val completed = checkNotNull(SessionCoachSessionStore(context).readLastCompletedSession())
        assertEquals(sessionId, completed.sessionId)
        assertEquals(3, completed.samples.size)

        // Inject one failure at the same production event-persistence boundary the app uses.
        // No synthetic optimizer state or fake SharedPreferences repository is used.
        var failedAsExpected = false
        try {
            processCompletedAdaptiveSession(
                completed = completed,
                optimizer = PerGameAdaptiveOptimizer(
                    confirmationsRequired = 1, cooldownMillis = 0, stateStore = adaptiveState
                ),
                nowMillis = 7_000L,
                wasSessionHandled = { adaptiveState.wasSessionHandled(sessionId) },
                resolveActiveProfile = { selection.profileForGameFlow(packageName).first()!! },
                persistProfile = selection::saveAdaptiveProfileForGame,
                markSessionHandled = { adaptiveState.markSessionHandled(sessionId) },
                recordPerformanceEvent = { throw IllegalStateException("E2E deliberate event write failure") }
            )
        } catch (expected: IllegalStateException) {
            failedAsExpected = true
        }
        assertTrue("Must reproduce the error before the retry", failedAsExpected)
        assertFalse(adaptiveState.wasSessionHandled(sessionId))
        assertEquals(PerformanceProfile.X4, selection.profileForGameFlow(packageName).first())
        assertNotNull(adaptiveState.readPendingDecision(sessionId))

        // Simulate a new GameHub process: recreate both SharedPreferences adapters and optimizer.
        val reopened = PerGameAdaptiveStatePreferencesStore(context)
        val replayed = processCompletedAdaptiveSession(
            completed = SessionCoachSessionStore(context).readLastCompletedSession(),
            optimizer = PerGameAdaptiveOptimizer(
                confirmationsRequired = 1, cooldownMillis = 0, stateStore = reopened
            ),
            nowMillis = 8_000L,
            wasSessionHandled = { reopened.wasSessionHandled(sessionId) },
            resolveActiveProfile = { selection.profileForGameFlow(packageName).first()!! },
            persistProfile = selection::saveAdaptiveProfileForGame,
            markSessionHandled = { reopened.markSessionHandled(sessionId) },
            recordPerformanceEvent = history::appendPerformanceEvent
        )
        assertNotNull(replayed)
        assertTrue(checkNotNull(replayed).changed)
        assertEquals(PerformanceProfile.BALANCED, replayed.profile)
        assertTrue(reopened.wasSessionHandled(sessionId))
        assertNull(reopened.readPendingDecision(sessionId))
        assertEquals(PerformanceProfile.BALANCED, selection.profileForGameFlow(packageName).first())
        assertEquals(
            1, history.performanceHistoryFlow(50).first().count { it.sessionId == sessionId }
        )

        // #152:41 — an older asynchronous proposal must never replace the approved
        // newer version or its known-good rollback destination.
        AiProfileProposalStore(context).recordApplied(
            packageName,
            approved.copy(
                version = 3,
                previousKnownGoodConfig = GameProfileConfig(
                    performanceProfile = PerformanceProfile.X4
                )
            )
        )
        val stable = AiProfileProposalStore(context)
        assertEquals(4, stable.rollbackState(packageName)?.version)
        assertEquals(
            PerformanceProfile.BALANCED,
            stable.rollbackState(packageName)?.previousKnownGoodConfig?.performanceProfile
        )
        assertEquals(5, stable.nextVersion(packageName))

        var rollbackCount = 0
        assertTrue(
            rollbackAiProfileProposalForSelectedGame(
                packageName = packageName,
                rollback = stable.rollbackState(packageName),
                save = { game, config, onSaved ->
                    runBlocking { selection.saveGameProfileConfig(game, config) }
                    onSaved()
                },
                clearRollback = stable::clearRollback,
                onRolledBack = { rollbackCount++ }
            )
        )
        assertEquals(1, rollbackCount)
        assertNull(AiProfileProposalStore(context).rollbackState(packageName))
        assertEquals(PerformanceProfile.BALANCED, selection.profileForGameFlow(packageName).first())

        // Reentering the completed session may not emit a duplicate policy change.
        val duplicate = processCompletedAdaptiveSession(
            completed = completed,
            optimizer = PerGameAdaptiveOptimizer(
                confirmationsRequired = 1, cooldownMillis = 0,
                stateStore = PerGameAdaptiveStatePreferencesStore(context)
            ),
            nowMillis = 9_000L,
            wasSessionHandled = { reopened.wasSessionHandled(sessionId) },
            resolveActiveProfile = { selection.profileForGameFlow(packageName).first()!! },
            persistProfile = selection::saveAdaptiveProfileForGame,
            markSessionHandled = { reopened.markSessionHandled(sessionId) },
            recordPerformanceEvent = history::appendPerformanceEvent
        )
        assertNull(duplicate)
        assertEquals(
            1, history.performanceHistoryFlow(50).first().count { it.sessionId == sessionId }
        )

        // Boot the real app on the emulator with the persisted checkpoint present.
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            assertEquals(
                PerformanceProfile.BALANCED,
                selection.profileForGameFlow(packageName).first()
            )
            assertTrue(PerGameAdaptiveStatePreferencesStore(context).wasSessionHandled(sessionId))
        }
    }
}

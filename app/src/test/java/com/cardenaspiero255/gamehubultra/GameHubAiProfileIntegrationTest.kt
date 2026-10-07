package com.cardenaspiero255.gamehubultra

import com.cardenaspiero255.gamehubultra.data.AppliedAiProfileProposalState
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal
import com.cardenaspiero255.gamehubultra.domain.GameProfileConfig
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameHubAiProfileIntegrationTest {
    @Test
    fun proposalWaitsForSelectedGameAndRuntimeRefreshCapabilities() {
        var versionCalls = 0
        val nextVersion: (String) -> Int = {
            versionCalls += 1
            1
        }

        assertNull(
            buildAiProfileProposalForSelectedGame(
                packageName = null,
                selectedConfig = null,
                effectiveProfile = PerformanceProfile.BALANCED,
                observations = emptyList(),
                sessionSamples = emptyList(),
                supportedRefreshRatesHz = setOf(60, 120),
                supportsSustainedPerformance = true,
                nextVersion = nextVersion
            )
        )
        assertNull(
            buildAiProfileProposalForSelectedGame(
                packageName = "game.example",
                selectedConfig = null,
                effectiveProfile = PerformanceProfile.BALANCED,
                observations = emptyList(),
                sessionSamples = emptyList(),
                supportedRefreshRatesHz = null,
                supportsSustainedPerformance = true,
                nextVersion = nextVersion
            )
        )

        assertEquals(0, versionCalls)
    }

    @Test
    fun unsupportedX4ProducesExplicitVersionedProposalFromEffectiveProfile() {
        val proposal = buildAiProfileProposalForSelectedGame(
            packageName = "game.example",
            selectedConfig = null,
            effectiveProfile = PerformanceProfile.X4,
            observations = emptyList(),
            sessionSamples = emptyList(),
            supportedRefreshRatesHz = emptySet(),
            supportsSustainedPerformance = false,
            nextVersion = { 4 }
        )

        assertNotNull(proposal)
        assertEquals(4, proposal.version)
        assertEquals(
            PerformanceProfile.X4,
            proposal.previousKnownGoodConfig.performanceProfile
        )
        assertEquals(
            PerformanceProfile.BALANCED,
            proposal.proposedConfig.performanceProfile
        )
        assertTrue(proposal.requiresExplicitApply)
    }

    @Test
    fun unchangedBalancedProfileDoesNotSurfaceAProposal() {
        assertNull(
            buildAiProfileProposalForSelectedGame(
                packageName = "game.example",
                selectedConfig = GameProfileConfig(
                    performanceProfile = PerformanceProfile.BALANCED
                ),
                effectiveProfile = PerformanceProfile.X4,
                observations = emptyList(),
                sessionSamples = emptyList(),
                supportedRefreshRatesHz = emptySet(),
                supportsSustainedPerformance = true,
                nextVersion = { 2 }
            )
        )
    }

    @Test
    fun applyingProposalPersistsOnlyAfterSaveCompletes() {
        val previous = GameProfileConfig(performanceProfile = PerformanceProfile.X4)
        val proposed = GameProfileConfig(performanceProfile = PerformanceProfile.BALANCED)
        val proposal = AiProfileProposal(
            version = 3,
            previousKnownGoodConfig = previous,
            proposedConfig = proposed,
            requiresExplicitApply = true,
            reasons = listOf("thermal"),
            disabledSettings = emptyList()
        )
        val events = mutableListOf<String>()

        val accepted = applyAiProfileProposalForSelectedGame(
            packageName = "game.example",
            proposal = proposal,
            save = { packageName, config, onSaved ->
                assertEquals("game.example", packageName)
                assertEquals(proposed, config)
                events += "save"
                onSaved()
            },
            recordApplied = { packageName, recorded ->
                assertEquals("game.example", packageName)
                assertEquals(proposal, recorded)
                events += "record"
            },
            onApplied = { events += "revision" }
        )

        assertTrue(accepted)
        assertEquals(listOf("save", "record", "revision"), events)
    }

    @Test
    fun applyNoOpsWithoutGameOrProposal() {
        var called = false
        val save: (String, GameProfileConfig, () -> Unit) -> Unit = { _, _, _ ->
            called = true
        }

        assertFalse(
            applyAiProfileProposalForSelectedGame(
                packageName = null,
                proposal = null,
                save = save,
                recordApplied = { _, _ -> called = true },
                onApplied = { called = true }
            )
        )
        assertFalse(called)
    }

    @Test
    fun rollbackRestoresKnownGoodConfigThenClearsRollback() {
        val previous = GameProfileConfig(performanceProfile = PerformanceProfile.X4)
        val rollback = AppliedAiProfileProposalState(
            version = 8,
            previousKnownGoodConfig = previous
        )
        val events = mutableListOf<String>()

        val accepted = rollbackAiProfileProposalForSelectedGame(
            packageName = "game.example",
            rollback = rollback,
            save = { packageName, config, onSaved ->
                assertEquals("game.example", packageName)
                assertEquals(previous, config)
                events += "save"
                onSaved()
            },
            clearRollback = {
                assertEquals("game.example", it)
                events += "clear"
            },
            onRolledBack = { events += "revision" }
        )

        assertTrue(accepted)
        assertEquals(listOf("save", "clear", "revision"), events)
    }
}

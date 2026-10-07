package com.cardenaspiero255.gamehubultra.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AiProfileBuilderTest {

    @Test
    fun thermalFailuresDowngradeAggressiveProfileAndRequireExplicitApply() {
        val current = GameProfileConfig(
            performanceProfile = PerformanceProfile.X4,
            thermalPreference = ThermalPreference.PERFORMANCE,
            refreshRateTargetHz = 120
        )
        val observations = listOf(
            observation(PerformanceProfile.X4, failed = true, highTemperature = true, thermalStatus = 4, at = 1L),
            observation(PerformanceProfile.X4, failed = true, highTemperature = true, thermalStatus = 4, at = 2L),
            observation(PerformanceProfile.X4, failed = true, highTemperature = true, thermalStatus = 5, at = 3L)
        )

        val proposal = AiProfileBuilder.propose(
            currentConfig = current,
            observations = observations,
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportsSustainedPerformance = true,
                supportsFrameInterpolation = false,
                supportedRefreshRatesHz = setOf(60, 90, 120)
            ),
            version = 7
        )

        assertEquals(7, proposal.version)
        assertEquals(current, proposal.previousKnownGoodConfig)
        assertEquals(PerformanceProfile.BALANCED, proposal.proposedConfig.performanceProfile)
        assertEquals(ThermalPreference.COOLER, proposal.proposedConfig.thermalPreference)
        assertTrue(proposal.requiresExplicitApply)
        assertTrue(proposal.reasons.any { it.contains("térmic", ignoreCase = true) })
    }

    @Test
    fun unsupportedPerformanceIntentIsDisabledAndExplained() {
        val current = GameProfileConfig(performanceProfile = PerformanceProfile.X4)

        val proposal = AiProfileBuilder.propose(
            currentConfig = current,
            observations = listOf(
                observation(
                    profile = PerformanceProfile.X4,
                    stable = true,
                    decision = OptimizationFeedbackDecision.ACCEPTED,
                    at = 1L
                )
            ),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportsSustainedPerformance = false,
                supportsFrameInterpolation = false
            ),
            version = 2
        )

        assertEquals(PerformanceProfile.BALANCED, proposal.proposedConfig.performanceProfile)
        assertTrue(proposal.disabledSettings.any { it.contains("X4", ignoreCase = true) })
        assertTrue(proposal.requiresExplicitApply)
    }

    @Test
    fun unsupportedRefreshTargetIsRemovedInsteadOfPretendingItCanBeApplied() {
        val current = GameProfileConfig(
            performanceProfile = PerformanceProfile.BALANCED,
            refreshRateTargetHz = 144
        )

        val proposal = AiProfileBuilder.propose(
            currentConfig = current,
            observations = emptyList(),
            sessionSamples = listOf(
                SessionCoachSnapshot(
                    timestampMillis = 1L,
                    batteryPercent = 80,
                    thermalStatus = 1,
                    thermalHeadroom = 0.20f,
                    refreshRateHz = 144f,
                    latencyMs = 25L
                )
            ),
            capabilities = AiProfileCapabilities(
                supportsSustainedPerformance = true,
                supportsFrameInterpolation = false,
                supportedRefreshRatesHz = setOf(60, 90, 120)
            ),
            version = 3
        )

        assertEquals(null, proposal.proposedConfig.refreshRateTargetHz)
        assertTrue(proposal.disabledSettings.any { it.contains("144") })
        assertTrue(proposal.requiresExplicitApply)
    }

    @Test
    fun emptyDisplayCapabilitiesAreUnverifiedAndDisableTargets() {
        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(
                refreshRateTargetHz = 120,
                resolutionTarget = ResolutionTarget(1920, 1080)
            ),
            observations = emptyList(),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportedRefreshRatesHz = emptySet(),
                supportedResolutions = emptySet()
            ),
            version = 9
        )

        assertEquals(null, proposal.proposedConfig.refreshRateTargetHz)
        assertEquals(null, proposal.proposedConfig.resolutionTarget)
        assertTrue(proposal.disabledSettings.any { it.contains("120") })
        assertTrue(proposal.disabledSettings.any { it.contains("1920x1080") })
        assertTrue(proposal.requiresExplicitApply)
    }

    @Test
    fun unchangedKnownGoodProfileDoesNotDemandAnApplyAction() {
        val current = GameProfileConfig(
            performanceProfile = PerformanceProfile.BALANCED,
            thermalPreference = ThermalPreference.ADAPTIVE
        )

        val proposal = AiProfileBuilder.propose(
            currentConfig = current,
            observations = listOf(
                observation(
                    profile = PerformanceProfile.BALANCED,
                    stable = true,
                    decision = OptimizationFeedbackDecision.ACCEPTED,
                    at = 10L
                )
            ),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(),
            version = 1
        )

        assertEquals(current, proposal.proposedConfig)
        assertFalse(proposal.requiresExplicitApply)
        assertTrue(proposal.disabledSettings.isEmpty())
    }

    @Test
    fun unsupportedFrameInterpolationIsDisabledAndExplained() {
        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.FRAME_INTERPOLATION
            ),
            observations = emptyList(),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportsFrameInterpolation = false
            ),
            version = 4
        )

        assertEquals(
            PerformanceProfile.BALANCED,
            proposal.proposedConfig.performanceProfile
        )
        assertTrue(
            proposal.disabledSettings.any {
                it.contains("Interpolación", ignoreCase = true)
            }
        )
    }

    @Test
    fun unsupportedResolutionIsRemovedAndExplained() {
        val requested = ResolutionTarget(2560, 1440)
        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(
                resolutionTarget = requested
            ),
            observations = emptyList(),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportedResolutions = setOf(
                    ResolutionTarget(1920, 1080)
                )
            ),
            version = 5
        )

        assertEquals(null, proposal.proposedConfig.resolutionTarget)
        assertTrue(
            proposal.disabledSettings.any {
                it.contains("2560x1440")
            }
        )
    }

    @Test
    fun acceptedStableHistoryCanProposeACompatibleProfile() {
        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.BALANCED
            ),
            observations = listOf(
                observation(
                    profile = PerformanceProfile.X4,
                    stable = true,
                    decision = OptimizationFeedbackDecision.ACCEPTED,
                    at = 1L
                ),
                observation(
                    profile = PerformanceProfile.X4,
                    stable = true,
                    decision = OptimizationFeedbackDecision.ACCEPTED,
                    at = 2L
                )
            ),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportsSustainedPerformance = true
            ),
            version = 6
        )

        assertEquals(
            PerformanceProfile.X4,
            proposal.proposedConfig.performanceProfile
        )
        assertTrue(proposal.requiresExplicitApply)
        assertTrue(
            proposal.reasons.any {
                it.contains("observaciones", ignoreCase = true)
            }
        )
    }

    @Test
    fun rejectedAndRevertedHistoryDoNotPromoteAggressiveProfile() {
        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(
                performanceProfile = PerformanceProfile.BALANCED
            ),
            observations = listOf(
                observation(
                    profile = PerformanceProfile.X4,
                    stable = true,
                    decision = OptimizationFeedbackDecision.REJECTED,
                    at = 1L
                ),
                observation(
                    profile = PerformanceProfile.X4,
                    stable = true,
                    decision = OptimizationFeedbackDecision.REVERTED,
                    at = 2L
                )
            ),
            sessionSamples = emptyList(),
            capabilities = AiProfileCapabilities(
                supportsSustainedPerformance = true
            ),
            version = 10
        )

        assertEquals(
            PerformanceProfile.BALANCED,
            proposal.proposedConfig.performanceProfile
        )
        assertFalse(proposal.requiresExplicitApply)
    }

    @Test
    fun stableMeasuredRefreshCanBeProposedWhenVerifiedByCapabilities() {
        val samples = listOf(1L, 2L, 3L).map { timestamp ->
            SessionCoachSnapshot(
                timestampMillis = timestamp,
                batteryPercent = 80,
                thermalStatus = 1,
                thermalHeadroom = 0.20f,
                refreshRateHz = 120f,
                latencyMs = 20L
            )
        }

        val proposal = AiProfileBuilder.propose(
            currentConfig = GameProfileConfig(),
            observations = emptyList(),
            sessionSamples = samples,
            capabilities = AiProfileCapabilities(
                supportedRefreshRatesHz = setOf(60, 90, 120)
            ),
            version = 8
        )

        assertEquals(120, proposal.proposedConfig.refreshRateTargetHz)
        assertTrue(proposal.requiresExplicitApply)
        assertTrue(
            proposal.reasons.any {
                it.contains("120 Hz")
            }
        )
    }

    @Test
    fun proposalVersionMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            AiProfileBuilder.propose(
                currentConfig = GameProfileConfig(),
                observations = emptyList(),
                sessionSamples = emptyList(),
                capabilities = AiProfileCapabilities(),
                version = 0
            )
        }
    }

    private fun observation(
        profile: PerformanceProfile,
        stable: Boolean = false,
        failed: Boolean = false,
        highTemperature: Boolean = false,
        thermalStatus: Int? = null,
        decision: OptimizationFeedbackDecision = OptimizationFeedbackDecision.NONE,
        at: Long
    ) = OptimizationObservation(
        contextKey = "device¦game¦1¦¦",
        profile = profile,
        stable = stable,
        failed = failed,
        highTemperature = highTemperature,
        thermalStatus = thermalStatus,
        timestampMillis = at,
        feedbackDecision = decision
    )
}

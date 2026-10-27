package com.cardenaspiero255.gamehubultra.platform.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DriverCompatibilityPolicyTest {
    private val game = GameDriverPreference("com.sample.game", "turnip-v1")
    private val verified = VerifiedAlternateDriver(
        driverId = "turnip-v1",
        allowedPackages = setOf("com.sample.game"),
        installed = true,
        selectionBackendVerified = true,
        prelaunchSelectionConfirmed = true
    )

    private fun assertSystem(result: DriverLaunchDecision, reason: DriverFallbackReason) {
        assertEquals(DriverLaunchRoute.SYSTEM, result.route)
        assertEquals(reason, result.reason)
        assertNull(result.driverId)
        assertFalse(result.activeDriverChanged)
    }

    @Test fun unverifiedGpuDriverSelectionAlwaysUsesSystem() {
        assertSystem(DriverCompatibilityPolicy.plan(game, false, listOf(verified)),
            DriverFallbackReason.SELECTION_UNVERIFIED)
    }

    @Test fun missingAndUninstalledDriversUseSystemFallback() {
        assertSystem(DriverCompatibilityPolicy.plan(game, true, emptyList()),
            DriverFallbackReason.DRIVER_UNAVAILABLE)
        assertSystem(DriverCompatibilityPolicy.plan(game, true, listOf(verified.copy(installed = false))),
            DriverFallbackReason.DRIVER_UNAVAILABLE)
    }

    @Test fun driversNotVerifiedForThisExactGameAreNeverSelected() {
        assertSystem(DriverCompatibilityPolicy.plan(game, true,
            listOf(verified.copy(allowedPackages = setOf("com.other.game")))),
            DriverFallbackReason.GAME_UNSUPPORTED)
    }

    @Test fun backendAndPrelaunchSelectionMustBothBeVerified() {
        assertSystem(DriverCompatibilityPolicy.plan(game, true,
            listOf(verified.copy(selectionBackendVerified = false))),
            DriverFallbackReason.SELECTION_UNVERIFIED)
        assertSystem(DriverCompatibilityPolicy.plan(game, true,
            listOf(verified.copy(prelaunchSelectionConfirmed = false))),
            DriverFallbackReason.SELECTION_UNVERIFIED)
    }

    @Test fun malformedPreferencesNeverBecomeDriverCommands() {
        assertSystem(DriverCompatibilityPolicy.plan(GameDriverPreference("invalid package", "turnip-v1"),
            true, listOf(verified)), DriverFallbackReason.INVALID_PACKAGE)
        assertSystem(DriverCompatibilityPolicy.plan(GameDriverPreference("com.sample.game", "../../turnip"),
            true, listOf(verified)), DriverFallbackReason.INVALID_DRIVER_ID)
    }

    @Test fun systemPreferenceNeverActivatesAlternativeDrivers() {
        assertSystem(DriverCompatibilityPolicy.plan(GameDriverPreference("com.sample.game"),
            true, listOf(verified)), DriverFallbackReason.SYSTEM_PREFERENCE)
    }

    @Test fun ambiguousDuplicateDriverDescriptorsFailClosed() {
        assertSystem(DriverCompatibilityPolicy.plan(game, true, listOf(verified, verified)),
            DriverFallbackReason.AMBIGUOUS_DRIVER)
    }

    @Test fun verifiedAlternativeIsOnlyAnEligiblePlanNotAnExecutedSwitch() {
        val plan = DriverCompatibilityPolicy.plan(game, true, listOf(verified))
        assertEquals(DriverLaunchRoute.ALTERNATE_READY, plan.route)
        assertEquals(DriverFallbackReason.VERIFIED_ALTERNATE_READY, plan.reason)
        assertEquals("turnip-v1", plan.driverId)
        assertFalse(plan.activeDriverChanged)
    }
}

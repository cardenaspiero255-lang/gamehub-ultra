package com.cardenaspiero255.gamehubultra.platform.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsDriverPreferenceReadbackTest {
    @Test fun absentOverridesAreSystemDefault() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.SYSTEM, null, null
        )
        assertEquals(DriverPreferenceStatus.SYSTEM_DEFAULT, result.status)
        assertTrue(result.matchesPreference)
    }

    @Test fun alternatePreferenceRequiresActualSystemReadback() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.ANGLE, null, null
        )
        assertEquals(DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED, result.status)
        assertFalse(result.matchesPreference)
    }

    @Test fun exactPerPackageAngleSelectionIsConfirmedButNotRuntimeProof() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.ANGLE,
            "com.other.game,com.example.game", "native,angle"
        )
        assertEquals(DriverPreferenceStatus.SYSTEM_SETTING_CONFIRMED, result.status)
        assertTrue(result.matchesPreference)
        assertFalse(result.driverLoadedVerified)
    }

    @Test fun nativeSelectionMatchesOnlyTheSamePackage() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.NATIVE,
            "com.example.game,com.other.game", "native,angle"
        )
        assertTrue(result.matchesPreference)
        assertEquals(OpenGlDriverPreference.NATIVE, result.readback)
    }

    @Test fun malformedPairsAreNeverTrusted() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.ANGLE,
            "com.example.game,com.other.game", "angle"
        )
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE, result.status)
        assertFalse(result.matchesPreference)
    }

    @Test fun duplicateEntriesFailClosedEvenWhenOneMatches() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.ANGLE,
            "com.example.game,com.example.game", "angle,angle"
        )
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE, result.status)
    }

    @Test fun invalidPackagesAndUnknownDriverValuesFailClosed() {
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE,
            GraphicsDriverPreferenceReadback.inspect(
                "../../game", OpenGlDriverPreference.ANGLE, "com.example.game", "angle"
            ).status)
        assertEquals(DriverPreferenceStatus.UNVERIFIABLE,
            GraphicsDriverPreferenceReadback.inspect(
                "com.example.game", OpenGlDriverPreference.ANGLE, "com.example.game", "turnip"
            ).status)
    }

    @Test fun existingSystemOverrideMustNotBeCalledSystemFallback() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.SYSTEM,
            "com.example.game", "angle"
        )
        assertEquals(DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED, result.status)
        assertFalse(result.matchesPreference)
    }

    @Test fun mismatchedSavedAndActualDriverRemainsPending() {
        val result = GraphicsDriverPreferenceReadback.inspect(
            "com.example.game", OpenGlDriverPreference.NATIVE,
            "com.example.game", "angle"
        )
        assertEquals(DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED, result.status)
    }

    @Test fun onlyAllowListedModesPersist() {
        assertEquals(OpenGlDriverPreference.ANGLE,
            OpenGlDriverPreference.fromStoredValue("angle"))
        assertEquals(OpenGlDriverPreference.SYSTEM,
            OpenGlDriverPreference.fromStoredValue("turnip-v1"))
        assertEquals(OpenGlDriverPreference.SYSTEM,
            OpenGlDriverPreference.fromStoredValue(null))
    }
}

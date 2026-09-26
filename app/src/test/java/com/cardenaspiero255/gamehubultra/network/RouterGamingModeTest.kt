package com.cardenaspiero255.gamehubultra.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RouterGamingModeTest {

    @Test
    fun recognizesFiberHomeHg5853sfAsQosCapable() {
        val profile = RouterCapabilityCatalog.find(
            manufacturer = "FiberHome",
            model = "HG5853SF"
        )

        assertNotNull(profile)
        assertTrue(profile.hardwareQosCapable)
        assertTrue(profile.supportsUpnpDiscovery)
    }

    @Test
    fun modelMatchingIsCaseAndWhitespaceInsensitive() {
        val profile = RouterCapabilityCatalog.find(
            manufacturer = "fiberhome",
            model = "  hg5853sf  "
        )

        assertEquals("HG5853SF", profile?.model)
    }

    @Test
    fun compatibleRouterRemainsRestrictedWithoutAuthorizedControlChannel() {
        val result = RouterGamingModePolicy.evaluate(
            evidence = RouterDiscoveryEvidence(
                manufacturer = "FiberHome",
                model = "HG5853SF",
                upnpVisible = true,
                authorizedQosControlAvailable = false
            )
        )

        assertEquals(RouterGamingModeStatus.COMPATIBLE_RESTRICTED, result.status)
        assertTrue(result.hardwareQosCapable)
        assertFalse(result.canAutoConfigureQos)
    }

    @Test
    fun upnpVisibilityAloneNeverMeansQosCanBeChanged() {
        val result = RouterGamingModePolicy.evaluate(
            evidence = RouterDiscoveryEvidence(
                manufacturer = "FiberHome",
                model = "HG5853SF",
                upnpVisible = true,
                authorizedQosControlAvailable = false
            )
        )

        assertFalse(result.canAutoConfigureQos)
    }

    @Test
    fun fullRouterGamingModeRequiresAuthorizedWritableQosChannel() {
        val result = RouterGamingModePolicy.evaluate(
            evidence = RouterDiscoveryEvidence(
                manufacturer = "FiberHome",
                model = "HG5853SF",
                upnpVisible = true,
                authorizedQosControlAvailable = true
            )
        )

        assertEquals(RouterGamingModeStatus.READY, result.status)
        assertTrue(result.canAutoConfigureQos)
    }

    @Test
    fun unknownRouterDoesNotClaimGamingPrioritySupport() {
        val result = RouterGamingModePolicy.evaluate(
            evidence = RouterDiscoveryEvidence(
                manufacturer = null,
                model = "unknown-router",
                upnpVisible = false,
                authorizedQosControlAvailable = false
            )
        )

        assertEquals(RouterGamingModeStatus.UNKNOWN, result.status)
        assertFalse(result.hardwareQosCapable)
        assertFalse(result.canAutoConfigureQos)
    }
}

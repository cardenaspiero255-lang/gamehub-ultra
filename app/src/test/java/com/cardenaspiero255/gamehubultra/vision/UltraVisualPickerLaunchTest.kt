package com.cardenaspiero255.gamehubultra.vision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class UltraVisualPickerLaunchTest {
    @Test fun cancelledPickerMustFinishBeforeAnotherMayLaunch() {
        val launch = UltraVisualPickerLaunch()
        assertTrue(launch.begin(1L))
        launch.revoke()
        assertTrue(launch.awaitingCallback)
        assertFalse(launch.begin(2L))
        assertNull(launch.complete(currentTicket = null))
        assertFalse(launch.awaitingCallback)
        assertTrue(launch.begin(2L))
        assertEquals(2L, launch.complete(currentTicket = 2L))
    }

    @Test fun obsoletePickerResultIsNeverAttributedToNewAuthorization() {
        val launch = UltraVisualPickerLaunch()
        assertTrue(launch.begin(7L))
        launch.revoke()
        // The old callback must consume only its own launch.
        assertNull(launch.complete(currentTicket = 8L))
        assertTrue(launch.begin(8L))
        assertEquals(8L, launch.complete(currentTicket = 8L))
        assertNull(launch.complete(currentTicket = 8L))
    }

    @Test fun duplicateAndInvalidLaunchesAreRejected() {
        val launch = UltraVisualPickerLaunch()
        assertFalse(launch.begin(0L))
        assertTrue(launch.begin(10L))
        assertFalse(launch.begin(11L))
        assertNull(launch.complete(currentTicket = 11L))
        assertTrue(launch.begin(11L))
        assertEquals(11L, launch.complete(currentTicket = 11L))
    }
}

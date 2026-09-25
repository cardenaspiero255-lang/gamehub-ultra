package com.cardenaspiero255.gamehubultra.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UltraWakeCommandTaskGuardTest {
    @Test
    fun successfulPostDoesNotReleaseCommandSlotEarly() {
        var cleanupCalls = 0

        UltraWakeCommandTaskGuard.run(
            onUnposted = { cleanupCalls += 1 }
        ) {
            true
        }

        assertEquals(0, cleanupCalls)
    }

    @Test
    fun rejectedPostReleasesCommandSlot() {
        var cleanupCalls = 0

        UltraWakeCommandTaskGuard.run(
            onUnposted = { cleanupCalls += 1 }
        ) {
            false
        }

        assertEquals(1, cleanupCalls)
    }

    @Test
    fun throwableBeforePostingStillReleasesCommandSlot() {
        var cleanupCalls = 0

        assertFailsWith<AssertionError> {
            UltraWakeCommandTaskGuard.run(
                onUnposted = { cleanupCalls += 1 }
            ) {
                throw AssertionError("boom")
            }
        }

        assertEquals(1, cleanupCalls)
    }
}

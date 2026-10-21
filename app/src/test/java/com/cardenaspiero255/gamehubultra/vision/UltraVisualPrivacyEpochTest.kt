package com.cardenaspiero255.gamehubultra.vision

import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraVisualPrivacyEpochTest {
    @Test fun privacyRevocationPublishesAnObservableEpoch() {
        val previous = UltraPrivacyEpoch.current()
        val revoked = UltraPrivacyEpoch.invalidate()
        assertTrue(revoked > previous)
        assertEquals(revoked, UltraPrivacyEpoch.changes.value)
        assertFalse(UltraPrivacyEpoch.isCurrent(previous))
    }

    @Test fun repeatedPrivacyRevocationIsMonotonicAndCannotRevalidateOldSelection() {
        val old = UltraPrivacyEpoch.current()
        val first = UltraPrivacyEpoch.invalidate()
        val second = UltraPrivacyEpoch.invalidate()
        assertTrue(second > first)
        assertEquals(second, UltraPrivacyEpoch.changes.value)
        assertFalse(UltraPrivacyEpoch.isCurrent(old))
    }
}

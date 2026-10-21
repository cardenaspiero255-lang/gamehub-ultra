package com.cardenaspiero255.gamehubultra.vision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraVisualDraftPolicyTest {
    @Test fun OCRDraftRequiresUnchangedPrivacyEpochAndGameAtSendTime() {
        val origin = UltraVisualDraftPolicy.Origin(privacyEpoch = 42L, gamePackage = "game.a")
        assertTrue(UltraVisualDraftPolicy.canSend(origin, 42L, "game.a"))
        assertFalse(UltraVisualDraftPolicy.canSend(origin, 43L, "game.a"))
        assertFalse(UltraVisualDraftPolicy.canSend(origin, 42L, "game.b"))
        assertFalse(UltraVisualDraftPolicy.canSend(origin, 42L, null))
    }

    @Test fun ordinaryUserTypedTextDoesNotAcquireAnOCRRestriction() {
        assertTrue(UltraVisualDraftPolicy.canSend(null, 999L, "different.game"))
    }
}

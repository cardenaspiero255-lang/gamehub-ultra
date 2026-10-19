package com.cardenaspiero255.gamehubultra.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UltraVoluntaryVisualSessionTest {
    private var epoch = 7L
    private var millis = 1_000L
    private fun session() = UltraVoluntaryVisualSession(
        currentPrivacyEpoch = { epoch },
        monotonicMillis = { millis }
    )
    private val uri = "content://media/picker/selected.png"

    @Test fun nothingIsReadableWithoutAnExplicitPickerSelection() {
        val gate = session()
        assertNull(gate.consumeAfterUserConfirmation(1L, "game.a"))
        assertFalse(gate.acceptPickedUri(1L, uri, "game.a"))
    }

    @Test fun pickerSelectionStillRequiresSecondExplicitConfirmation() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertTrue(gate.awaitingConfirmation(ticket, "game.a"))
        // No image bytes, URI or metadata are exposed through the status API.
        assertFalse(gate.toString().contains(uri))
        assertEquals(uri, gate.consumeAfterUserConfirmation(ticket, "game.a")?.uri)
        assertFalse(gate.awaitingConfirmation(ticket, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun userCancellationRejectsNullImageAndDoesNotRetainApproval() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertFalse(gate.acceptPickedUri(ticket, null, "game.a"))
        assertFalse(gate.awaitingConfirmation(ticket, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun onlyContentProviderUrisAreEligible() {
        for (invalid in listOf("", "file:///sdcard/screenshot.png", "https://example.com/img.png",
            "content://", "content:///missing-authority", "content://media", "content://evil@media/a",
            "content://media/a\nsecret", "content://media/a".padEnd(2100, 'x'))) {
            val gate = session()
            val ticket = gate.beginUserSelection("game.a")
            assertFalse(gate.acceptPickedUri(ticket, invalid, "game.a"), invalid.take(40))
            assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
        }
    }

    @Test fun aGameSwitchInvalidatesSelectionEvenIfGameSwitchesBack() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertFalse(gate.acceptPickedUri(ticket, uri, "game.b"))
        assertFalse(gate.awaitingConfirmation(ticket, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun gameContextMustStillMatchAtFinalConfirmation() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.b"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun aLateCallbackCannotResurrectCancelledPermission() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        gate.revoke()
        assertFalse(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun aPriorPickerCallbackCannotReplaceANewerSelection() {
        val gate = session()
        val old = gate.beginUserSelection("game.a")
        val newer = gate.beginUserSelection("game.a")
        assertTrue(newer > old)
        assertFalse(gate.acceptPickedUri(old, uri, "game.a"))
        assertTrue(gate.acceptPickedUri(newer, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(old, "game.a"))
        assertEquals(uri, gate.consumeAfterUserConfirmation(newer, "game.a")?.uri)
    }

    @Test fun aPrivacyEraseEpochInvalidatesPickerCallbacks() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        epoch++
        assertFalse(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun aPrivacyEraseEpochInvalidatesAlreadyPickedImages() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        epoch++
        assertFalse(gate.awaitingConfirmation(ticket, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun pickerResultExpiresWithoutAnyBackgroundCapture() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        millis += 60_001L
        assertFalse(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun approvedSelectionPreservesItsOriginalPrivacyEpochAcrossEraseRace() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        val approved = gate.consumeAfterUserConfirmation(ticket, "game.a")
        assertEquals(uri, approved?.uri)
        assertEquals(7L, approved?.privacyEpoch)
        assertEquals("game.a", approved?.gamePackage)
        // An erase after confirmation cannot relabel a previously approved
        // image with the new epoch and silently re-authorize it.
        epoch++
        assertEquals(7L, approved?.privacyEpoch)
        assertFalse(UltraVisualDraftPolicy.canSend(
            UltraVisualDraftPolicy.Origin(approved!!.privacyEpoch, approved.gamePackage),
            epoch, "game.a"
        ))
    }

    @Test fun confirmedCandidateExpiresBeforeUserApproves() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        millis += 120_001L
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }

    @Test fun rollingBackMonotonicClockCannotExtendAuthorization() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        millis--
        assertFalse(gate.acceptPickedUri(ticket, uri, "game.a"))
    }

    @Test fun aSecondExplicitSelectionDropsTheFirstImageAndNonce() {
        val gate = session()
        val old = gate.beginUserSelection(null)
        assertTrue(gate.acceptPickedUri(old, uri, null))
        val next = gate.beginUserSelection(null)
        assertNull(gate.consumeAfterUserConfirmation(old, null))
        assertTrue(gate.acceptPickedUri(next, uri, null))
        assertEquals(uri, gate.consumeAfterUserConfirmation(next, null)?.uri)
    }

    @Test fun confirmingWrongTicketNeverLeaksTheValidUri() {
        val gate = session()
        val ticket = gate.beginUserSelection("game.a")
        assertTrue(gate.acceptPickedUri(ticket, uri, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket + 1, "game.a"))
        assertNull(gate.consumeAfterUserConfirmation(ticket, "game.a"))
    }
}

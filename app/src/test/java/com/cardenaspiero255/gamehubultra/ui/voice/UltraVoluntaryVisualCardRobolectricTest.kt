package com.cardenaspiero255.gamehubultra.ui.voice

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import com.cardenaspiero255.gamehubultra.vision.UltraVoluntaryVisualSession
import com.cardenaspiero255.gamehubultra.vision.UltraVisualReadControl
import kotlinx.coroutines.Job
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Real Compose/Robolectric coverage of the user-facing privacy boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraVoluntaryVisualCardRobolectricTest {
    @get:Rule val rule = createComposeRule()
    private var selected: ((Uri?) -> Unit)? = null
    private var shared = mutableListOf<String>()
    private var game by mutableStateOf<String?>("game.a")
    private var enabled by mutableStateOf(true)
    private val pickedUri = Uri.parse("content://media/picker/image.png")

    private fun mount(
        onRecognized: ((String, com.cardenaspiero255.gamehubultra.vision.UltraVisualDraftPolicy.Origin) -> Unit)? = null,
        read: suspend () -> String = { "CODIGO CONFIDENCIAL" }
    ) {
        selected = null
        shared = mutableListOf()
        rule.setContent {
            UltraVoluntaryVisualCard(
                gamePackage = game,
                enabled = enabled,
                onUseRecognizedText = { recognized, origin ->
                    // Tests may invalidate during the parent callback to prove
                    // that the callback never restamps private image provenance.
                    if (onRecognized != null) {
                        onRecognized(recognized, origin)
                    } else {
                        assertEquals(UltraPrivacyEpoch.current(), origin.privacyEpoch)
                        assertEquals(game, origin.gamePackage)
                        shared.add(recognized)
                    }
                },
                pickerOverride = { callback -> selected = callback },
                ocrOverride = { _, _, _, _ -> read() }
            )
        }
    }

    private fun pick(uri: Uri? = pickedUri) {
        rule.onNodeWithTag("ultra-visual-select-image").performClick()
        rule.runOnIdle { selected?.invoke(uri) }
    }

    private fun confirm() {
        rule.onNodeWithTag("ultra-visual-confirm").performClick()
    }


    @Test fun disposalCleanupRevokesSessionReadControlAndRunningWork() {
        val session = UltraVoluntaryVisualSession()
        val ticket = session.beginUserSelection("game.a")
        val control = UltraVisualReadControl()
        val work = Job()
        disposeVisualOperation(session, control, work)
        assertTrue(control.isRevoked)
        assertTrue(work.isCancelled)
        assertFailsWith<java.util.concurrent.CancellationException> { control.ensureActive() }
        assertFalse(session.acceptPickedUri(ticket, "content://media/picker/draft.png", "game.a"))
    }

    @Test fun previewNeedsSecondConsentAndNoDraftIsPreparedUntilFinalButton() {
        mount()
        rule.onNodeWithText("Visión local de Ultra").assertExists()
        rule.onNodeWithTag("ultra-visual-select-image").assertExists()
        pick()
        rule.onNodeWithText("Autorizar lectura local").assertExists()
        assertEquals(0, shared.size)
        confirm()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("CODIGO CONFIDENCIAL").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, shared.size)
        rule.onNodeWithTag("ultra-visual-use-text").performClick()
        assertEquals(listOf("CODIGO CONFIDENCIAL"), shared)
    }

    @Test fun parentHandoffKeepsOriginalEpochIfErasureRacesInsideTheCallback() {
        var captured: com.cardenaspiero255.gamehubultra.vision.UltraVisualDraftPolicy.Origin? = null
        mount(onRecognized = { _, origin ->
            captured = origin
            UltraPrivacyEpoch.invalidate()
        })
        pick()
        confirm()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("CODIGO CONFIDENCIAL").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("ultra-visual-use-text").performClick()
        val origin = assertNotNull(captured)
        assertFalse(com.cardenaspiero255.gamehubultra.vision.UltraVisualDraftPolicy.canSend(
            origin, UltraPrivacyEpoch.current(), "game.a"
        ))
    }

    @Test fun dismissingConfirmationNeverStartsAnOcrRead() {
        var calls = 0
        mount { calls++; "MUST NOT BE READ" }
        pick()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithTag("ultra-visual-confirm").assertDoesNotExist()
        assertEquals(0, calls)
        rule.onNodeWithTag("ultra-visual-select-image").assertExists()
    }

    @Test fun cancellingSystemPickerDoesNotCreateAnApproval() {
        mount()
        pick(null)
        rule.onNodeWithText("Autorizar lectura local").assertDoesNotExist()
        rule.onNodeWithText("Selección cancelada o autorización caducada.").assertExists()
        rule.onNodeWithTag("ultra-visual-select-image").assertExists()
    }

    @Test fun refusingNonContentUriKeepsApprovalClosed() {
        mount()
        pick(Uri.parse("file:///storage/emulated/0/private.png"))
        rule.onNodeWithTag("ultra-visual-confirm").assertDoesNotExist()
        rule.onNodeWithText("Selección cancelada o autorización caducada.").assertExists()
    }

    @Test fun changingGameWithOutstandingPickerSuppressesOldCallback() {
        mount()
        rule.onNodeWithTag("ultra-visual-select-image").performClick()
        rule.runOnIdle { game = "game.b" }
        rule.onNodeWithTag("ultra-visual-select-image").assertIsNotEnabled()
        rule.runOnIdle { selected?.invoke(pickedUri) }
        rule.onNodeWithTag("ultra-visual-confirm").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-select-image").performClick()
    }

    @Test fun externalPrivacyClearRevokesPendingDialog() {
        mount()
        pick()
        rule.onNodeWithText("Autorizar lectura local").assertExists()
        rule.runOnIdle { UltraPrivacyEpoch.invalidate() }
        rule.onNodeWithTag("ultra-visual-confirm").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-use-text").assertDoesNotExist()
    }

    @Test fun disablingAssistantRevokesPendingDialog() {
        mount()
        pick()
        rule.runOnIdle { enabled = false }
        rule.onNodeWithTag("ultra-visual-confirm").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-select-image").assertIsNotEnabled()
    }

    @Test fun missingTextDoesNotExposeAnySendControl() {
        mount { "" }
        pick()
        confirm()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("No se detectó texto en la imagen.").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("ultra-visual-use-text").assertDoesNotExist()
    }

    @Test fun OCRFailureIsReportedWithoutContentLeakage() {
        mount { throw IllegalArgumentException("Sensitive provider diagnostics") }
        pick()
        confirm()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("No fue posible leer esta imagen localmente.").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Sensitive provider diagnostics").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-use-text").assertDoesNotExist()
    }

    @Test fun cancellingBusyOcrRevokesResultAndEnablesNewSelection() {
        mount { suspendCancellableCoroutine { /* Cancelled by the user. */ } }
        pick()
        confirm()
        rule.onNodeWithText("Cancelar lectura").performClick()
        rule.onNodeWithText("Lectura cancelada.").assertExists()
        rule.onNodeWithTag("ultra-visual-use-text").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-select-image").assertExists()
    }

    @Test fun privacyRevocationAfterOCRHidesResultImmediately() {
        mount()
        pick()
        confirm()
        rule.waitUntil(12000) {
            rule.onAllNodesWithText("CODIGO CONFIDENCIAL").fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnIdle { UltraPrivacyEpoch.invalidate() }
        rule.onNodeWithText("CODIGO CONFIDENCIAL").assertDoesNotExist()
        rule.onNodeWithTag("ultra-visual-use-text").assertDoesNotExist()
    }
}

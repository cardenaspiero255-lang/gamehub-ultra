package com.cardenaspiero255.gamehubultra.ui.voice

import android.net.Uri
import android.os.CancellationSignal
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.vision.UltraVisualOcr
import com.cardenaspiero255.gamehubultra.vision.UltraVoluntaryVisualSession
import com.cardenaspiero255.gamehubultra.vision.UltraVisualPickerLaunch
import com.cardenaspiero255.gamehubultra.vision.UltraVisualReadControl
import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UltraVoluntaryVisualCard(
    gamePackage: String?,
    enabled: Boolean,
    onUseRecognizedText: (String) -> Unit,
    // Overrides are internal hooks for Robolectric security/coverage regressions.
    pickerOverride: (((Uri?) -> Unit) -> Unit)? = null,
    ocrOverride: (suspend (Context, Uri, UltraVisualReadControl, CancellationSignal) -> String)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { UltraVoluntaryVisualSession() }
    val launchGate = remember { UltraVisualPickerLaunch() }
    val latestGame by rememberUpdatedState(gamePackage)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestUseText by rememberUpdatedState(onUseRecognizedText)
    val privacyRevision by UltraPrivacyEpoch.changes.collectAsState()
    var ticket by remember { mutableStateOf<Long?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmationOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var textResult by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    var readControl by remember { mutableStateOf<UltraVisualReadControl?>(null) }
    var resultEpoch by remember { mutableStateOf<Long?>(null) }
    var resultGame by remember { mutableStateOf<String?>(null) }

    fun revoke() {
        session.revoke()
        readControl?.revoke()
        readControl = null
        work?.cancel()
        work = null
        ticket = null
        // A cancelled system picker still owns its outstanding callback.
        // Never launch a newer selection until the old callback returns.
        pickerOpen = launchGate.awaitingCallback
        confirmationOpen = false
        busy = false
        textResult = ""
        resultEpoch = null
        resultGame = null
    }

    val handlePickerResult: (Uri?) -> Unit = { uri ->
        // The Android picker does not include a launch nonce in its callback.
        // The launch gate forbids any overlapping picker, so an old result
        // cannot be reinterpreted as a fresh user authorization.
        val id = launchGate.complete(ticket)
        pickerOpen = false
        if (latestEnabled && id != null && uri != null &&
            session.acceptPickedUri(id, uri.toString(), latestGame)
        ) {
            confirmationOpen = true
            notice = "Imagen seleccionada. Confirma para analizarla localmente."
        } else {
            revoke()
            notice = "Selección cancelada o autorización caducada."
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> handlePickerResult(uri) }

    LaunchedEffect(gamePackage) {
        revoke()
        notice = ""
    }
    // React to privacy erasure even when it originates in another component.
    // The StateFlow is monotonic, preventing stale image text from lingering.
    LaunchedEffect(privacyRevision) {
        revoke()
        notice = ""
    }
    LaunchedEffect(enabled) {
        if (!enabled) revoke()
    }
    DisposableEffect(session) {
        onDispose {
            session.revoke()
            readControl?.revoke()
            work?.cancel()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Visión local de Ultra", style = MaterialTheme.typography.titleMedium)
        Text(
            "Selecciona una imagen y confirma antes de leer texto. " +
                "No se abre la cámara ni se envía contenido automáticamente.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            modifier = Modifier.testTag("ultra-visual-select-image"),
            enabled = enabled && !busy && !pickerOpen && !confirmationOpen,
            onClick = {
                textResult = ""
                resultEpoch = null
                resultGame = null
                val selectedTicket = session.beginUserSelection(latestGame)
                if (!launchGate.begin(selectedTicket)) {
                    session.revoke()
                    ticket = null
                    notice = "Una selección anterior aún debe cerrarse."
                } else {
                    ticket = selectedTicket
                    pickerOpen = true
                    notice = ""
                    if (pickerOverride != null) {
                        pickerOverride { selectedUri -> handlePickerResult(selectedUri) }
                    } else {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }
            }
        ) {
            Text(if (busy) "Analizando…" else "Elegir imagen")
        }
        if (busy) {
            TextButton(
                onClick = {
                    revoke()
                    notice = "Lectura cancelada."
                }
            ) { Text("Cancelar lectura") }
        }
        if (notice.isNotBlank()) {
            Text(notice, style = MaterialTheme.typography.bodySmall)
        }
        if (textResult.isNotBlank() && resultEpoch == UltraPrivacyEpoch.current() &&
            resultGame == gamePackage
        ) {
            Text(textResult, style = MaterialTheme.typography.bodySmall)
            TextButton(
                modifier = Modifier.testTag("ultra-visual-use-text"),
                enabled = enabled && !busy && resultEpoch == UltraPrivacyEpoch.current() &&
                    resultGame == gamePackage,
                onClick = {
                    if (resultEpoch == UltraPrivacyEpoch.current() &&
                        resultGame == latestGame && latestEnabled
                    ) {
                        latestUseText(textResult)
                    }
                }
            ) { Text("Preparar consulta con Ultra") }
        }
    }

    if (confirmationOpen) {
        AlertDialog(
            onDismissRequest = {
                revoke()
                notice = "Análisis cancelado."
            },
            title = { Text("Autorizar lectura local") },
            text = {
                Text(
                    "Se extraerá texto de esta imagen solamente tras confirmar. " +
                        "El resultado no se guardará en memoria ni se enviará a servicios externos."
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("ultra-visual-confirm"),
                    enabled = enabled,
                    onClick = {
                        val id = ticket
                        val approved = id?.let {
                            session.consumeAfterUserConfirmation(it, latestGame)
                        }
                        confirmationOpen = false
                        ticket = null
                        if (approved == null) {
                            revoke()
                            notice = "La autorización caducó; elige otra imagen."
                        } else {
                            val acceptedEpoch = UltraPrivacyEpoch.current()
                            val acceptedGame = latestGame
                            busy = true
                            notice = "Analizando solo en el dispositivo…"
                            val signal = CancellationSignal()
                            val control = UltraVisualReadControl { signal.cancel() }
                            readControl = control
                            work = scope.launch {
                                val currentWork = currentCoroutineContext()[Job]
                                try {
                                    val extracted = withContext(Dispatchers.IO) {
                                        val approvedUri = Uri.parse(approved)
                                        if (ocrOverride == null) {
                                            UltraVisualOcr.readApprovedImage(context, approvedUri, control, signal)
                                        } else {
                                            ocrOverride(context, approvedUri, control, signal)
                                        }
                                    }
                                    if (isActive && latestEnabled && UltraPrivacyEpoch.isCurrent(acceptedEpoch) &&
                                        latestGame == acceptedGame
                                    ) {
                                        textResult = extracted.trim().take(1000)
                                        resultEpoch = acceptedEpoch
                                        resultGame = acceptedGame
                                        notice = if (textResult.isBlank()) {
                                            "No se detectó texto en la imagen."
                                        } else {
                                            "Texto detectado. Decide si deseas usarlo en Ultra."
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    if (UltraPrivacyEpoch.isCurrent(acceptedEpoch) &&
                                        latestGame == acceptedGame && isActive
                                    ) {
                                        notice = "No fue posible leer esta imagen localmente."
                                    }
                                } finally {
                                    control.revoke()
                                    if (readControl === control) readControl = null
                                    // Revoked results are suppressed, but the matching job
                                    // must always release its busy state, even across epoch changes.
                                    if (work === currentWork) {
                                        work = null
                                        busy = false
                                        if (!UltraPrivacyEpoch.isCurrent(acceptedEpoch) ||
                                            latestGame != acceptedGame || !latestEnabled
                                        ) {
                                            textResult = ""
                                            resultEpoch = null
                                            resultGame = null
                                            notice = "Autorización de imagen revocada."
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { Text("Procesar imagen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    revoke()
                    notice = "Análisis cancelado."
                }) { Text("Cancelar") }
            }
        )
    }
}

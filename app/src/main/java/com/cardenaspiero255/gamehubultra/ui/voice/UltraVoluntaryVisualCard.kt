package com.cardenaspiero255.gamehubultra.ui.voice

import android.net.Uri
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
    onUseRecognizedText: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { UltraVoluntaryVisualSession() }
    val latestGame by rememberUpdatedState(gamePackage)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestUseText by rememberUpdatedState(onUseRecognizedText)
    var ticket by remember { mutableStateOf<Long?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var confirmationOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var textResult by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    var resultEpoch by remember { mutableStateOf<Long?>(null) }
    var resultGame by remember { mutableStateOf<String?>(null) }

    fun revoke() {
        session.revoke()
        work?.cancel()
        work = null
        ticket = null
        pickerOpen = false
        confirmationOpen = false
        busy = false
        textResult = ""
        resultEpoch = null
        resultGame = null
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        pickerOpen = false
        val id = ticket
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

    LaunchedEffect(gamePackage) {
        revoke()
        notice = ""
    }
    LaunchedEffect(enabled) {
        if (!enabled) revoke()
    }
    DisposableEffect(session) {
        onDispose {
            session.revoke()
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
                ticket = session.beginUserSelection(latestGame)
                pickerOpen = true
                notice = ""
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
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
                            work = scope.launch {
                                val currentWork = currentCoroutineContext()[Job]
                                try {
                                    val extracted = withContext(Dispatchers.IO) {
                                        UltraVisualOcr.readApprovedImage(context, Uri.parse(approved))
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

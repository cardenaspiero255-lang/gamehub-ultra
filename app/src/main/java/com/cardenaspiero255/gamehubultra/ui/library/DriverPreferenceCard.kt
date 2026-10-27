package com.cardenaspiero255.gamehubultra.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardenaspiero255.gamehubultra.platform.gpu.AndroidGraphicsDriverPreferences
import com.cardenaspiero255.gamehubultra.platform.gpu.DriverPreferenceStatus
import com.cardenaspiero255.gamehubultra.platform.gpu.OpenGlDriverPreference

/** Per-game user settings, never an implicit privileged GPU-driver switch. */
@Composable
internal fun DriverPreferenceCard(packageName: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var requested by remember(packageName) {
        mutableStateOf(AndroidGraphicsDriverPreferences.selected(context, packageName))
    }
    var revision by remember(packageName) { mutableIntStateOf(0) }
    var settingsUnavailable by remember(packageName) { mutableStateOf(false) }

    DisposableEffect(owner, packageName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val readback = remember(packageName, requested, revision) {
        AndroidGraphicsDriverPreferences.inspect(context, packageName)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Driver OpenGL por juego", style = MaterialTheme.typography.titleMedium)
            Text(
                "La preferencia se guarda solo para este juego. Android controla el driver real.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    OpenGlDriverPreference.SYSTEM to "Sistema",
                    OpenGlDriverPreference.ANGLE to "ANGLE",
                    OpenGlDriverPreference.NATIVE to "Nativo"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = requested == mode,
                        onClick = {
                            if (AndroidGraphicsDriverPreferences.save(context, packageName, mode)) {
                                requested = mode
                                revision++
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            val message = when (readback.status) {
                DriverPreferenceStatus.SYSTEM_DEFAULT ->
                    "Sin cambio de driver aplicado por GameHub; Android decide."
                DriverPreferenceStatus.SYSTEM_SETTING_CONFIRMED ->
                    "Preferencia de Android confirmada para este paquete. " +
                        "El driver cargado dentro del juego aún no está verificado."
                DriverPreferenceStatus.MANUAL_CONFIGURATION_REQUIRED ->
                    "Preferencia pendiente: debes seleccionarla también para este juego " +
                        "en Opciones de desarrollador de Android. GameHub no la aplica automáticamente."
                DriverPreferenceStatus.UNVERIFIABLE ->
                    "No es posible verificar de forma segura la preferencia del sistema."
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text(
                "Turnip no se instala ni se activa desde Android estándar sin un backend " +
                    "compatible y comprobado.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = {
                    settingsUnavailable =
                        !AndroidGraphicsDriverPreferences.openSystemDeveloperOptions(context)
                }
            ) {
                Text("Abrir opciones de desarrollador")
            }
            if (settingsUnavailable) {
                Text(
                    "Este dispositivo no permite abrir esas opciones desde GameHub.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

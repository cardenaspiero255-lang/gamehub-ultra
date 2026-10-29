package com.cardenaspiero255.gamehubultra.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.cardenaspiero255.gamehubultra.platform.compat.AndroidDeviceCompatibilityProbe
import com.cardenaspiero255.gamehubultra.platform.compat.AndroidDeviceCompatibilityStore
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDecision
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityDeviceSnapshot
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityGpuFamily
import com.cardenaspiero255.gamehubultra.platform.compat.CompatibilityStatus
import com.cardenaspiero255.gamehubultra.platform.compat.DeviceCompatibilityDatabase
import com.cardenaspiero255.gamehubultra.platform.gpu.CapabilityEvidence
import com.cardenaspiero255.gamehubultra.platform.vendor.VendorSocFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CAR-56: only reads hardware evidence for this app and shows a safe diagnosis. */
@Composable
internal fun CompatibilityDiagnosticsCard(
    packageName: String,
    readSnapshot: (Context) -> CompatibilityDeviceSnapshot = {
        AndroidDeviceCompatibilityProbe.read(it)
    }
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember(packageName) { mutableIntStateOf(0) }
    var snapshot by remember(packageName) { mutableStateOf<CompatibilityDeviceSnapshot?>(null) }
    var decision by remember(packageName) {
        mutableStateOf(CompatibilityDecision(CompatibilityStatus.INVALID_INPUT))
    }
    var clipboardUnavailable by remember(packageName) { mutableStateOf(false) }

    DisposableEffect(owner, packageName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(context, packageName, revision) {
        val (observed, assessment) = withContext(Dispatchers.IO) {
            val readout = readSnapshot(context)
            val saved = AndroidDeviceCompatibilityStore.load(context)
            // A locally stored record is NOT its own verifier.
            readout to DeviceCompatibilityDatabase.resolve(
                readout.key, packageName, saved, emptySet()
            )
        }
        snapshot = observed
        decision = assessment
    }

    if (snapshot == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text("Comprobando capacidades Android…", modifier = Modifier.padding(14.dp))
        }
    }
    snapshot?.let { observed ->
        CompatibilityDiagnosticsContent(
            snapshot = observed,
            decision = decision,
            onCopyDiagnostic = {
                val safeText = DeviceCompatibilityDatabase.exportDiagnostic(
                    observed.key, packageName, decision
                )
                clipboardUnavailable = !runCatching {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Compatibilidad", safeText))
                    true
                }.getOrDefault(false)
            }
        )
        if (clipboardUnavailable) {
            Text("No se pudo copiar el diagnóstico en este dispositivo.")
        }
    }
}

@Composable
internal fun CompatibilityDiagnosticsContent(
    snapshot: CompatibilityDeviceSnapshot,
    decision: CompatibilityDecision,
    onCopyDiagnostic: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Compatibilidad del dispositivo (CAR-56)",
                style = MaterialTheme.typography.titleMedium
            )
            if (snapshot.deviceClassObserved) {
                val socName = when (snapshot.key.socFamily) {
                    VendorSocFamily.MEDIATEK -> "MediaTek"
                    VendorSocFamily.QUALCOMM -> "Qualcomm"
                    VendorSocFamily.EXYNOS -> "Exynos"
                    VendorSocFamily.GENERIC -> "No verificado"
                }
                val gpuName = when (snapshot.key.gpuFamily) {
                    CompatibilityGpuFamily.MALI -> "Mali"
                    CompatibilityGpuFamily.ADRENO -> "Adreno"
                    CompatibilityGpuFamily.POWERVR -> "PowerVR"
                    else -> "Desconocida"
                }
                Text("$socName · $gpuName · Android API ${snapshot.key.androidApiLevel}")
            } else {
                Text("Datos insuficientes para clasificar el dispositivo.")
            }
            val gles = if (snapshot.openGlEs == CapabilityEvidence.VERIFIED) {
                "verificado"
            } else {
                "no verificado"
            }
            val vulkan = if (snapshot.vulkan == CapabilityEvidence.VERIFIED) {
                "verificado"
            } else if (snapshot.vulkan == CapabilityEvidence.UNAVAILABLE) {
                "no disponible"
            } else {
                "no verificado"
            }
            Text("OpenGL ES: $gles · Vulkan: $vulkan")
            val message = when (decision.status) {
                CompatibilityStatus.KNOWN_GOOD ->
                    "Existe una recomendación verificada; se requiere acción explícita."
                CompatibilityStatus.NO_RECORD ->
                    "Sin perfil conocido verificado para este juego."
                CompatibilityStatus.UNVERIFIED ->
                    "Hay una regla local sin evidencia autenticada; no se aplica."
                CompatibilityStatus.AMBIGUOUS ->
                    "Reglas contradictorias: configuración recomendada no verificada."
                CompatibilityStatus.INVALID_INPUT ->
                    "No hay datos suficientes para recomendar un perfil."
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            Text(
                "El diagnóstico no cambia drivers ni activa perfiles.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onCopyDiagnostic) {
                Text("Copiar diagnóstico sin identificadores")
            }
        }
    }
}

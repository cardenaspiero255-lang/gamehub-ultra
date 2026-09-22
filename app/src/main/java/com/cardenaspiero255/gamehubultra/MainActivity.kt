package com.cardenaspiero255.gamehubultra

import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceController
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.PerformanceState
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme

class MainActivity : ComponentActivity() {
    private lateinit var performanceController: PerformanceController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val capabilities = DeviceCapabilitiesProvider.get(this)
        performanceController = PerformanceController(capabilities)
        val initialState = performanceController.apply(PerformanceProfile.BALANCED, window)
        val device = DeviceInfoProvider.get(this)

        setContent {
            GameHubUltraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameHubUltraApp(
                        initialState = initialState,
                        device = device,
                        onProfileSelected = { profile ->
                            performanceController.apply(profile, window)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHubUltraApp(
    initialState: PerformanceState,
    device: DeviceInfo,
    onProfileSelected: (PerformanceProfile) -> PerformanceState
) {
    var state by remember { mutableStateOf(initialState) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GAMEHUB ULTRA", style = MaterialTheme.typography.headlineMedium)
                Text("Centro de rendimiento para Android", style = MaterialTheme.typography.bodyLarge)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Perfil activo", style = MaterialTheme.typography.titleLarge)
                    Text(state.selectedProfile.title, style = MaterialTheme.typography.headlineSmall)
                    Text(state.selectedProfile.description)
                    Text(
                        if (state.sustainedModeApplied)
                            "Sustained Performance Mode: aplicado a GameHub Ultra."
                        else
                            "Sustained Performance Mode: no aplicado."
                    )
                    Text(
                        "Intención de interpolación: " +
                            if (state.selectedProfile.frameInterpolationIntent)
                                "priorizada, pero requiere una API compatible."
                            else
                                "no solicitada."
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Modos de rendimiento", style = MaterialTheme.typography.titleLarge)
                PerformanceProfile.entries.forEach { profile ->
                    Button(
                        onClick = { state = onProfileSelected(profile) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profile.title)
                    }
                }
            }
        }

        item {
            DeviceStatusCard(device = device, capabilities = state.capabilities)
        }
    }
}

@Composable
private fun DeviceStatusCard(
    device: DeviceInfo,
    capabilities: DeviceCapabilities?
) {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val thermal = remember(powerManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus
        } else {
            null
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Estado real del dispositivo", style = MaterialTheme.typography.titleLarge)
            DeviceRow("Fabricante", device.manufacturer)
            DeviceRow("Modelo", device.model)
            DeviceRow("Android", "${device.androidVersion} (API ${device.sdkInt})")
            DeviceRow("CPU", device.cpuModel)
            DeviceRow("Núcleos lógicos", device.cpuCores.toString())
            DeviceRow("RAM total", "${device.totalRamMb} MB")
            DeviceRow("GPU vendor", device.gpuVendor ?: "No disponible")
            DeviceRow("GPU renderer", device.gpuRenderer ?: "No disponible")
            DeviceRow("ABI", device.supportedAbis.joinToString().ifBlank { "No disponible" })

            Text(
                "Sustained Performance: " +
                    if (capabilities?.sustainedPerformanceSupported == true) "compatible" else "no disponible"
            )
            Text(
                "Thermal API: " +
                    if (capabilities?.thermalStatusAvailable == true) "disponible" else "no disponible"
            )
            Text(
                "Performance Hint API: " +
                    if (capabilities?.performanceHintsAvailable == true) "disponible" else "no disponible"
            )
            Text("Estado térmico: ${thermalLabel(thermal)}")
            Text(
                "Límite real: GameHub Ultra no puede cambiar por sí solo la frecuencia de CPU/GPU, " +
                    "activar interpolación de frames ni modificar el modo de rendimiento de otra " +
                    "aplicación sin APIs privilegiadas o soporte del fabricante."
            )
        }
    }
}

@Composable
private fun DeviceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value)
    }
}

private fun thermalLabel(status: Int?): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "leve"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderado"
        PowerManager.THERMAL_STATUS_SEVERE -> "severo"
        PowerManager.THERMAL_STATUS_CRITICAL -> "crítico"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergencia"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "apagado térmico"
        null -> "no disponible"
        else -> "desconocido"
    }

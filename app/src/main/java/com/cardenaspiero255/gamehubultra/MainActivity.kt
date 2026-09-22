package com.cardenaspiero255.gamehubultra

import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.ui.theme.GameHubUltraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameHubUltraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameHubUltraApp()
                }
            }
        }
    }
}

@Composable
private fun GameHubUltraApp() {
    var selected by remember { mutableStateOf(PerformanceProfile.BALANCED) }
    val device = remember { DeviceInfoProvider.get() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
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
                    Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                    Text(selected.description)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Modos de rendimiento", style = MaterialTheme.typography.titleLarge)
                PerformanceProfile.entries.forEach { profile ->
                    Button(
                        onClick = { selected = profile },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profile.title)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Estado del dispositivo", style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fabricante")
                        Text(device.manufacturer)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Modelo")
                        Text(device.model)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Android")
                        Text(device.androidVersion)
                    }
                    Text("ABI: ${device.supportedAbis.joinToString()}")
                    Text(
                        "Las optimizaciones avanzadas dependerán de las APIs, permisos y capacidades expuestas por cada dispositivo."
                    )
                }
            }
        }
    }
}

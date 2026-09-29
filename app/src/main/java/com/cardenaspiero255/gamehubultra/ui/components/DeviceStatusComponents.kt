package com.cardenaspiero255.gamehubultra.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.thermalLabel
import com.cardenaspiero255.gamehubultra.domain.BatteryTelemetry
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilities
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo

@Composable
internal fun DeviceStatusCard(
    device: DeviceInfo,
    capabilities: DeviceCapabilities?
) {
    val context = LocalContext.current
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val batteryManager = remember(context) {
        context.getSystemService(android.os.BatteryManager::class.java)
    }

    var batteryPercent by remember {
        mutableStateOf(
            BatteryTelemetry.sanitizePercentage(
                batteryManager?.getIntProperty(
                    android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
                )
            )
        )
    }
    var thermalStatus by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager?.currentThermalStatus
            } else {
                null
            }
        )
    }

    DisposableEffect(context, batteryManager, powerManager) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val level = intent.getIntExtra(
                    android.os.BatteryManager.EXTRA_LEVEL,
                    -1
                )
                val scale = intent.getIntExtra(
                    android.os.BatteryManager.EXTRA_SCALE,
                    -1
                )
                batteryPercent = BatteryTelemetry.fromBroadcast(level, scale)
            }
        }

        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val thermalListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                PowerManager.OnThermalStatusChangedListener { status ->
                    thermalStatus = status
                }
            } else {
                null
            }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalListener != null &&
            powerManager != null
        ) {
            powerManager.addThermalStatusListener(
                ContextCompat.getMainExecutor(context),
                thermalListener
            )
        }

        onDispose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            if (
                thermalListener != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                powerManager != null
            ) {
                powerManager.removeThermalStatusListener(thermalListener)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.device_status),
                style = MaterialTheme.typography.titleLarge
            )
            if (batteryPercent != null) {
                Text(
                    stringResource(R.string.battery) + ": " +
                        batteryPercent + "%"
                )
                LinearProgressIndicator(
                    progress = { (batteryPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.battery) + ": " +
                        stringResource(R.string.not_available)
                )
            }

            Text(
                stringResource(R.string.thermal_status) + ": " +
                    thermalLabel(thermalStatus)
            )
            DeviceRow(stringResource(R.string.manufacturer), device.manufacturer)
            DeviceRow(stringResource(R.string.model), device.model)
            DeviceRow(
                stringResource(R.string.android_version),
                device.androidVersion + " (API " + device.sdkInt + ")"
            )
            DeviceRow(
                stringResource(R.string.cpu),
                device.cpuModel.ifBlank {
                    stringResource(R.string.not_available)
                }
            )
            DeviceRow(
                stringResource(R.string.cores),
                device.cpuCores.toString()
            )
            DeviceRow(
                stringResource(R.string.ram),
                device.totalRamMb.toString() + " MB"
            )
            DeviceRow(
                stringResource(R.string.gpu_vendor),
                device.gpuVendor ?: stringResource(R.string.not_available)
            )
            DeviceRow(
                stringResource(R.string.gpu_renderer),
                device.gpuRenderer ?: stringResource(R.string.not_available)
            )
            DeviceRow(
                stringResource(R.string.abi),
                device.supportedAbis.joinToString().ifBlank {
                    stringResource(R.string.not_available)
                }
            )
            CapabilityRow(
                stringResource(R.string.sustained_performance),
                capabilities?.sustainedPerformanceSupported == true
            )
            CapabilityRow(
                stringResource(R.string.thermal_api),
                capabilities?.thermalStatusAvailable == true
            )
            CapabilityRow(
                stringResource(R.string.performance_hint_api),
                capabilities?.performanceHintsAvailable == true
            )
            Text(stringResource(R.string.capability_note))
        }
    }
}

@Composable
internal fun CapabilityRow(
    label: String,
    supported: Boolean
) {
    DeviceRow(
        label,
        if (supported) {
            stringResource(R.string.supported)
        } else {
            stringResource(R.string.not_supported)
        }
    )
}

@Composable
internal fun DeviceRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, modifier = Modifier.weight(0.9f))
        Text(value, modifier = Modifier.weight(1.6f))
    }
}


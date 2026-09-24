package com.cardenaspiero255.gamehubultra.platform

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.ActivityManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import com.cardenaspiero255.gamehubultra.BatteryTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ThermalTelemetry(
    val status: Int?,
    val headroom: Float?
)

data class BatteryRuntimeTelemetry(
    val percent: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean
)

data class RefreshTelemetry(
    val supportedRefreshRatesHz: Set<Int>,
    val currentRefreshRateHz: Float?
)

data class ConnectivityTelemetry(
    val networkHandle: Long?,
    val connected: Boolean,
    val validated: Boolean,
    val metered: Boolean,
    val transport: String?,
    val downstreamBandwidthKbps: Int?,
    val latencyMs: Long?
)

data class MemoryRuntimeTelemetry(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long
) {
    val usedPercent: Int
        get() = if (totalRamMb <= 0L) 0 else ((usedRamMb * 100L) / totalRamMb).toInt().coerceIn(0, 100)
}

data class StorageTelemetry(
    val freeBytes: Long,
    val totalBytes: Long
) {
    val freePercent: Int
        get() = if (totalBytes <= 0L) 0 else ((freeBytes * 100L) / totalBytes)
            .toInt()
            .coerceIn(0, 100)
}

data class RuntimeDiagnostics(
    val thermal: ThermalTelemetry,
    val battery: BatteryRuntimeTelemetry,
    val refresh: RefreshTelemetry,
    val connectivity: ConnectivityTelemetry,
    val storage: StorageTelemetry,
    val memory: MemoryRuntimeTelemetry,
    val inputDeviceCount: Int,
    val peripherals: PeripheralDiagnostics
)

object RuntimeDiagnosticsProvider {
    fun get(context: Context): RuntimeDiagnostics {
        val appContext = context.applicationContext
        return RuntimeDiagnostics(
            thermal = runCatching { readThermal(appContext) }
                .getOrDefault(ThermalTelemetry(null, null)),
            battery = runCatching { readBattery(appContext) }
                .getOrDefault(BatteryRuntimeTelemetry(null, false, false)),
            refresh = runCatching { readRefresh(appContext) }
                .getOrDefault(RefreshTelemetry(emptySet(), null)),
            connectivity = runCatching { readConnectivity(appContext) }
                .getOrDefault(ConnectivityTelemetry(null, false, false, true, null, null, null)),
            storage = runCatching { readStorage() }
                .getOrDefault(StorageTelemetry(0L, 0L)),
            memory = runCatching { readMemory(appContext) }
                .getOrDefault(MemoryRuntimeTelemetry(0L, 0L, 0L)),
            inputDeviceCount = runCatching { readInputDevices(appContext) }
                .getOrDefault(0),
            peripherals = runCatching { PeripheralDiagnosticsProvider.get(appContext) }
                .getOrDefault(PeripheralDiagnostics(0, 0, 0, 0))
        )
    }

    private fun readThermal(context: Context): ThermalTelemetry {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalTelemetry(null, null)
        }
        val powerManager = context.getSystemService(PowerManager::class.java)
            ?: return ThermalTelemetry(null, null)

        val status = runCatching { powerManager.currentThermalStatus }.getOrNull()
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { powerManager.getThermalHeadroom(10) }
                .getOrNull()
                ?.takeIf { !it.isNaN() && it >= 0f }
        } else {
            null
        }
        return ThermalTelemetry(status, headroom)
    }

    private fun readBattery(context: Context): BatteryRuntimeTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val percent = intent?.let {
            BatteryTelemetry.fromBroadcast(
                level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            )
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val powerManager = context.getSystemService(PowerManager::class.java)
        val powerSave = powerManager?.isPowerSaveMode == true

        return BatteryRuntimeTelemetry(percent, charging, powerSave)
    }

    private fun readRefresh(context: Context): RefreshTelemetry {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)

        val supported = display?.supportedModes
            ?.map { mode -> mode.refreshRate.roundToHz() }
            ?.filter { it in 30..360 }
            ?.toSet()
            .orEmpty()
        val current = display?.refreshRate

        return RefreshTelemetry(supported, current)
    }

    private fun readConnectivity(context: Context): ConnectivityTelemetry {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return ConnectivityTelemetry(null, false, false, true, null, null, null)

        val network = manager.activeNetwork ?: return ConnectivityTelemetry(
            networkHandle = null,
            connected = false,
            validated = false,
            metered = manager.isActiveNetworkMetered,
            transport = null,
            downstreamBandwidthKbps = null,
            latencyMs = null
        )
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return ConnectivityTelemetry(
                networkHandle = network.networkHandle,
                connected = false,
                validated = false,
                metered = manager.isActiveNetworkMetered,
                transport = null,
                downstreamBandwidthKbps = null,
                latencyMs = null
            )

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Móvil"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Otro"
        }

        return ConnectivityTelemetry(
            networkHandle = network.networkHandle,
            connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = manager.isActiveNetworkMetered,
            transport = transport,
            downstreamBandwidthKbps = capabilities
                .linkDownstreamBandwidthKbps
                .takeIf { it > 0 },
            latencyMs = null
        )
    }

    private fun readMemory(context: Context): MemoryRuntimeTelemetry {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return MemoryRuntimeTelemetry(0L, 0L, 0L)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val totalBytes = info.totalMem.coerceAtLeast(0L)
        val availableBytes = info.availMem.coerceIn(0L, totalBytes)
        return MemoryRuntimeTelemetry(
            totalRamMb = totalBytes / (1024L * 1024L),
            availableRamMb = availableBytes / (1024L * 1024L),
            usedRamMb = (totalBytes - availableBytes) / (1024L * 1024L)
        )
    }

    private fun readStorage(): StorageTelemetry {
        val stats = StatFs(android.os.Environment.getDataDirectory().path)
        return StorageTelemetry(
            freeBytes = stats.availableBytes.coerceAtLeast(0L),
            totalBytes = stats.totalBytes.coerceAtLeast(0L)
        )
    }

    private fun readInputDevices(context: Context): Int {
        val inputManager = context.getSystemService(InputManager::class.java)
        return inputManager?.inputDeviceIds?.count { deviceId ->
            inputManager.getInputDevice(deviceId)?.isVirtual != true
        } ?: 0
    }

    private fun Float.roundToHz(): Int = kotlin.math.round(this).toInt()
}

object ConnectivityLatencyProbe {
    private const val MAX_LATENCY_MS = 10_000L

    suspend fun measure(
        context: Context,
        expectedNetworkHandle: Long,
        endpoint: String = "https://www.gstatic.com/generate_204"
    ): Long? = withContext(Dispatchers.IO) {
        val manager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
            ?: return@withContext null

        val network = manager.activeNetwork ?: return@withContext null
        if (network.networkHandle != expectedNetworkHandle) return@withContext null

        val capabilities = manager.getNetworkCapabilities(network)
            ?: return@withContext null
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return@withContext null
        }

        runCatching {
            val start = android.os.SystemClock.elapsedRealtime()
            val connection = network.openConnection(URL(endpoint)) as HttpURLConnection
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connect()
            connection.inputStream.use { }
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            connection.disconnect()

            val currentNetwork = manager.activeNetwork
            elapsed.takeIf {
                currentNetwork?.networkHandle == expectedNetworkHandle && it in 1..MAX_LATENCY_MS
            }
        }.getOrNull()
    }
}

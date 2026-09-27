package com.cardenaspiero255.gamehubultra.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider

object NetworkRuntimeOptimizer {
    private const val LOCK_TAG = "GameHubUltra:NetworkBooster"
    private val lockGuard = Any()
    private var activeLock: WifiManager.WifiLock? = null
    private var activeMode: Int? = null

    fun apply(context: Context, profile: NetworkGameProfile): Boolean {
        val telemetry = RuntimeDiagnosticsProvider.get(context).connectivity
        val action = NetworkLocalPriorityPolicy.actionFor(
            profile,
            telemetry.transport,
            telemetry.connected,
            telemetry.validated,
            Build.VERSION.SDK_INT
        )
        return when (action) {
            NetworkPriorityAction.RELEASE_WIFI_LOCK -> {
                release()
                true
            }
            NetworkPriorityAction.UNAVAILABLE -> false
            NetworkPriorityAction.LOW_LATENCY_WIFI ->
                acquire(context, WifiManager.WIFI_MODE_FULL_LOW_LATENCY)
            NetworkPriorityAction.HIGH_PERFORMANCE_WIFI ->
                acquire(context, WifiManager.WIFI_MODE_FULL_HIGH_PERF)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquire(context: Context, mode: Int): Boolean = synchronized(lockGuard) {
        runCatching {
            val manager = context.applicationContext.getSystemService(WifiManager::class.java)
                ?: return@synchronized false
            if (activeMode != mode) {
                activeLock?.takeIf { it.isHeld }?.release()
                activeLock = null
            }
            val lock = activeLock ?: manager.createWifiLock(mode, LOCK_TAG).also {
                it.setReferenceCounted(false)
                activeLock = it
                activeMode = mode
            }
            if (!lock.isHeld) lock.acquire()
            lock.isHeld
        }.getOrDefault(false)
    }

    fun release() = synchronized(lockGuard) {
        runCatching { activeLock?.takeIf { it.isHeld }?.release() }
        activeLock = null
        activeMode = null
    }
}

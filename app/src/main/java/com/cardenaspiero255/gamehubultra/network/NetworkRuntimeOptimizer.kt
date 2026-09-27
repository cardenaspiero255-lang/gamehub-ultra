package com.cardenaspiero255.gamehubultra.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider

enum class NetworkOptimizationOutcome {
    NOT_REQUESTED,
    APPLIED,
    RELEASED_OR_NOT_NEEDED,
    UNAVAILABLE
}

object NetworkOptimizationResultPolicy {
    fun reportsApplied(action: NetworkPriorityAction): Boolean =
        action == NetworkPriorityAction.LOW_LATENCY_WIFI ||
            action == NetworkPriorityAction.HIGH_PERFORMANCE_WIFI
}

object NetworkLockLifecyclePolicy {
    fun shouldRelease(
        wifiTransport: Boolean,
        validated: Boolean
    ): Boolean = !wifiTransport || !validated
}

object NetworkLeaseGenerationPolicy {
    fun shouldRelease(
        callbackGeneration: Long,
        activeGeneration: Long
    ): Boolean = callbackGeneration == activeGeneration
}

object NetworkRuntimeOptimizer {
    private const val LOCK_TAG = "GameHubUltra:NetworkBooster"
    private const val MAX_LOCK_LEASE_MILLIS = 30L * 60L * 1000L

    private val lockGuard = Any()
    private var activeLock: WifiManager.WifiLock? = null
    private var activeMode: Int? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val leaseHandler = Handler(Looper.getMainLooper())
    private var leaseGeneration: Long = 0L
    private var pendingLeaseRelease: Runnable? = null

    fun apply(context: Context, profile: NetworkGameProfile): NetworkOptimizationOutcome {
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
                NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED
            }
            NetworkPriorityAction.UNAVAILABLE -> {
                release()
                NetworkOptimizationOutcome.UNAVAILABLE
            }
            NetworkPriorityAction.LOW_LATENCY_WIFI,
            NetworkPriorityAction.HIGH_PERFORMANCE_WIFI -> {
                val mode = if (action == NetworkPriorityAction.LOW_LATENCY_WIFI) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                if (acquire(context, mode)) {
                    NetworkOptimizationOutcome.APPLIED
                } else {
                    NetworkOptimizationOutcome.UNAVAILABLE
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquire(context: Context, mode: Int): Boolean = synchronized(lockGuard) {
        runCatching {
            val appContext = context.applicationContext
            val manager = appContext.getSystemService(WifiManager::class.java)
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

            if (!lock.isHeld) {
                lock.acquire()
            }

            leaseGeneration += 1L
            val generation = leaseGeneration
            pendingLeaseRelease?.let(leaseHandler::removeCallbacks)
            val releaseTask = Runnable { releaseIfGeneration(generation) }
            pendingLeaseRelease = releaseTask
            leaseHandler.postDelayed(releaseTask, MAX_LOCK_LEASE_MILLIS)

            ensureConnectivityGuard(appContext)
            lock.isHeld
        }.getOrDefault(false)
    }

    private fun releaseIfGeneration(callbackGeneration: Long) = synchronized(lockGuard) {
        if (
            !NetworkLeaseGenerationPolicy.shouldRelease(
                callbackGeneration = callbackGeneration,
                activeGeneration = leaseGeneration
            )
        ) {
            return@synchronized
        }
        releaseLocked()
    }

    private fun ensureConnectivityGuard(context: Context) {
        if (networkCallback != null) return

        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                release()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val shouldRelease = NetworkLockLifecyclePolicy.shouldRelease(
                    wifiTransport = networkCapabilities.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI
                    ),
                    validated = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
                )
                if (shouldRelease) {
                    release()
                }
            }
        }

        runCatching {
            manager.registerDefaultNetworkCallback(callback)
            connectivityManager = manager
            networkCallback = callback
        }
    }

    fun release() = synchronized(lockGuard) {
        leaseGeneration += 1L
        releaseLocked()
    }

    private fun releaseLocked() {
        pendingLeaseRelease?.let(leaseHandler::removeCallbacks)
        pendingLeaseRelease = null

        runCatching {
            activeLock?.takeIf { it.isHeld }?.release()
        }
        activeLock = null
        activeMode = null

        val manager = connectivityManager
        val callback = networkCallback
        connectivityManager = null
        networkCallback = null
        if (manager != null && callback != null) {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
}

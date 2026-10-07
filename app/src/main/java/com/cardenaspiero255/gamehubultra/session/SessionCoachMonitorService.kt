package com.cardenaspiero255.gamehubultra.session

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.MainActivity
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.data.SessionCoachSessionStore
import com.cardenaspiero255.gamehubultra.data.SessionCoachStoredSession
import com.cardenaspiero255.gamehubultra.domain.AiSessionCoach
import com.cardenaspiero255.gamehubultra.domain.SessionCoachMessage
import com.cardenaspiero255.gamehubultra.domain.SessionCoachPriority
import com.cardenaspiero255.gamehubultra.domain.SessionCoachSnapshot
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionAdvisor
import com.cardenaspiero255.gamehubultra.domain.ThermalPredictionEngine
import com.cardenaspiero255.gamehubultra.platform.ConnectivityLatencyProbe
import com.cardenaspiero255.gamehubultra.platform.ConnectivityTelemetry
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun installedGameVersionKey(
    context: Context,
    packageName: String
): String? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
    }
    val versionName = info.versionName.orEmpty().trim()
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    "${versionName}#${versionCode}"
}.getOrNull()

internal enum class SessionCoachGamePresence {
    ACTIVE,
    INACTIVE,
    UNKNOWN
}

internal object SessionCoachGamePresenceDetector {
    private const val LOOKBACK_MS = 2L * 60L * 1_000L
    private const val LEGACY_MOVE_TO_FOREGROUND = 1
    private const val LEGACY_MOVE_TO_BACKGROUND = 2

    fun observe(
        context: Context,
        packageName: String,
        previousPresence: SessionCoachGamePresence = SessionCoachGamePresence.UNKNOWN,
        nowMillis: Long = System.currentTimeMillis()
    ): SessionCoachGamePresence {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive == false) {
            return SessionCoachGamePresence.INACTIVE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !hasUsageAccess(context)) {
            return SessionCoachGamePresence.UNKNOWN
        }

        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return SessionCoachGamePresence.UNKNOWN
        val events = manager.queryEvents((nowMillis - LOOKBACK_MS).coerceAtLeast(0L), nowMillis)
        val event = UsageEvents.Event()
        var latestForegroundPackage: String? = null
        var latestForegroundAt = Long.MIN_VALUE
        var targetBackgroundAt = Long.MIN_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                LEGACY_MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.timeStamp >= latestForegroundAt) {
                        latestForegroundAt = event.timeStamp
                        latestForegroundPackage = event.packageName
                    }
                }

                LEGACY_MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (event.packageName == packageName) {
                        targetBackgroundAt = maxOf(targetBackgroundAt, event.timeStamp)
                    }
                }
            }
        }

        return resolvePresence(
            latestForegroundPackage = latestForegroundPackage,
            latestForegroundAt = latestForegroundAt,
            targetBackgroundAt = targetBackgroundAt,
            previousPresence = previousPresence,
            targetPackage = packageName
        )
    }

    internal fun resolvePresence(
        latestForegroundPackage: String?,
        latestForegroundAt: Long,
        targetBackgroundAt: Long,
        previousPresence: SessionCoachGamePresence,
        targetPackage: String = "game.a"
    ): SessionCoachGamePresence =
        when {
            latestForegroundPackage == targetPackage ->
                SessionCoachGamePresence.ACTIVE
            latestForegroundPackage != null &&
                latestForegroundAt >= targetBackgroundAt ->
                SessionCoachGamePresence.INACTIVE
            targetBackgroundAt != Long.MIN_VALUE ->
                SessionCoachGamePresence.INACTIVE
            previousPresence == SessionCoachGamePresence.ACTIVE ->
                SessionCoachGamePresence.ACTIVE
            else ->
                SessionCoachGamePresence.UNKNOWN
        }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

class SessionCoachMonitorService : Service() {
    companion object {
        const val ACTION_START =
            "com.cardenaspiero255.gamehubultra.session.COACH_START"
        const val ACTION_STOP =
            "com.cardenaspiero255.gamehubultra.session.COACH_STOP"

        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val SAMPLE_INTERVAL_MS = 15_000L
        private const val LATENCY_RECHECK_MS = 30_000L
        private const val MAX_SESSION_DURATION_MS = 4L * 60L * 60L * 1_000L

        private const val INACTIVE_EVIDENCE_REQUIRED = 3
        internal const val UNKNOWN_EVIDENCE_REQUIRED = 20

        internal fun shouldProbeLatency(
            network: ConnectivityTelemetry,
            lastNetworkHandle: Long? = null,
            lastLatencyCheckAt: Long,
            nowMillis: Long
        ): Boolean =
            network.connected &&
                network.validated &&
                !network.metered &&
                network.networkHandle != null &&
                (
                    network.networkHandle != lastNetworkHandle ||
                        lastLatencyCheckAt == 0L ||
                        nowMillis - lastLatencyCheckAt >= LATENCY_RECHECK_MS
                    )

        internal fun shouldResetLatency(
            network: ConnectivityTelemetry,
            lastNetworkHandle: Long? = null
        ): Boolean =
            !network.connected ||
                !network.validated ||
                network.metered ||
                (
                    lastNetworkHandle != null &&
                        network.networkHandle != lastNetworkHandle
                    )

        internal fun shouldStopOwnedMonitor(
            activeMonitorSessionId: String?,
            expectedSessionId: String?
        ): Boolean =
            expectedSessionId == null || activeMonitorSessionId == expectedSessionId

        internal fun nextInactiveEvidenceCount(
            presence: SessionCoachGamePresence,
            currentCount: Int
        ): Int =
            when (presence) {
                SessionCoachGamePresence.ACTIVE -> 0
                SessionCoachGamePresence.INACTIVE -> currentCount + 1
                SessionCoachGamePresence.UNKNOWN -> 0
            }

        internal fun shouldFinishForInactivity(evidenceCount: Int): Boolean =
            evidenceCount >= INACTIVE_EVIDENCE_REQUIRED

        internal fun nextUnknownEvidenceCount(
            presence: SessionCoachGamePresence,
            currentCount: Int
        ): Int =
            when (presence) {
                SessionCoachGamePresence.UNKNOWN -> currentCount + 1
                SessionCoachGamePresence.ACTIVE,
                SessionCoachGamePresence.INACTIVE -> 0
            }

        internal fun shouldFinishForUnknownPresence(evidenceCount: Int): Boolean =
            evidenceCount >= UNKNOWN_EVIDENCE_REQUIRED

        internal fun thermalPredictionObservation(
            samples: List<SessionCoachSnapshot>,
            previousObservation: SessionCoachMessage?,
            engine: ThermalPredictionEngine = ThermalPredictionEngine()
        ): SessionCoachMessage? {
            val latestStatus = samples.lastOrNull()?.thermalStatus
            if (
                latestStatus != null &&
                latestStatus >= engine.policy.severeThermalStatus
            ) {
                return null
            }
            return ThermalPredictionAdvisor.message(
                prediction = engine.predict(samples),
                previousObservation = previousObservation
            )
        }

        internal fun start(
            context: Context,
            packageName: String,
            nowMillis: Long = System.currentTimeMillis(),
            sessionId: String = UUID.randomUUID().toString()
        ): String? {
            val cleanPackage = packageName.trim()
            if (cleanPackage.isEmpty()) return null

            val store = SessionCoachSessionStore(context)
            store.finishActiveSession(nowMillis)?.let { previous ->
                SessionCoachNotifications.postSummary(context, previous)
            }
            val gameVersion = installedGameVersionKey(context, cleanPackage)
            if (!store.beginSession(
                    sessionId = sessionId,
                    packageName = cleanPackage,
                    startedAtMillis = nowMillis,
                    gameVersion = gameVersion
                )
            ) {
                return null
            }

            val intent = Intent(context, SessionCoachMonitorService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_PACKAGE_NAME, cleanPackage)

            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                sessionId
            }.getOrElse {
                store.discardActiveSession()
                null
            }
        }

        internal fun cancelLaunch(context: Context) {
            SessionCoachSessionStore(context).discardActiveSession()
            requestStop(context)
        }

        fun finishOnReturn(
            context: Context,
            nowMillis: Long = System.currentTimeMillis()
        ): SessionCoachStoredSession? {
            val finished = SessionCoachSessionStore(context)
                .finishActiveSession(nowMillis)
                ?: return null
            SessionCoachNotifications.postSummary(context, finished)
            requestStop(context)
            return finished
        }

        private fun requestStop(context: Context) {
            val intent = Intent(context, SessionCoachMonitorService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val store by lazy { SessionCoachSessionStore(applicationContext) }
    private var monitorJob: Job? = null
    private var activeMonitorSessionId: String? = null
    private var lastLatencyMs: Long? = null
    private var lastLatencyCheckAt = 0L
    private var lastLatencyNetworkHandle: Long? = null

    override fun onCreate() {
        super.onCreate()
        SessionCoachNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val expectedSessionId = activeMonitorSessionId
                store.finishActiveSession(
                    endedAtMillis = System.currentTimeMillis(),
                    expectedSessionId = expectedSessionId
                )?.let { finished ->
                    SessionCoachNotifications.postSummary(this, finished)
                }
                stopMonitoring(startId, expectedSessionId)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                if (sessionId.isBlank() || packageName.isBlank()) {
                    stopMonitoring(startId)
                    return START_NOT_STICKY
                }

                ensureForeground(
                    SessionCoachNotifications.foreground(
                        context = this,
                        title = "Ultra Session Coach",
                        detail = "Preparando análisis para $packageName…"
                    )
                )
                val previousMonitorJob = monitorJob
                activeMonitorSessionId = sessionId
                monitorJob = null
                previousMonitorJob?.cancel()
                lastLatencyMs = null
                lastLatencyCheckAt = 0L
                lastLatencyNetworkHandle = null
                monitorJob = serviceScope.launch {
                    monitorSession(sessionId, packageName, startId)
                }
            }

            else -> stopMonitoring(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorSession(
        sessionId: String,
        packageName: String,
        startId: Int
    ) {
        val startedAt = store.readActiveSession()
            ?.takeIf { it.sessionId == sessionId }
            ?.startedAtMillis
        if (startedAt == null) {
            stopMonitoring(startId, sessionId)
            return
        }

        val device = withContext(Dispatchers.IO) {
            DeviceInfoProvider.get(applicationContext)
        }
        var previous = store.readActiveSession()?.samples?.lastOrNull()
        var firstSample = previous == null
        var inactiveEvidenceCount = 0
        var unknownEvidenceCount = 0
        var previousGamePresence = SessionCoachGamePresence.UNKNOWN

        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            if (now - startedAt >= MAX_SESSION_DURATION_MS) {
                finishOwnedSession(sessionId, now, startId)
                return
            }
            val activeSession = store.readActiveSession()
            if (activeSession?.sessionId != sessionId) {
                stopMonitoring(startId, sessionId)
                return
            }

            val presence = withContext(Dispatchers.IO) {
                SessionCoachGamePresenceDetector.observe(
                    context = applicationContext,
                    packageName = packageName,
                    previousPresence = previousGamePresence,
                    nowMillis = now
                )
            }
            previousGamePresence = presence
            inactiveEvidenceCount = nextInactiveEvidenceCount(
                presence = presence,
                currentCount = inactiveEvidenceCount
            )
            unknownEvidenceCount = nextUnknownEvidenceCount(
                presence = presence,
                currentCount = unknownEvidenceCount
            )
            if (
                shouldFinishForInactivity(inactiveEvidenceCount) ||
                shouldFinishForUnknownPresence(unknownEvidenceCount)
            ) {
                finishOwnedSession(sessionId, now, startId)
                return
            }

            val diagnostics = readDiagnostics(now)
            val current = SessionCoachTelemetryMapper.snapshot(now, diagnostics)
            val measuredObservations = previous
                ?.let { AiSessionCoach.midSession(it, current) }
                .orEmpty()
            val thermalPrediction = thermalPredictionObservation(
                samples = activeSession.samples + current,
                previousObservation = activeSession.latestThermalPredictionObservation
            )
            val observations = buildList {
                addAll(measuredObservations)
                thermalPrediction?.let(::add)
            }

            store.appendSnapshot(
                sessionId = sessionId,
                snapshot = current,
                observations = observations,
                thermalPredictionObservation = thermalPrediction
            )

            if (firstSample) {
                val pre = AiSessionCoach.preSession(
                    readiness = SessionCoachTelemetryMapper.readiness(device, diagnostics),
                    snapshot = current
                )
                store.recordPreSession(sessionId, pre)
                updateForeground(
                    SessionCoachNotifications.foreground(
                        context = this,
                        title = pre.title,
                        detail = pre.detail
                    )
                )
                firstSample = false
            } else {
                observations.maxByOrNull { it.priority.ordinal }?.let { message ->
                    updateForeground(
                        SessionCoachNotifications.foreground(
                            context = this,
                            title = message.title,
                            detail = message.action ?: message.detail
                        )
                    )
                    if (message.priority == SessionCoachPriority.ACTION) {
                        SessionCoachNotifications.postAction(this, message)
                    }
                }
            }

            previous = current
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private suspend fun readDiagnostics(nowMillis: Long): RuntimeDiagnostics {
        val base = withContext(Dispatchers.IO) {
            RuntimeDiagnosticsProvider.get(applicationContext)
        }
        val network = base.connectivity
        val handle = network.networkHandle

        if (shouldResetLatency(network, lastLatencyNetworkHandle)) {
            lastLatencyMs = null
            lastLatencyCheckAt = 0L
            lastLatencyNetworkHandle = null
        }

        val shouldProbe = shouldProbeLatency(
            network = network,
            lastNetworkHandle = lastLatencyNetworkHandle,
            lastLatencyCheckAt = lastLatencyCheckAt,
            nowMillis = nowMillis
        )
        if (shouldProbe) {
            lastLatencyMs = withContext(Dispatchers.IO) {
                ConnectivityLatencyProbe.measure(
                    context = applicationContext,
                    expectedNetworkHandle = checkNotNull(handle)
                )
            }
            lastLatencyCheckAt = nowMillis
            lastLatencyNetworkHandle = handle
        }

        return base.copy(
            connectivity = network.copy(latencyMs = lastLatencyMs)
        )
    }

    private fun finishOwnedSession(
        sessionId: String,
        nowMillis: Long,
        startId: Int
    ) {
        store.finishActiveSession(
            endedAtMillis = nowMillis,
            expectedSessionId = sessionId
        )?.let { finished ->
            SessionCoachNotifications.postSummary(this, finished)
        }
        stopMonitoring(startId, sessionId)
    }

    private fun ensureForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SessionCoachNotifications.FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SessionCoachNotifications.FOREGROUND_ID, notification)
        }
    }

    private fun updateForeground(notification: Notification) {
        getSystemService(NotificationManager::class.java)
            ?.notify(SessionCoachNotifications.FOREGROUND_ID, notification)
    }

    private fun stopMonitoring(
        startId: Int,
        expectedSessionId: String? = null
    ) {
        if (!shouldStopOwnedMonitor(activeMonitorSessionId, expectedSessionId)) return
        monitorJob?.cancel()
        monitorJob = null
        activeMonitorSessionId = null
        if (stopSelfResult(startId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }
}

internal object SessionCoachNotificationPermission {
    fun shouldRequest(
        sdkInt: Int,
        granted: Boolean,
        hasRequestedBefore: Boolean = false,
        shouldShowRationale: Boolean = false
    ): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            !granted &&
            !hasRequestedBefore &&
            !shouldShowRationale
}

internal object SessionCoachNotifications {
    const val FOREGROUND_ID = 45_001
    private const val SUMMARY_ID = 45_002
    private const val ACTION_ID = 45_003
    private const val CHANNEL_ID = "ultra_session_coach"
    private const val ALERT_CHANNEL_ID = "ultra_session_coach_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ultra Session Coach",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Análisis de sesión con telemetría real del dispositivo."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Alertas de Ultra Session Coach",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Cambios relevantes detectados durante una sesión."
            }
        )
    }

    fun foreground(
        context: Context,
        title: String,
        detail: String
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_gamehub)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                "Detener",
                stopIntent(context)
            )
            .build()
    }

    fun postAction(
        context: Context,
        message: SessionCoachMessage
    ) {
        ensureChannel(context)
        val detail = message.action ?: message.detail
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_gamehub)
            .setContentTitle(message.title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ACTION_ID, notification)
    }

    fun postSummary(
        context: Context,
        session: SessionCoachStoredSession
    ) {
        ensureChannel(context)
        val report = AiSessionCoach.postSession(session.samples)
        val detail = buildString {
            append(report.summary)
            report.nextSteps.take(3).forEach { step ->
                append("\n• ")
                append(step)
            }
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_gamehub)
            .setContentTitle("Resumen de sesión · Ultra")
            .setContentText(report.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(SUMMARY_ID, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopIntent(context: Context): PendingIntent =
        PendingIntent.getService(
            context,
            1,
            Intent(context, SessionCoachMonitorService::class.java)
                .setAction(SessionCoachMonitorService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

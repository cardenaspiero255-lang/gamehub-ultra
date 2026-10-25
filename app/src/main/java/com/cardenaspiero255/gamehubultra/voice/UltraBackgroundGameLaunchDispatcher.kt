package com.cardenaspiero255.gamehubultra.voice

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cardenaspiero255.gamehubultra.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android 10+ disallows unsolicited activity starts from background services.
 * An active foreground microphone service DOES NOT remove that restriction.
 *
 * Never report a mere, exception-free Context.startActivity call as an observed
 * launch. An explicitly user-granted overlay can permit a direct request on
 * supported OS versions; otherwise require the user to tap an explicit game
 * notification (system-sent PendingIntent).
 */
internal enum class UltraBackgroundLaunchPath {
    REQUEST_DIRECT,
    USER_TAP_NOTIFICATION,
    NOTIFICATION_PERMISSION_REQUIRED
}

internal enum class UltraBackgroundGameLaunchOutcome {
    REQUESTED,
    TAP_REQUIRED,
    NOTIFICATION_PERMISSION_REQUIRED,
    NOT_INSTALLED,
    FAILED
}

internal object UltraBackgroundGameLaunchPolicy {
    fun decide(
        appVisible: Boolean,
        overlayAllowed: Boolean,
        notificationsAllowed: Boolean
    ): UltraBackgroundLaunchPath = when {
        appVisible || overlayAllowed -> UltraBackgroundLaunchPath.REQUEST_DIRECT
        notificationsAllowed -> UltraBackgroundLaunchPath.USER_TAP_NOTIFICATION
        else -> UltraBackgroundLaunchPath.NOTIFICATION_PERMISSION_REQUIRED
    }
}

internal class UltraBackgroundGameLaunchDispatcher(
    private val launchIntentForPackage: (String) -> Intent?,
    private val visible: () -> Boolean,
    private val overlayAllowed: () -> Boolean,
    private val notificationsAllowed: () -> Boolean,
    private val startActivity: (Intent) -> Unit,
    private val postNotification: (String, Intent) -> Boolean
) {
    fun launch(packageName: String): UltraBackgroundGameLaunchOutcome {
        val intent = launchIntentForPackage(packageName)
            ?: return UltraBackgroundGameLaunchOutcome.NOT_INSTALLED
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val notificationAllowed = notificationsAllowed()
        return when (
            UltraBackgroundGameLaunchPolicy.decide(
                appVisible = visible(),
                overlayAllowed = overlayAllowed(),
                notificationsAllowed = notificationAllowed
            )
        ) {
            UltraBackgroundLaunchPath.REQUEST_DIRECT -> {
                val started = runCatching { startActivity(intent) }.isSuccess
                if (started) {
                    // API returns Unit, not proof that the new activity was shown.
                    UltraBackgroundGameLaunchOutcome.REQUESTED
                } else {
                    fallbackToNotification(packageName, intent, notificationAllowed)
                }
            }
            UltraBackgroundLaunchPath.USER_TAP_NOTIFICATION ->
                fallbackToNotification(packageName, intent, notificationAllowed)
            UltraBackgroundLaunchPath.NOTIFICATION_PERMISSION_REQUIRED ->
                UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED
        }
    }

    private fun fallbackToNotification(
        packageName: String,
        intent: Intent,
        notificationAllowed: Boolean
    ): UltraBackgroundGameLaunchOutcome {
        if (!notificationAllowed) {
            return UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED
        }
        return if (runCatching { postNotification(packageName, intent) }.getOrDefault(false)) {
            UltraBackgroundGameLaunchOutcome.TAP_REQUIRED
        } else {
            UltraBackgroundGameLaunchOutcome.FAILED
        }
    }

    companion object {
        fun from(context: Context): UltraBackgroundGameLaunchDispatcher {
            val appContext = context.applicationContext
            return UltraBackgroundGameLaunchDispatcher(
                launchIntentForPackage = appContext.packageManager::getLaunchIntentForPackage,
                visible = UltraAppVisibilityTracker::isVisible,
                overlayAllowed = {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        Settings.canDrawOverlays(appContext)
                },
                notificationsAllowed = {
                    NotificationManagerCompat.from(appContext).areNotificationsEnabled() &&
                        (
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                ContextCompat.checkSelfPermission(
                                    appContext, Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            )
                },
                startActivity = appContext::startActivity,
                postNotification = { packageName, intent ->
                    UltraBackgroundGameLaunchNotification.show(appContext, packageName, intent)
                }
            )
        }
    }
}

/** Actual UI visibility, not the foreground-service status or app process alive. */
internal class UltraAppVisibilityTracker : Application.ActivityLifecycleCallbacks {
    override fun onActivityStarted(activity: Activity) {
        visibleCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        visibleCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private val visibleCount = AtomicInteger(0)
        fun isVisible(): Boolean = visibleCount.get() > 0
    }
}

internal object UltraBackgroundGameLaunchNotification {
    private const val CHANNEL_ID = "ultra_voice_launch_actions"
    private const val NOTIFICATION_ID_BASE = 24100

    fun show(context: Context, packageName: String, intent: Intent): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Apertura de juegos solicitada por voz",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Acción para abrir un juego cuando Android bloquea aperturas automáticas."
                }
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gamehub_tile)
            .setContentTitle("GameHub Ultra: abrir juego")
            .setContentText("Android requiere que toques aquí para abrir el juego solicitado.")
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID_BASE + (packageName.hashCode() and 0x0FFF), notification)
        return true
    }
}

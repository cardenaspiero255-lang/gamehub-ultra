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


/**
 * Speech must describe the Android dispatch result, not claim that the
 * foreground activity was observed. Keep user-visible outcomes testable
 * without booting a game or bypassing Android background-launch policy.
 */
internal object UltraBackgroundGameLaunchFeedback {
    fun response(
        outcome: UltraBackgroundGameLaunchOutcome?,
        openedGameLabel: String?,
        ordinaryResult: () -> String
    ): String = when (outcome) {
        UltraBackgroundGameLaunchOutcome.TAP_REQUIRED ->
            "Recibí la orden. Android no me permite abrir ese juego " +
                "directamente desde otra aplicación. Toca la notificación para abrirlo."
        UltraBackgroundGameLaunchOutcome.NOTIFICATION_PERMISSION_REQUIRED ->
            "Recibí la orden, pero Android restringe la apertura desde " +
                "segundo plano y las notificaciones están desactivadas. " +
                "Actívalas o permite la apertura manos libres desde ajustes de Ultra."
        UltraBackgroundGameLaunchOutcome.FAILED ->
            "No pude entregar a Android la solicitud de apertura. " +
                "Revisa las notificaciones y vuelve a intentarlo."
        UltraBackgroundGameLaunchOutcome.NOT_INSTALLED ->
            "Android no encontró una actividad de inicio para el juego solicitado."
        UltraBackgroundGameLaunchOutcome.REQUESTED ->
            if (openedGameLabel != null) {
                "Envié a Android la solicitud de abrir $openedGameLabel."
            } else ordinaryResult()
        null -> ordinaryResult()
    }
}

internal object UltraBackgroundGameLaunchPolicy {
    fun decide(
        appVisible: Boolean,
        overlayAllowed: Boolean,
        notificationsAllowed: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): UltraBackgroundLaunchPath = when {
        // Background activity launch restrictions start with Android 10 (API 29).
        // Preserve the existing hands-free path on supported Android 8/9.
        appVisible || overlayAllowed || sdkInt < Build.VERSION_CODES.Q ->
            UltraBackgroundLaunchPath.REQUEST_DIRECT
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
                visible = { UltraAppVisibilityTracker.isVisible() },
                overlayAllowed = {
                    // API 35+ additionally constrains the overlay exception
                    // (the overlay must be visible). We do not create a window,
                    // so never claim an overlay grant alone authorizes launching.
                    Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        Settings.canDrawOverlays(appContext)
                },
                notificationsAllowed = {
                    val enabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    enabled && permissionGranted &&
                        UltraBackgroundGameLaunchNotification.isChannelEnabled(appContext)
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

    /**
     * User-controlled channel importance is independent of app-wide
     * POST_NOTIFICATIONS permission. A missing channel is created on demand.
     */
    fun isChannelEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return false
        return manager.getNotificationChannel(CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }

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
        // Creating an already user-blocked channel does not restore its
        // importance. Do not claim TAP_REQUIRED if Android will drop the alert.
        if (!isChannelEnabled(context)) return false

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

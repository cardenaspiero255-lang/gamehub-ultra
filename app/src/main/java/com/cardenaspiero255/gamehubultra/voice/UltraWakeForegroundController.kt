package com.cardenaspiero255.gamehubultra.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cardenaspiero255.gamehubultra.R

internal class UltraWakeForegroundController(
    private val service: Service
) {
    fun ensureForeground() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gamehub_tile)
            .setContentTitle("GameHub Ultra")
            .setContentText("Escucha activa: di “Ultra …” para usar comandos.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Asistente de voz GameHub Ultra",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Indica que la escucha continua está activa."
        }
        service.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "ultra_voice"
        const val NOTIFICATION_ID = 2301
    }
}

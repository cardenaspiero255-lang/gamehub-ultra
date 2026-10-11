package com.gamehub.ultra.optimizer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager

data class HardwareInfo(
    val manufacturer: String,
    val model: String,
    val cpuCores: Int,
    val ramGb: Int,
    val refreshRate: Float
)

object HardwareDetector {

    fun detect(context: Context): HardwareInfo {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val ramGb = (memoryInfo.totalMem / (1024L * 1024L * 1024L)).toInt()

        val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.refreshRate
        }

        return HardwareInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            ramGb = ramGb,
            refreshRate = refreshRate
        )
    }

    fun recommendedProfile(info: HardwareInfo): String {
        return when {
            info.ramGb >= 12 && info.cpuCores >= 8 -> "PERFORMANCE"
            info.ramGb >= 8 && info.cpuCores >= 8 -> "BALANCED"
            else -> "COOL"
        }
    }
}

package com.cardenaspiero255.gamehubultra.platform

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import java.io.File

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val cpuModel: String,
    val cpuCores: Int,
    val totalRamMb: Long,
    val gpuVendor: String?,
    val gpuRenderer: String?
)

object DeviceInfoProvider {
    fun get(context: Context): DeviceInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)

        val gpu = GpuInfoProvider.get()

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuModel = detectCpuModel(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalRamMb = memoryInfo.totalMem.coerceAtLeast(0L) / BYTES_PER_MB,
            gpuVendor = gpu?.vendor,
            gpuRenderer = gpu?.renderer
        )
    }

    private fun detectCpuModel(): String {
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        return socModel
            ?: CpuInfoParser.parseModel(readCpuInfo())
            ?: "No disponible"
    }

    private fun readCpuInfo(): String? = runCatching {
        File("/proc/cpuinfo").bufferedReader().use { it.readText() }
    }.getOrNull()

    private const val BYTES_PER_MB = 1024L * 1024L
}

object CpuInfoParser {
    private val modelKeys = setOf("model name", "hardware", "processor")

    fun parseModel(cpuInfo: String?): String? {
        if (cpuInfo.isNullOrBlank()) return null

        return cpuInfo.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null

                val key = line.substring(0, separator).trim().lowercase()
                if (key !in modelKeys) return@mapNotNull null

                val value = line.substring(separator + 1).trim()
                if (value.isEmpty() || (key == "processor" && value.all(Char::isDigit))) {
                    return@mapNotNull null
                }

                value
            }
            .firstOrNull()
    }
}

data class GpuInfo(
    val vendor: String?,
    val renderer: String?
)

object GpuInfoProvider {
    fun get(): GpuInfo? = runCatching { detect() }.getOrNull()

    private fun detect(): GpuInfo? {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var madeCurrent = false

        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

            val config = chooseConfig(display) ?: return null

            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                ),
                0
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null

            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
                ),
                0
            )
            if (surface == EGL14.EGL_NO_SURFACE) return null

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            madeCurrent = true

            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            if (vendor.isNullOrBlank() && renderer.isNullOrBlank()) return null

            return GpuInfo(vendor = vendor?.trim(), renderer = renderer?.trim())
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (madeCurrent) {
                    EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                }
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface)
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context)
                }
                EGL14.eglTerminate(display)
            }
        }
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig? {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)

        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0)) {
            return null
        }

        return configs[0]
    }
}

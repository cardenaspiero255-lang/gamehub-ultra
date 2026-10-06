package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStore
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.domain.EmulatorBackendDetector
import com.cardenaspiero255.gamehubultra.domain.OptimizationFingerprint
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal object VoiceOptimizationFeedbackContext {
    internal fun enrichOrBase(
        base: GameHubAiContext,
        loader: suspend () -> GameHubAiContext
    ): GameHubAiContext =
        runCatching { runBlocking { loader() } }.getOrDefault(base)

    fun enrichBlockingOrBase(
        base: GameHubAiContext,
        context: Context,
        device: DeviceInfo
    ): GameHubAiContext =
        enrichOrBase(base) {
            enrich(base = base, context = context, device = device)
        }

    suspend fun enrich(
        base: GameHubAiContext,
        repository: GameOptimizationMemoryStateRepository,
        contextKey: OptimizationContextKey
    ): GameHubAiContext =
        base.copy(
            optimizationObservations = repository
                .observationsFlow(contextKey)
                .first()
        )

    suspend fun enrich(
        base: GameHubAiContext,
        context: Context,
        device: DeviceInfo
    ): GameHubAiContext {
        val appContext = context.applicationContext
        val repository: GameOptimizationMemoryStateRepository =
            GameOptimizationMemoryStore(appContext)
        return enrich(
            base = base,
            repository = repository,
            contextKey = contextKey(
                context = appContext,
                device = device,
                gamePackage = base.selectedGamePackage
            )
        )
    }

    private fun contextKey(
        context: Context,
        device: DeviceInfo,
        gamePackage: String?
    ): OptimizationContextKey {
        val gameVersion = gamePackage
            ?.takeIf(String::isNotBlank)
            ?.let { packageName ->
                @Suppress("DEPRECATION")
                runCatching {
                    context.packageManager
                        .getPackageInfo(packageName, 0)
                        .versionName
                }.getOrNull()
            }
        return buildContextKey(
            device = device,
            gamePackage = gamePackage,
            gameVersion = gameVersion,
            emulatorBackend = EmulatorBackendDetector.detect()
        )
    }

    internal fun buildContextKey(
        device: DeviceInfo,
        gamePackage: String?,
        gameVersion: String?,
        emulatorBackend: String?
    ): OptimizationContextKey {
        val gpuVendor = device.gpuVendor?.trim().orEmpty()
        val gpuRenderer = device.gpuRenderer?.trim().orEmpty()
        val driverFingerprint = if (gpuVendor.isBlank() && gpuRenderer.isBlank()) {
            null
        } else {
            "$gpuVendor|$gpuRenderer"
        }
        return OptimizationContextKey(
            deviceFingerprint = OptimizationFingerprint.from(
                device = device,
                gamePackage = gamePackage,
                gameVersion = gameVersion,
                emulatorBackend = emulatorBackend,
                driverFingerprint = driverFingerprint
            ),
            gamePackage = gamePackage.orEmpty(),
            gameVersion = gameVersion,
            emulatorBackend = emulatorBackend,
            driverFingerprint = driverFingerprint
        )
    }
}

package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import com.cardenaspiero255.gamehubultra.ai.GameHubAiContext
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStateRepository
import com.cardenaspiero255.gamehubultra.data.GameOptimizationMemoryStore
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKey
import com.cardenaspiero255.gamehubultra.data.OptimizationContextKeyFactory
import com.cardenaspiero255.gamehubultra.domain.EmulatorBackendDetector
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
    ): GameHubAiContext = enrichOrBase(base) { enrich(base, context, device) }

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
        return enrich(
            base,
            GameOptimizationMemoryStore(appContext),
            contextKey(appContext, device, base.selectedGamePackage)
        )
    }

    private fun contextKey(
        context: Context,
        device: DeviceInfo,
        gamePackage: String?
    ): OptimizationContextKey {
        @Suppress("DEPRECATION")
        val gameVersion = gamePackage?.takeIf(String::isNotBlank)?.let { packageName ->
            runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull()
        }
        return buildContextKey(device, gamePackage, gameVersion, EmulatorBackendDetector.detect())
    }

    internal fun buildContextKey(
        device: DeviceInfo,
        gamePackage: String?,
        gameVersion: String?,
        emulatorBackend: String?
    ): OptimizationContextKey =
        OptimizationContextKeyFactory.from(
            device = device,
            gamePackage = gamePackage,
            gameVersion = gameVersion,
            emulatorBackend = emulatorBackend
        )
}

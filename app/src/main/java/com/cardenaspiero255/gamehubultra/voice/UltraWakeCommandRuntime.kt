package com.cardenaspiero255.gamehubultra.voice

import android.content.Context
import android.content.Intent
import com.cardenaspiero255.gamehubultra.GameLibrary
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.ai.AiAdviceFormatter
import com.cardenaspiero255.gamehubultra.ai.GameHubAiAdvisor
import com.cardenaspiero255.gamehubultra.ai.UltraAgentRoute
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryCommandParser
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryTurnPersistencePolicy
import com.cardenaspiero255.gamehubultra.ai.UltraQueryExecutor
import com.cardenaspiero255.gamehubultra.ai.UltraRuntimeTelemetry
import com.cardenaspiero255.gamehubultra.ai.UltraUnifiedAgentRouter
import com.cardenaspiero255.gamehubultra.composition.GameHubProductionComposition
import com.cardenaspiero255.gamehubultra.data.DurableSelectionMutationQueue
import com.cardenaspiero255.gamehubultra.data.GameSelectionStateRepository
import com.cardenaspiero255.gamehubultra.data.GameAliasStateRepository
import com.cardenaspiero255.gamehubultra.data.UltraConversationMemoryStore
import com.cardenaspiero255.gamehubultra.data.effectiveProfileForSelection
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceCapabilitiesProvider
import com.cardenaspiero255.gamehubultra.platform.DeviceInfoProvider
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal class UltraWakeCommandRuntime(
    private val context: Context,
    private val queryExecutor: UltraQueryExecutor,
    private val aiAdvisor: GameHubAiAdvisor,
    private val aliasRepository: GameAliasStateRepository,
    private val memoryStore: UltraConversationMemoryStore,
    private val conversationLedger: UltraVoiceConversationLedger
) {
    fun execute(transcript: String): String {
        val selectionRepository: GameSelectionStateRepository =
            GameHubProductionComposition.selectionRepository(context.applicationContext)
        val selectedGamePackage = runCatching {
            runBlocking { selectionRepository.selectedGameFlow().first() }
        }.getOrNull()
        val selectedProfile = runCatching {
            runBlocking { selectionRepository.effectiveProfileForSelection(selectedGamePackage) }
        }.getOrNull() ?: PerformanceProfile.BALANCED

        val device = DeviceInfoProvider.get(context)
        val diagnostics = RuntimeDiagnosticsProvider.get(context)
        val capabilities = DeviceCapabilitiesProvider.get(context)
        val baseAiContext = VoiceAiContextFactory.create(selectedGamePackage, selectedProfile, device, diagnostics, capabilities)
        val aiContext =
            VoiceOptimizationFeedbackContext.enrichBlockingOrBase(baseAiContext, context, device)
        val status = VoiceDeviceStatus(
            batteryPercent = diagnostics.battery.percent,
            thermalLabel = voiceThermalLabel(diagnostics.thermal.status)
        )
        val inSessionResponse = UltraInSessionVoiceResponder.respond(
            query = transcript,
            metrics = UltraSessionMetricsFactory.fromRuntime(
                selectedGamePackage = selectedGamePackage,
                refreshRateHz = diagnostics.refresh.currentRefreshRateHz,
                batteryPercent = diagnostics.battery.percent,
                thermalLabel = status.thermalLabel,
                totalRamMb = device.totalRamMb,
                networkLatencyMs = diagnostics.connectivity.latencyMs
            )
        )
        if (inSessionResponse.handled) return inSessionResponse.message

        val intentResolver = aiAdvisor.intentResolver()
        val voicePreferences = GameHubProductionComposition.voicePreferenceRepository(context.applicationContext).load()
        val personalizedCommand = UltraVoicePersonalizationResolver.resolve(
            transcript = transcript,
            selectedGamePackage = selectedGamePackage,
            preferences = voicePreferences
        )
        conversationLedger.bindScope(selectedGamePackage)
        val conversationBefore = conversationLedger.snapshot()
        val contextualCommand = UltraContextualVoiceResolver.resolve(
            transcript = transcript,
            context = UltraVoiceContext(
                selectedGamePackage = selectedGamePackage,
                thermalLabel = status.thermalLabel
            ),
            optionalResolver = intentResolver,
            knownGameAliases = aliasRepository.aliases().keys
        )
        val route = when {
            personalizedCommand != null -> UltraAgentRoute.Command(personalizedCommand)
            contextualCommand !is VoiceCommand.Unknown -> UltraAgentRoute.Command(contextualCommand)
            else -> UltraUnifiedAgentRouter.route(
            transcript = transcript,
            optionalResolver = intentResolver,
            telemetry = UltraRuntimeTelemetry(
                batteryPercent = status.batteryPercent,
                thermalLabel = status.thermalLabel,
                refreshRateHz = diagnostics.refresh.currentRefreshRateHz
            ),
            knownGameAliases = aliasRepository.aliases().keys,
            conversationHistory = conversationBefore
        )
        }

        return when (route) {
            is UltraAgentRoute.Utility -> {
                if (route.answer.intent is com.cardenaspiero255.gamehubultra.ai.UltraUtilityIntent.NetworkGamingControl) {
                    com.cardenaspiero255.gamehubultra.ai.UltraNetworkGamingRuntimeController.execute(
                        intent = route.answer.intent,
                        applyCompetitive = {
                            com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer.apply(
                                context,
                                com.cardenaspiero255.gamehubultra.network.NetworkGameProfile.COMPETITIVE
                            )
                        }
                    )
                } else route.answer.message
            }
            is UltraAgentRoute.Chat -> {
                val memoryCommand = UltraMemoryCommandParser.parse(route.message)
                val answer = queryExecutor.answer(
                    route = route,
                    stableKnowledgeFallback = {
                        aiAdvisor.generalKnowledgeChatOrNull(
                            message = route.message,
                            context = aiContext,
                            conversation = conversationBefore
                        )
                    }
                ) {
                    aiAdvisor.chat(
                        message = route.message,
                        context = aiContext,
                        conversation = conversationBefore
                    )
                }
                if (memoryCommand == null) {
                    val delta = conversationLedger.record(route.message, answer)
                    memoryStore.enqueueSyncConversation(
                        previous = delta.previous,
                        next = delta.next,
                        scope = UltraMemoryScope(userId = "local", gamePackage = selectedGamePackage),
                        timestampMillis = System.currentTimeMillis()
                    )
                } else if (UltraMemoryTurnPersistencePolicy.resetsConversationContext(memoryCommand)) {
                    conversationLedger.clear()
                }
                answer
            }
            is UltraAgentRoute.Command -> formatCommandResult(
                VoiceCommandEngine.execute(
                    command = route.command,
                    gamesProvider = { GameLibrary.discoverForVoice(context) },
                    aliasGamesProvider = { GameLibrary.discover(context).games },
                    launchGame = { packageName -> launchGame(packageName) },
                    saveSelectedGame = { packageName ->
                        DurableSelectionMutationQueue.enqueue(
                            onFailure = ::reportSelectionPersistenceFailure
                        ) { selectionRepository.saveSelectedGame(packageName) }
                    },
                    saveSelectedProfile = { profile ->
                        DurableSelectionMutationQueue.enqueue(
                            onFailure = ::reportSelectionPersistenceFailure
                        ) { selectionRepository.saveSelectedProfile(profile) }
                    },
                    saveSelectedGameWithProfile = { packageName, profile ->
                        DurableSelectionMutationQueue.enqueue(
                            onFailure = ::reportSelectionPersistenceFailure
                        ) { selectionRepository.saveSelectedGameAndProfile(packageName, profile) }
                    },
                    isProfileAvailable = { profile ->
                        if (selectedGamePackage == null) {
                            true
                        } else {
                            UltraSessionProfileSafety.canApply(
                                command = VoiceCommand.SelectProfile(profile),
                                sessionActive = true,
                                safelySupported = capabilities.sustainedPerformanceSupported
                            )
                        }
                    },
                    statusProvider = { status },
                    deferProfileApplication = true,
                    aiAdvisor = { question -> aiAdvisor.advise(question, aiContext) },
                    aliasIntentResolver = intentResolver,
                    gameAliasesProvider = { aliasRepository.aliases() },
                    saveGameAlias = { alias, packageName -> aliasRepository.save(alias, packageName) },
                    networkStatusProvider = { VoiceNetworkSnapshotFactory.current(context) },
                    applyNetworkProfile = { profile ->
                        com.cardenaspiero255.gamehubultra.network.NetworkRuntimeOptimizer.apply(context, profile)
                    }
                )
            )
        }
    }

    private fun formatCommandResult(result: VoiceActionResult): String = when (result) {
        is VoiceActionResult.ProfileSelected -> "Perfil ${result.profile.title} seleccionado."
        is VoiceActionResult.GameOpened -> "Abriendo ${result.game.label}."
        is VoiceActionResult.GameAliasSaved -> context.getString(
            R.string.voice_result_game_alias_saved,
            result.alias.uppercase(),
            result.game.label
        )
        is VoiceActionResult.DeviceStatus ->
            "Estado: batería ${result.status.batteryPercent ?: "no disponible"} por ciento, térmica ${result.status.thermalLabel}."
        is VoiceActionResult.NetworkReport -> NetworkVoiceResponseText.format(result)
        is VoiceActionResult.AiAdvice -> AiAdviceFormatter.fullResponse(context, result.advice)
        VoiceActionResult.Help ->
            "Puedes decir: Ultra, dime la hora; Ultra, abre un juego; Ultra, pon X4; Ultra, háblame de los osos; Ultra, háblame de Nike; Ultra, cuéntame sobre un tema; o Ultra, dime qué sabes de algo."
        is VoiceActionResult.NotAvailable -> "No disponible. ${result.detail}"
        VoiceActionResult.RequiresPermission -> "Necesito permiso de micrófono."
        is VoiceActionResult.Failed -> "No pude completar el comando. ${result.detail}"
    }

    private fun voiceThermalLabel(status: Int?): String = when (status) {
        0 -> "normal"
        1 -> "ligera"
        2 -> "moderada"
        3 -> "alta"
        4 -> "severa"
        5 -> "crítica"
        6 -> "emergencia"
        7 -> "apagado"
        else -> "no disponible"
    }

    private fun launchGame(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    private fun reportSelectionPersistenceFailure(error: Throwable) {
        android.util.Log.e("UltraWakeService", "Selection persistence failed", error)
    }
}

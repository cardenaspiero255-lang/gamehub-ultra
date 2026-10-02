package com.cardenaspiero255.gamehubultra.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardenaspiero255.gamehubultra.*
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.ai.*
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CHAT_HISTORY = 20

@Composable
internal fun VoiceAssistantCard(
    selectedProfileName: String,
    onProfileSelected: (PerformanceProfile) -> Unit,
    onGameSelected: (String) -> Unit,
    onVoiceSelectedGame: (String) -> Unit,
    onVoiceSelectedProfile: (PerformanceProfile) -> Unit,
    onVoiceSelectedGameWithProfile: (String, PerformanceProfile) -> Unit,
    aiContext: GameHubAiContext,
    ultraRuntime: UltraUiRuntimeDependencies,
    queryRunner: UltraAssistantQueryRunner,
    conversation: List<String>,
    onConversationChanged: (List<String>) -> Unit,
    assistantInputEnabled: Boolean,
    startListeningRequest: Int = 0,
    onListeningRequestConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aliasRepository = ultraRuntime.aliasRepository
    val aiAdvisor = ultraRuntime.assistant
    val queryExecutor = ultraRuntime.queryExecutor
    val agentRouter = ultraRuntime.agentRouter
    val networkGaming = ultraRuntime.networkGaming
    val continuousVoiceController = remember(context) {
        ContinuousVoiceController(
            AndroidContinuousVoiceGateway(context)
        )
    }
    var continuousListeningEnabled by remember {
        mutableStateOf(continuousVoiceController.isEnabled())
    }
    val aiIntentResolver = remember(aiAdvisor) { aiAdvisor.intentResolver() }
    val latestAiContext by rememberUpdatedState(aiContext)
    val latestConversation by rememberUpdatedState(conversation)
    val latestOnConversationChanged by rememberUpdatedState(onConversationChanged)
    val latestAssistantInputEnabled by rememberUpdatedState(assistantInputEnabled)
    var listening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var response by rememberSaveable { mutableStateOf<String?>(null) }
    var recommendedProfile by remember(aiContext.selectedGamePackage) {
        mutableStateOf<UltraScopedProfileRecommendation?>(null)
    }
    var pendingContinuousListening by rememberSaveable { mutableStateOf(false) }
    var pendingSingleListening by rememberSaveable { mutableStateOf(false) }
    var showTextChat by rememberSaveable { mutableStateOf(false) }
    var chatMessage by rememberSaveable { mutableStateOf("") }
    val chatSending by queryRunner.isRunning.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    fun sendTypedChatMessage() {
        val message = chatMessage.trim()
        if (!latestAssistantInputEnabled || message.isBlank() || chatSending) return

        val turnAiContext = latestAiContext
        val originatingGamePackage = turnAiContext.selectedGamePackage
        val conversationAtStart = latestConversation
        val previousConversation = conversationAtStart.takeLast(MAX_CHAT_HISTORY - 1)
        val resetConversationAfterCommand =
            UltraMemoryTurnPersistencePolicy.resetsConversationContext(message)
        val withUser = UltraConversationPolicy.append(
            history = conversationAtStart,
            entry = "Tú: " + message,
            maxEntries = MAX_CHAT_HISTORY
        )

        val failureAnswer = "No pude completar la consulta. Inténtalo de nuevo."
        val submission = queryRunner.launch(
            onAccepted = {
                chatMessage = ""
                latestOnConversationChanged(withUser)
            },
            onFailure = {
                val published = queryRunner.appendAssistantIfCurrentGame(
                    originatingGamePackage = originatingGamePackage,
                    assistantEntry = "Ultra: " + failureAnswer,
                    maxEntries = MAX_CHAT_HISTORY
                )
                if (published) {
                    withContext(Dispatchers.Main) {
                        response = failureAnswer
                    }
                }
            }
        ) {
            val deviceStatus = VoiceDeviceStatusProvider.read(context)
            val route = agentRouter.route(
                UltraAgentRoutingRequest(
                    transcript = message.trim(),
                    conversationHistory = previousConversation,
                    optionalResolver = aiIntentResolver,
                    telemetry = UltraRuntimeTelemetry(
                        batteryPercent = deviceStatus.batteryPercent,
                        thermalLabel = deviceStatus.thermalLabel,
                        refreshRateHz = turnAiContext.refreshRateHz
                    ),
                    knownGameAliases = aliasRepository.aliases().keys
                )
            )

            when (route) {
                is UltraAgentRoute.Utility -> {
                    val answer = UltraUtilityRuntimeExecutor.executeIfCurrent(
                        answer = route.answer,
                        networkGaming = networkGaming,
                        isCurrent = {
                            latestAiContext.selectedGamePackage == originatingGamePackage
                        }
                    ) ?: return@launch
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    if (published) {
                        withContext(Dispatchers.Main) {
                            response = answer
                        }
                    }
                }

                is UltraAgentRoute.Chat -> {
                    val answer = queryExecutor.answer(
                        route = route,
                        stableKnowledgeFallback = {
                            aiAdvisor.generalKnowledgeChatOrNull(
                                message = route.message,
                                context = turnAiContext,
                                conversation = previousConversation
                            )
                        }
                    ) {
                        aiAdvisor.chat(
                            message = route.message,
                            context = turnAiContext,
                            conversation = previousConversation
                        )
                    }
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY,
                        resetConversation = resetConversationAfterCommand
                    )
                    if (published) {
                        withContext(Dispatchers.Main) {
                            response = answer
                        }
                    }
                }

                is UltraAgentRoute.Command -> {
                    val result = VoiceCommandEngine.execute(
                        command = route.command,
                        gamesProvider = { GameLibrary.discover(context).games },
                        launchGame = { packageName ->
                            GameLauncher.launch(context, packageName)
                        },
                        saveSelectedGame = onVoiceSelectedGame,
                        saveSelectedProfile = onVoiceSelectedProfile,
                        saveSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                        isProfileAvailable = { _ -> true },
                        statusProvider = { VoiceDeviceStatusProvider.read(context) },
                        aiAdvisor = { question ->
                            aiAdvisor.advise(question, latestAiContext)
                        },
                        aliasIntentResolver = aiIntentResolver,
                        gameAliasesProvider = { aliasRepository.aliases() },
                        saveGameAlias = { alias, packageName ->
                            aliasRepository.save(alias, packageName)
                        },
                        networkStatusProvider = {
                            VoiceNetworkSnapshotFactory.current(context)
                        },
                        applyNetworkProfile = networkGaming::applyProfile
                    )
                    val answer = VoiceResponseFormatter.format(context, result)
                    val published = queryRunner.appendAssistantIfCurrentGame(
                        originatingGamePackage = originatingGamePackage,
                        assistantEntry = "Ultra: " + answer,
                        maxEntries = MAX_CHAT_HISTORY
                    )

                    if (published) {
                        withContext(Dispatchers.Main) {
                            if (result is VoiceActionResult.GameOpened) {
                                onGameSelected(result.game.packageName)
                            }
                            UltraCommandUiEffectPolicy
                                .profileForCurrentGameCallback(result)
                                ?.let(onProfileSelected)
                            recommendedProfile =
                                UltraCommandUiEffectPolicy.recommendationForUserApply(
                                    result = result,
                                    gamePackage = originatingGamePackage
                                )
                            response = answer
                        }
                    }
                }
            }
        }
        if (submission is UltraAssistantQuerySubmission.Rejected) {
            return
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted && pendingContinuousListening) {
            pendingContinuousListening = false
            when (continuousVoiceController.enableAfterPermissionGranted()) {
                ContinuousVoiceChange.ENABLED -> {
                    continuousListeningEnabled = true
                    response = "Escucha continua activada."
                }
                ContinuousVoiceChange.PERMISSION_REQUIRED -> {
                    continuousListeningEnabled = false
                    response = context.getString(R.string.voice_permission_required)
                }
                ContinuousVoiceChange.DISABLED -> Unit
            }
        } else if (!granted) {
            pendingContinuousListening = false
            pendingSingleListening = false
            continuousListeningEnabled = continuousVoiceController.isEnabled()
            response = context.getString(R.string.voice_permission_required)
        }
    }

    val voiceController = remember(context) {
        lateinit var controller: VoiceAssistantController
        controller = VoiceAssistantController(
            context = context,
            onListeningChanged = { listening = it },
            onTranscript = transcript@{ spokenText ->
                val turnAiContext = latestAiContext
                val capturedVoiceScope = captureUltraVoiceTurnScope(
                    assistantInputReady = latestAssistantInputEnabled,
                    gamePackage = turnAiContext.selectedGamePackage
                ) ?: return@transcript
                val originatingGamePackage = capturedVoiceScope.gamePackage
                transcript = spokenText
                scope.launch(Dispatchers.IO) {
                    if (
                        !isUltraVoiceTurnScopeCurrent(
                            captured = capturedVoiceScope,
                            assistantInputReady = latestAssistantInputEnabled,
                            currentGamePackage = latestAiContext.selectedGamePackage
                        )
                    ) {
                        return@launch
                    }
                    val conversationAtStart = latestConversation
                    val conversationBeforeTurn =
                        conversationAtStart.takeLast(MAX_CHAT_HISTORY - 1)
                    val withUser = UltraConversationPolicy.append(
                        history = conversationAtStart,
                        entry = "Tú: " + spokenText,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (
                            UltraConversationScopePolicy.isSameGame(
                                originatingGamePackage,
                                latestAiContext.selectedGamePackage
                            )
                        ) {
                            latestOnConversationChanged(withUser)
                        }
                    }

                    val voiceStatus = VoiceDeviceStatusProvider.read(context)
                    val route = agentRouter.route(
                        UltraAgentRoutingRequest(
                            transcript = spokenText,
                            optionalResolver = aiIntentResolver,
                            telemetry = UltraRuntimeTelemetry(
                                batteryPercent = voiceStatus.batteryPercent,
                                thermalLabel = voiceStatus.thermalLabel,
                                refreshRateHz = turnAiContext.refreshRateHz
                            ),
                            knownGameAliases = aliasRepository.aliases().keys,
                            conversationHistory = conversationBeforeTurn
                        )
                    )
                    when (route) {
                        is UltraAgentRoute.Utility -> {
                            val answer = UltraUtilityRuntimeExecutor.executeIfCurrent(
                                answer = route.answer,
                                networkGaming = networkGaming,
                                isCurrent = {
                                    isUltraVoiceTurnScopeCurrent(
                                        captured = capturedVoiceScope,
                                        assistantInputReady = latestAssistantInputEnabled,
                                        currentGamePackage = latestAiContext.selectedGamePackage
                                    )
                                }
                            ) ?: return@launch
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + answer,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    latestOnConversationChanged(withAnswer)
                                    response = answer
                                    controller.speak(answer)
                                }
                            }
                        }

                        is UltraAgentRoute.Chat -> {
                            val answer = queryExecutor.answer(
                                route = route,
                                stableKnowledgeFallback = {
                                    aiAdvisor.generalKnowledgeChatOrNull(
                                        message = route.message,
                                        context = turnAiContext,
                                        conversation = conversationBeforeTurn
                                    )
                                }
                            ) {
                                aiAdvisor.chat(
                                    message = route.message,
                                    context = turnAiContext,
                                    conversation = conversationBeforeTurn
                                )
                            }
                            val withAnswer =
                                if (
                                    UltraMemoryTurnPersistencePolicy
                                        .resetsConversationContext(route.message)
                                ) {
                                    listOf("Ultra: " + answer)
                                } else {
                                    UltraConversationPolicy.append(
                                        history = withUser,
                                        entry = "Ultra: " + answer,
                                        maxEntries = MAX_CHAT_HISTORY
                                    )
                                }
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    latestOnConversationChanged(withAnswer)
                                    response = answer
                                    controller.speak(answer)
                                }
                            }
                        }

                        is UltraAgentRoute.Command -> {
                            val result = VoiceCommandEngine.execute(
                                command = route.command,
                                gamesProvider = { GameLibrary.discover(context).games },
                                launchGame = { packageName ->
                                    GameLauncher.launch(context, packageName)
                                },
                                saveSelectedGame = onVoiceSelectedGame,
                                saveSelectedProfile = onVoiceSelectedProfile,
                                saveSelectedGameWithProfile = onVoiceSelectedGameWithProfile,
                                // X4 is an app profile. Hardware Sustained Performance Mode is
                                // applied opportunistically by PerformanceController when supported.
                                isProfileAvailable = { _ -> true },
                                statusProvider = { VoiceDeviceStatusProvider.read(context) },
                                aiAdvisor = { question ->
                                    aiAdvisor.advise(question, latestAiContext)
                                },
                                aliasIntentResolver = aiIntentResolver,
                                gameAliasesProvider = { aliasRepository.aliases() },
                                saveGameAlias = { alias, packageName ->
    aliasRepository.save(alias, packageName)
},
networkStatusProvider = { VoiceNetworkSnapshotFactory.current(context) },
applyNetworkProfile = networkGaming::applyProfile
                            )
                            val spokenResponse = VoiceResponseFormatter.format(context, result)
                            val withAnswer = UltraConversationPolicy.append(
                                history = withUser,
                                entry = "Ultra: " + spokenResponse,
                                maxEntries = MAX_CHAT_HISTORY
                            )
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (result is VoiceActionResult.GameOpened) {
                                    onGameSelected(result.game.packageName)
                                }
                                UltraCommandUiEffectPolicy
                                    .profileForCurrentGameCallback(result)
                                    ?.let(onProfileSelected)
                                val recommended =
                                    UltraCommandUiEffectPolicy.recommendationForUserApply(
                                        result = result,
                                        gamePackage = originatingGamePackage
                                    )
                                UltraVoiceResultPublisher.publishIfCurrentGame(
                                    originatingGamePackage = originatingGamePackage,
                                    currentGamePackage = latestAiContext.selectedGamePackage
                                ) {
                                    recommendedProfile = recommended
                                    latestOnConversationChanged(withAnswer)
                                    response = spokenResponse
                                    controller.speak(spokenResponse)
                                }
                            }
                        }
                    }
                }
            },
            onError = {
                response = context.getString(R.string.voice_recognition_error)
            }
        )
        controller
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    LaunchedEffect(startListeningRequest, assistantInputEnabled) {
        if (startListeningRequest <= 0 || !assistantInputEnabled) return@LaunchedEffect
        if (permissionGranted) {
            voiceController.startListening()
        } else {
            pendingSingleListening = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onListeningRequestConsumed()
    }

    LaunchedEffect(permissionGranted, pendingSingleListening, assistantInputEnabled) {
        if (permissionGranted && pendingSingleListening && assistantInputEnabled) {
            pendingSingleListening = false
            voiceController.startListening()
        }
    }

    if (showTextChat) {
        AlertDialog(
            onDismissRequest = { if (!chatSending) showTextChat = false },
            title = { Text("Ultra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        latestConversation.takeLast(MAX_CHAT_HISTORY).forEach { entry ->
                            Text(entry, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = chatMessage,
                        onValueChange = { chatMessage = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_chat_input_label)) },
                        placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                        enabled = assistantInputEnabled && !chatSending,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = ::sendTypedChatMessage,
                    enabled = assistantInputEnabled && chatMessage.isNotBlank() && !chatSending
                ) {
                    Text(
                        if (chatSending) {
                            stringResource(R.string.ai_chat_thinking)
                        } else {
                            stringResource(R.string.ai_chat_send)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        latestOnConversationChanged(emptyList())
                        chatMessage = ""
                    },
                    enabled = !chatSending
                ) {
                    Text(stringResource(R.string.ai_chat_clear))
                }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.voice_assistant_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.voice_assistant_subtitle))
            if (!assistantInputEnabled) {
                Text(
                    "Preparando el contexto de Ultra…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = { showTextChat = true },
                enabled = assistantInputEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ai_chat_input_label))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Escucha continua", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Responde solo cuando digas “Ultra”.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = continuousListeningEnabled,
                    enabled = assistantInputEnabled,
                    onCheckedChange = { enabled ->
                        when (continuousVoiceController.setEnabled(enabled)) {
                            ContinuousVoiceChange.PERMISSION_REQUIRED -> {
                                pendingContinuousListening = true
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            ContinuousVoiceChange.ENABLED -> {
                                pendingContinuousListening = false
                                continuousListeningEnabled = true
                                response = "Escucha continua activada."
                            }
                            ContinuousVoiceChange.DISABLED -> {
                                pendingContinuousListening = false
                                continuousListeningEnabled = false
                                response = "Escucha continua desactivada."
                            }
                        }
                    }
                )
            }
            Button(
                onClick = {
                    if (permissionGranted) {
                        if (listening) {
                            voiceController.stopListening()
                        } else {
                            voiceController.startListening()
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = assistantInputEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        listening -> stringResource(R.string.voice_stop)
                        permissionGranted -> stringResource(R.string.voice_start)
                        else -> stringResource(R.string.voice_permission_button)
                    }
                )
            }
            Text(
                stringResource(
                    R.string.voice_selected_profile,
                    selectedProfileName
                )
            )
            if (transcript.isNotBlank()) {
                Text(stringResource(R.string.voice_transcript, transcript))
            }
            response?.let { Text(it) }
            UltraCommandUiEffectPolicy.profileForCurrentGame(
                recommendation = recommendedProfile,
                currentGamePackage = aiContext.selectedGamePackage
            )?.let { profile ->
                Button(
                    onClick = {
                        onProfileSelected(profile)
                        recommendedProfile = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aplicar recomendación: ${profile.title}")
                }
            }
        }
    }
}
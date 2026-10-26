package com.cardenaspiero255.gamehubultra.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardenaspiero255.gamehubultra.*
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.ai.*
import com.cardenaspiero255.gamehubultra.voice.UltraPrivacyEpoch
import com.cardenaspiero255.gamehubultra.vision.UltraVisualDraftPolicy
import com.cardenaspiero255.gamehubultra.vision.UltraVisualReadControl
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.ui.runtime.UltraUiRuntimeDependencies
import com.cardenaspiero255.gamehubultra.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_CHAT_HISTORY = 20

internal fun presentVoiceTranscript(value: String): String =
    value
        .replace(Regex("""(?i)\btik\s*tok\b"""), "TikTok")
        .replace(Regex("""(?i)\byou\s*tube\b"""), "YouTube")
        .replace(Regex("""(?i)\bwhats\s*app\b"""), "WhatsApp")

/**
 * Share one lifecycle-bound listener with any Compose host that displays
 * continuous microphone state. Keeping it separate permits real Compose
 * regression tests without instantiating the entire assistant card.
 */
@Composable
internal fun ContinuousVoiceStateObserver(
    gateway: AndroidContinuousVoiceGateway,
    onChanged: (Boolean) -> Unit
) {
    val latestOnChanged by rememberUpdatedState(onChanged)
    DisposableEffect(gateway) {
        val unsubscribe = gateway.observeEnabled { latestOnChanged(it) }
        onDispose { unsubscribe() }
    }
}

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
    // Internal-only seams for hermetic Compose privacy regressions.
    visualPickerOverride: (((android.net.Uri?) -> Unit) -> Unit)? = null,
    visualOcrOverride: (suspend (android.content.Context, android.net.Uri, UltraVisualReadControl, android.os.CancellationSignal) -> String)? = null,
    // Optional observation seam: tests drive the real microphone recognition listener.
    voiceControllerObserver: ((VoiceAssistantController) -> Unit)? = null
) {
    val context = LocalContext.current
    var backgroundLaunchAllowed by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                Settings.canDrawOverlays(context)
        )
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        backgroundLaunchAllowed =
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                Settings.canDrawOverlays(context)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val aliasRepository = ultraRuntime.aliasRepository
    val aiAdvisor = ultraRuntime.assistant
    val queryExecutor = ultraRuntime.queryExecutor
    val agentRouter = ultraRuntime.agentRouter
    val networkGaming = ultraRuntime.networkGaming
    val continuousVoiceGateway = remember(context) { AndroidContinuousVoiceGateway(context) }
    val continuousVoiceController = remember(continuousVoiceGateway) {
        ContinuousVoiceController(continuousVoiceGateway)
    }
    var continuousListeningEnabled by remember(continuousVoiceController) {
        mutableStateOf(continuousVoiceController.isEnabled())
    }
    val aiIntentResolver = remember(aiAdvisor) { aiAdvisor.intentResolver() }
    val latestAiContext by rememberUpdatedState(aiContext)
    val latestConversation by rememberUpdatedState(conversation)
    val latestOnConversationChanged by rememberUpdatedState(onConversationChanged)
    val latestAssistantInputEnabled by rememberUpdatedState(assistantInputEnabled)
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<String?>(null) }

    // Android may reject startForeground after startForegroundService succeeded.
    // Observe the service-owned preference rather than treating the initial tap
    // result as permanent proof that microphone capture is running.
    ContinuousVoiceStateObserver(continuousVoiceGateway) { enabled ->
        val previouslyEnabled = continuousListeningEnabled
        continuousListeningEnabled = enabled
        if (previouslyEnabled && !enabled) {
            response = "La escucha continua se detuvo. Revisa los permisos del micrófono y las restricciones del sistema."
        }
    }
    var recommendedProfile by remember(aiContext.selectedGamePackage) {
        mutableStateOf<UltraScopedProfileRecommendation?>(null)
    }
    var pendingContinuousListening by rememberSaveable { mutableStateOf(false) }
    var pendingOneShotListening by rememberSaveable { mutableStateOf(false) }
    var lastHandledStartRequest by remember { mutableIntStateOf(0) }
    var showTextChat by rememberSaveable { mutableStateOf(false) }
    var showMemoryPrivacy by rememberSaveable { mutableStateOf(false) }
    var showEraseMemoryConfirmation by rememberSaveable { mutableStateOf(false) }
    var memoryPrivacyBusy by remember { mutableStateOf(false) }
    var memoryPrivacyNotice by remember { mutableStateOf<String?>(null) }
    val privateMemoryStore = ultraRuntime.memoryPrivacy
    var memoryConsent by remember { mutableStateOf(false) }
    var visualResetKey by remember { mutableIntStateOf(0) }
    var memoryRetentionDays by remember { mutableIntStateOf(30) }
    var chatMessage by remember { mutableStateOf("") }
    var visualDraftOrigin by remember { mutableStateOf<UltraVisualDraftPolicy.Origin?>(null) }
    val privacyRevision by UltraPrivacyEpoch.changes.collectAsState()
    var activeVoiceTurn by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val chatSending by queryRunner.isRunning.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(privacyRevision, aiContext.selectedGamePackage) {
        // A StateFlow emission can be observed one composition behind the
        // atomic epoch when a new OCR draft is prepared at the same time.
        // Use the authoritative epoch for both revocation and send-time gates.
        if (!UltraVisualDraftPolicy.canSend(
                visualDraftOrigin, UltraPrivacyEpoch.current(), aiContext.selectedGamePackage
            )
        ) {
            chatMessage = ""
            visualDraftOrigin = null
            showTextChat = false
            visualResetKey++
        }
    }

    fun sendTypedChatMessage() {
        // Capture the OCR origin ONCE; a reactive erase must not turn the
        // same private message into an unrestricted ordinary typed request.
        val draftOriginAtSend = visualDraftOrigin
        // Observe privacy and game provenance again synchronously at send-time;
        // a Compose recomposition may not yet have processed an external erase.
        if (!UltraVisualDraftPolicy.canSend(
                draftOriginAtSend,
                UltraPrivacyEpoch.current(),
                latestAiContext.selectedGamePackage
            )
        ) {
            chatMessage = ""
            visualDraftOrigin = null
            showTextChat = false
            return
        }
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

        // Identity commands are local, deterministic settings operations.
        // Resolve them at the user action boundary, before asynchronous AI
        // dispatch; no provider, game launcher or network dependency is needed.
        val localIdentityAnswer = UltraWakeLocalIdentityActions.handle(context, message)
        if (localIdentityAnswer != null) {
            if (!UltraPrivacyEpoch.isCurrent(
                    draftOriginAtSend?.privacyEpoch ?: UltraPrivacyEpoch.current()
                ) || latestAiContext.selectedGamePackage != originatingGamePackage
            ) return
            chatMessage = ""
            visualDraftOrigin = null
            latestOnConversationChanged(
                UltraConversationPolicy.append(
                    history = withUser,
                    entry = "Ultra: " + localIdentityAnswer,
                    maxEntries = MAX_CHAT_HISTORY
                )
            )
            response = localIdentityAnswer
            return
        }

        val failureAnswer = "No pude completar la consulta. Inténtalo de nuevo."
        val submission = queryRunner.launch(
            // Runner rechecks this immutable consent epoch during admission and
            // immediately before external query dispatch.
            expectedPrivacyEpoch = draftOriginAtSend?.privacyEpoch,
            onAccepted = {
                chatMessage = ""
                visualDraftOrigin = null
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
            queryRunner.requireCurrentDispatch(originatingGamePackage)
            val route = agentRouter.route(
                UltraAgentRoutingRequest(
                    transcript = UltraWakeLocalIdentityActions.canonicalize(context, message),
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

            // Routing can suspend; revocation during that interval must
            // terminate the turn before any provider or command side effect.
            queryRunner.requireCurrentDispatch(originatingGamePackage)
            when (route) {
                is UltraAgentRoute.Utility -> {
                    queryRunner.requireCurrentDispatch(originatingGamePackage)
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
                    queryRunner.requireCurrentDispatch(originatingGamePackage)
                    // The research engine may spawn provider workers and retry.
                    // Carry the original epoch through request.copy() so those
                    // workers can recheck it at each provider attempt.
                    val consentBoundRoute = if (draftOriginAtSend == null) route else {
                        route.copy(query = route.query?.copy(
                            privacyEpoch = draftOriginAtSend.privacyEpoch
                        ))
                    }
                    // Device status and route construction can suspend or race erasure:
                    // fence once more at the actual remote/provider dispatch boundary.
                    queryRunner.requireCurrentDispatch(originatingGamePackage)
                    val answer = queryExecutor.answer(
                        route = consentBoundRoute,
                        stableKnowledgeFallback = {
                            queryRunner.requireCurrentDispatch(originatingGamePackage)
                            aiAdvisor.generalKnowledgeChatOrNull(
                                message = route.message,
                                context = turnAiContext,
                                conversation = previousConversation
                            )
                        }
                    ) {
                        queryRunner.requireCurrentDispatch(originatingGamePackage)
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
                    queryRunner.requireCurrentDispatch(originatingGamePackage)
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
                            queryRunner.requireCurrentDispatch(originatingGamePackage)
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
                ContinuousVoiceChange.SYSTEM_RESTRICTED -> {
                    continuousListeningEnabled = false
                    response = "La escucha continua no está disponible con las restricciones actuales del sistema."
                }
                ContinuousVoiceChange.DISABLED -> Unit
            }
        } else if (!granted) {
            pendingContinuousListening = false
            pendingOneShotListening = false
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
                val turnPrivacyEpoch = UltraPrivacyEpoch.current()
                transcript = spokenText
                if (
                    !UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch) ||
                    !isUltraVoiceTurnScopeCurrent(
                        captured = capturedVoiceScope,
                        assistantInputReady = latestAssistantInputEnabled,
                        currentGamePackage = latestAiContext.selectedGamePackage
                    )
                ) return@transcript
                // No background coroutine or LLM is required to rename Ultra or
                // answer its current name. Use the same local handler as chat.
                val localIdentityAnswer = UltraWakeLocalIdentityActions.handle(context, spokenText)
                if (localIdentityAnswer != null) {
                    val withUser = UltraConversationPolicy.append(
                        history = latestConversation,
                        entry = "Tú: " + spokenText,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    val withAnswer = UltraConversationPolicy.append(
                        history = withUser,
                        entry = "Ultra: " + localIdentityAnswer,
                        maxEntries = MAX_CHAT_HISTORY
                    )
                    if (UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch)) {
                        UltraVoiceResultPublisher.publishIfCurrentGame(
                            originatingGamePackage = originatingGamePackage,
                            currentGamePackage = latestAiContext.selectedGamePackage
                        ) {
                            latestOnConversationChanged(withAnswer)
                            response = localIdentityAnswer
                            controller.speak(localIdentityAnswer)
                        }
                    }
                    return@transcript
                }
                activeVoiceTurn = scope.launch(Dispatchers.IO) {
                    if (
                        !UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch) ||
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
                            UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch) &&
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
                            transcript = UltraWakeLocalIdentityActions.canonicalize(context, spokenText),
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
                                    UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch) && isUltraVoiceTurnScopeCurrent(
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
                                if (UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch)) UltraVoiceResultPublisher.publishIfCurrentGame(
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
                                if (UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch)) UltraVoiceResultPublisher.publishIfCurrentGame(
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
                                if (UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch) && result is VoiceActionResult.GameOpened) {
                                    onGameSelected(result.game.packageName)
                                }
                                if (!UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch)) return@withContext
                                UltraCommandUiEffectPolicy
                                    .profileForCurrentGameCallback(result)
                                    ?.let(onProfileSelected)
                                val recommended =
                                    UltraCommandUiEffectPolicy.recommendationForUserApply(
                                        result = result,
                                        gamePackage = originatingGamePackage
                                    )
                                if (UltraPrivacyEpoch.isCurrent(turnPrivacyEpoch)) UltraVoiceResultPublisher.publishIfCurrentGame(
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
    SideEffect { voiceControllerObserver?.invoke(voiceController) }

    fun requestOneShotListening() {
        if (!latestAssistantInputEnabled || listening) return
        if (permissionGranted) {
            voiceController.startListening()
        } else {
            pendingOneShotListening = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(startListeningRequest, assistantInputEnabled) {
        if (
            assistantInputEnabled &&
            startListeningRequest > lastHandledStartRequest
        ) {
            lastHandledStartRequest = startListeningRequest
            requestOneShotListening()
        }
    }

    LaunchedEffect(permissionGranted, pendingOneShotListening) {
        if (permissionGranted && pendingOneShotListening) {
            pendingOneShotListening = false
            requestOneShotListening()
        }
    }

    DisposableEffect(voiceController) {
        onDispose { voiceController.release() }
    }

    fun invalidatePrivateTurns() {
        UltraPrivacyEpoch.invalidate()
        visualResetKey++
        visualDraftOrigin = null
        queryRunner.cancelActive()
        activeVoiceTurn?.cancel()
        voiceController.stopListening()
        transcript = ""
        response = null
        chatMessage = ""
    }

    // Preserve the settings dialog while an independent confirmation is displayed.
    // The confirmation overlays the settings; cancelling only closes the top dialog.
    if (showMemoryPrivacy) {
        AlertDialog(
            onDismissRequest = { if (!memoryPrivacyBusy) showMemoryPrivacy = false },
            title = { Text("Memoria privada de Ultra") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Desactivada por defecto. Solo se guardan conversaciones locales cifradas tras tu autorización. " +
                            "Cada juego conserva su propio contexto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Guardar recuerdos", modifier = Modifier.weight(1f))
                        Switch(
                            modifier = Modifier.testTag("ultra-memory-consent-toggle"),
                            checked = memoryConsent,
                            enabled = !memoryPrivacyBusy,
                            onCheckedChange = { enabled ->
                                if (!enabled) invalidatePrivateTurns()
                                memoryPrivacyBusy = true
                                scope.launch(Dispatchers.Main.immediate) {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { privateMemoryStore.setMemoryEnabled(enabled) }
                                    }
                                    memoryPrivacyBusy = false
                                    if (result.isSuccess) {
                                        memoryConsent = enabled
                                        if (!enabled) latestOnConversationChanged(emptyList())
                                        memoryPrivacyNotice =
                                            if (enabled) "Memoria activada con tu consentimiento."
                                            else "Memoria desactivada; las conversaciones guardadas no se consultarán."
                                    } else {
                                        memoryPrivacyNotice = "No fue posible actualizar el consentimiento."
                                    }
                                }
                            }
                        )
                    }
                    Text("Conservar conversaciones durante:", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(7, 30, 90).forEach { days ->
                            FilterChip(
                                selected = memoryRetentionDays == days,
                                onClick = {
                                    memoryPrivacyBusy = true
                                    scope.launch(Dispatchers.Main.immediate) {
                                        val updated = withContext(Dispatchers.IO) {
                                            runCatching {
                                                privateMemoryStore.setConversationRetentionDays(days)
                                            }.getOrDefault(false)
                                        }
                                        if (updated) {
                                            memoryRetentionDays = days
                                            memoryPrivacyNotice = "Retención de conversaciones: $days días."
                                        } else {
                                            memoryPrivacyNotice = "No se pudo cambiar la retención."
                                        }
                                        memoryPrivacyBusy = false
                                    }
                                },
                                enabled = !memoryPrivacyBusy,
                                label = { Text("$days días") }
                            )
                        }
                    }
                    Text(
                        "Los recuerdos guardados explícitamente no caducan con el historial. " +
                            "Puedes borrar el historial del juego actual o eliminar toda la memoria.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        enabled = !memoryPrivacyBusy,
                        onClick = {
                            invalidatePrivateTurns()
                            memoryPrivacyBusy = true
                            val selectedGame = latestAiContext.selectedGamePackage
                            scope.launch(Dispatchers.Main.immediate) {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        privateMemoryStore.clearConversationHistory(
                                            UltraMemoryScope(userId = "local", gamePackage = selectedGame)
                                        )
                                    }
                                }
                                memoryPrivacyBusy = false
                                if (result.isSuccess) {
                                    latestOnConversationChanged(emptyList())
                                    memoryPrivacyNotice = "Historial del juego actual borrado."
                                } else {
                                    memoryPrivacyNotice = "No pude borrar el historial."
                                }
                            }
                        }
                    ) { Text("Borrar historial del juego") }
                    TextButton(
                        enabled = !memoryPrivacyBusy,
                        onClick = { showEraseMemoryConfirmation = true }
                    ) { Text("Borrar toda mi memoria") }
                    memoryPrivacyNotice?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMemoryPrivacy = false },
                    enabled = !memoryPrivacyBusy
                ) { Text("Cerrar") }
            }
        )
    }

    if (showMemoryPrivacy && showEraseMemoryConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!memoryPrivacyBusy) showEraseMemoryConfirmation = false
            },
            title = { Text("Confirmar borrado de memoria") },
            text = {
                Text("Se borrarán conversaciones y recuerdos de todos los juegos. ¿Confirmas?")
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("ultra-erase-confirm"),
                    enabled = !memoryPrivacyBusy,
                    onClick = {
                        invalidatePrivateTurns()
                        memoryPrivacyBusy = true
                        scope.launch(Dispatchers.Main.immediate) {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { privateMemoryStore.deleteAllPrivateMemory() }
                            }
                            memoryPrivacyBusy = false
                            showEraseMemoryConfirmation = false
                            if (result.isSuccess) {
                                memoryConsent = false
                                latestOnConversationChanged(emptyList())
                                memoryPrivacyNotice =
                                    "Memoria cifrada eliminada y consentimiento revocado."
                            } else {
                                memoryPrivacyNotice = "No pude borrar toda la memoria."
                            }
                        }
                    }
                ) { Text("Borrar definitivamente") }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("ultra-erase-cancel"),
                    enabled = !memoryPrivacyBusy,
                    onClick = { showEraseMemoryConfirmation = false }
                ) { Text("Cancelar") }
            }
        )
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
                        onValueChange = {
                            chatMessage = it.take(1000)
                            // Continue treating edited OCR text as private until the user sends.
                        },
                        modifier = Modifier.fillMaxWidth().testTag("ultra-chat-private-draft"),
                        label = { Text(stringResource(R.string.ai_chat_input_label)) },
                        placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                        enabled = assistantInputEnabled && !chatSending,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("ultra-chat-send"),
                    onClick = ::sendTypedChatMessage,
                    enabled = assistantInputEnabled && chatMessage.isNotBlank() && !chatSending &&
                        UltraVisualDraftPolicy.canSend(
                            visualDraftOrigin, UltraPrivacyEpoch.current(), aiContext.selectedGamePackage
                        )
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
                        visualDraftOrigin = null
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showTextChat = true },
                    enabled = assistantInputEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Escribir")
                }
                Button(
                    onClick = {
                        if (listening) {
                            voiceController.stopListening()
                        } else {
                            requestOneShotListening()
                        }
                    },
                    enabled = assistantInputEnabled || listening,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        when {
                            listening -> "Detener"
                            permissionGranted -> "Hablar"
                            else -> "Micrófono"
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Escucha continua", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Responde cuando digas Ultra o el nombre personalizado que elegiste.",
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
                            ContinuousVoiceChange.SYSTEM_RESTRICTED -> {
                                pendingContinuousListening = false
                                continuousListeningEnabled = false
                                response = "La escucha continua no está disponible con las restricciones actuales del sistema."
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
            if (continuousListeningEnabled) {
                Text(
                    "Fuera de GameHub, Android puede impedir abrir juegos " +
                        "automáticamente. En ese caso Ultra envía una " +
                        "notificación que debes tocar. En Android 14 la apertura " +
                        "automática opcional usa el permiso especial " +
                        "«Mostrar sobre otras aplicaciones», que también " +
                        "permite superposiciones. Solo se solicita si lo eliges.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (backgroundLaunchAllowed) {
                        Text(
                            "Permiso opcional de apertura desde segundo plano concedido. " +
                                "Android todavía puede restringir algunas aperturas.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        TextButton(
                            onClick = {
                                runCatching {
                                    overlayPermissionLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }.onFailure {
                                    response = "No pude abrir los ajustes de permisos. " +
                                        "Puedes seguir usando la notificación para abrir juegos."
                                }
                            }
                        ) {
                            Text("Conceder «Mostrar sobre otras aplicaciones» (opcional)")
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    if (!memoryPrivacyBusy) {
                        showEraseMemoryConfirmation = false
                        memoryPrivacyNotice = null
                        memoryPrivacyBusy = true
                        scope.launch(Dispatchers.Main.immediate) {
                            val settings = withContext(Dispatchers.IO) {
                                runCatching {
                                    privateMemoryStore.isMemoryEnabled() to
                                        privateMemoryStore.conversationRetentionDays()
                                }
                            }
                            memoryPrivacyBusy = false
                            if (settings.isSuccess) {
                                memoryConsent = settings.getOrThrow().first
                                memoryRetentionDays = settings.getOrThrow().second
                                showMemoryPrivacy = true
                            } else {
                                response = "No pude cargar los controles de privacidad."
                            }
                        }
                    }
                }
            ) { Text("Privacidad y memoria") }
            key(visualResetKey) {
                UltraVoluntaryVisualCard(
                    gamePackage = aiContext.selectedGamePackage,
                    enabled = assistantInputEnabled,
                    pickerOverride = visualPickerOverride,
                    ocrOverride = visualOcrOverride,
                    onUseRecognizedText = { extracted, authorizedOrigin ->
                        // Never restamp old OCR bytes with a newly observed epoch.
                        // The child supplies the immutable scope captured at picker consent.
                        if (UltraVisualDraftPolicy.canSend(
                                authorizedOrigin,
                                UltraPrivacyEpoch.current(),
                                latestAiContext.selectedGamePackage
                            )
                        ) {
                            visualDraftOrigin = authorizedOrigin
                            chatMessage = ("Ayúdame a interpretar este texto de una imagen elegida por mí:\n" +
                                extracted.take(800)).take(1000)
                            showTextChat = true
                        }
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
                Text(
                    stringResource(
                        R.string.voice_transcript,
                        presentVoiceTranscript(transcript)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            response?.let {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
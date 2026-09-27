package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

sealed interface UltraAgentRoute {
    data class Command(val command: VoiceCommand) : UltraAgentRoute
    data class Chat(
        val message: String,
        val query: UltraGeneralQueryRequest? = null
    ) : UltraAgentRoute
    data class Utility(val answer: UltraAgentAnswer) : UltraAgentRoute
}

data class UltraRuntimeTelemetry(
    val batteryPercent: Int? = null,
    val thermalLabel: String? = null,
    val refreshRateHz: Float? = null
)

data class UltraAgentAnswer(
    val message: String,
    val intent: UltraUtilityIntent,
    val canRunDuringGame: Boolean,
    val requiresPhoneRoleOrPermission: Boolean = false
)

sealed interface UltraUtilityIntent {
    data object CurrentTime : UltraUtilityIntent
    data object CurrentDate : UltraUtilityIntent
    data object DeviceTemperature : UltraUtilityIntent
    data object BatteryStatus : UltraUtilityIntent
    data object RefreshRate : UltraUtilityIntent
    data object CallInterruptionShield : UltraUtilityIntent
    data object GeneralCapabilityHelp : UltraUtilityIntent
    data class StudyMath(val solution: UltraMathSolution) : UltraUtilityIntent
    data class NetworkGamingControl(
        val competitive: Boolean,
        val routerGaming: Boolean
    ) : UltraUtilityIntent
}

object UltraUnifiedAgentRouter {
    fun route(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null,
        telemetry: UltraRuntimeTelemetry? = null,
        clock: Clock = Clock.systemDefaultZone(),
        knownGameAliases: Set<String> = emptySet(),
        conversationHistory: List<String> = emptyList()
    ): UltraAgentRoute {
        // Memory commands must bypass profile/game parsing. Phrases such as
        // "elimina de tu memoria prefiero X4" contain profile keywords but are
        // conversational memory operations, not launch/profile commands.
        if (UltraMemoryCommandParser.parse(transcript) != null) {
            return UltraAgentRoute.Chat(transcript.trim())
        }

        val command = VoiceCommandParser.parse(
            transcript = transcript,
            optionalResolver = optionalResolver,
            knownGameAliases = knownGameAliases
        )

        // Alias-definition commands must win over utility keyword matching.
        // Example: "when I say bb open Battery Boy" must define an alias,
        // not be reinterpreted as a battery-status question.
        if (command is VoiceCommand.DefineGameAlias) {
            return UltraAgentRoute.Command(command)
        }

        val learnedAlias =
            VoiceCommandParser.isKnownGameAlias(transcript, knownGameAliases) ||
                (
                    command is VoiceCommand.OpenGame &&
                        VoiceCommandParser.isKnownGameAlias(
                            command.query,
                            knownGameAliases
                        )
                )

        // A persisted alias is an explicit user choice and must beat built-in
        // network/utility phrases, including aliases such as "gaming router".
        if (command is VoiceCommand.OpenGame && learnedAlias) {
            return UltraAgentRoute.Command(command)
        }

        // The leading assistant invocation has already been stripped by
        // VoiceCommandParser. If an assistant-name token appears again in the
        // resulting launch query, it belongs to the game title. This prevents
        // titles such as "Ultra Competitive" from being reclassified as the
        // built-in competitive-network command.
        val assistantNameInsideGameTitle =
            command is VoiceCommand.OpenGame &&
                VoiceCommandParser.hasExplicitLaunchIntent(transcript) &&
                command.query.split(" ").any { token ->
                    token == "ultra" || token == "gamehub"
                }
        if (assistantNameInsideGameTitle) {
            return UltraAgentRoute.Command(command)
        }

        UltraNetworkGamingIntentParser.parse(transcript)?.let { intent ->
            return UltraAgentRoute.Utility(
                UltraGeneralAssistant.answer(
                    intent = intent,
                    clock = clock,
                    telemetry = telemetry
                )
            )
        }

        val queryRequest = UltraContextualQueryPlanner.plan(
            message = transcript,
            conversationHistory = conversationHistory
        )
        val contextualResearchFollowUp =
            conversationHistory.isNotEmpty() &&
                UltraConversationContextResolver.looksLikeFollowUp(transcript) &&
                queryRequest.kind != UltraGeneralQueryKind.GENERAL_KNOWLEDGE

        if (!VoiceCommandParser.hasExplicitLaunchIntent(transcript)) {
            UltraMathEngine.solve(transcript)?.let { solution ->
                return UltraAgentRoute.Utility(
                    UltraGeneralAssistant.answer(
                        intent = UltraUtilityIntent.StudyMath(solution),
                        clock = clock,
                        telemetry = telemetry
                    )
                )
            }

            if (contextualResearchFollowUp) {
                return UltraAgentRoute.Chat(
                    message = transcript.trim(),
                    query = queryRequest
                )
            }

            UltraGeneralAssistant.classify(transcript)?.let { intent ->
                return UltraAgentRoute.Utility(
                    UltraGeneralAssistant.answer(
                        intent = intent,
                        clock = clock,
                        telemetry = telemetry
                    )
                )
            }
        }

        return when {
            command is VoiceCommand.Unknown ->
                UltraAgentRoute.Chat(
                    message = transcript.trim(),
                    query = queryRequest
                )
            command is VoiceCommand.OpenGame &&
                command.requestedProfile == null &&
                !VoiceCommandParser.hasExplicitLaunchIntent(transcript) &&
                !learnedAlias ->
                UltraAgentRoute.Chat(
                    message = transcript.trim(),
                    query = queryRequest
                )
            else ->
                UltraAgentRoute.Command(command)
        }
    }
}

object UltraNetworkGamingIntentParser {
    private data class Clause(
        val text: String,
        val connectorBefore: String? = null
    )

    private val actionPattern = Regex(
        """\b(activa|activar|pon|poner|ponme|habilita|habilitar|usa|usar|quiero|aplica|aplicar|prioriza|priorizar|enable|activate|set|use|prioritize)\b"""
    )
    private val questionPattern = Regex(
        """\b(que es|que significa|como funciona|quiero saber|explicame|dime que es|como (?:puedo|podria|debo) (?:activar|habilitar|poner|usar|priorizar)|como activo|what is|what does|how does|tell me about|i want to know|how (?:can|do) i (?:activate|enable|set|use|prioritize))\b"""
    )
    private val clauseSeparator = Regex("""\b(y|and|pero|but|ademas|also|o|or|ni|nor)\b""")
    private val additiveConnectors = setOf("y", "and", "ademas", "also")
    private val statusPattern = Regex(
        """\b(ya esta activo|ya esta activa|ya esta activado|ya esta activada|esta activo|esta activa|esta activado|esta activada|already active|is already active|is active|already activated|is already activated|is activated|already enabled|is already enabled|is enabled)\b"""
    )
    private val competitivePatterns = listOf(
        Regex(
            """\b(modo competitivo|perfil competitivo|competitive mode|competitive profile)\b(?=\s*(?:$|y\b|and\b|pero\b|but\b|con\b|with\b|sin\b|without\b|excepto\b|except\b|qos\b|router\b|gaming\b|por favor\b|please\b|ahora\b|now\b))"""
        ),
        Regex(
            """\b(competitivo|competitive)\b(?=\s*(?:$|con\b|with\b|sin\b|without\b|excepto\b|except\b|qos\b|router\b|gaming\b))"""
        )
    )
    private val routerGamingPatterns = listOf(
        Regex(
            """\b(router gaming|gaming router|modo gaming del router|gaming router mode)\b(?=\s*(?:$|y\b|and\b|pero\b|but\b|con\b|with\b|sin\b|without\b|excepto\b|except\b|qos\b|por favor\b|please\b|ahora\b|now\b))"""
        ),
        Regex(
            """\b(qos gaming|gaming qos)\b(?=\s*(?:$|y\b|and\b|pero\b|but\b|con\b|with\b|sin\b|without\b|excepto\b|except\b|por favor\b|please\b|ahora\b|now\b))"""
        ),
        Regex("""\b(prioridad gaming del router|prioridad del router|router con prioridad)\b"""),
        Regex(
            """\b(prioriza|priorizar|prioridad|prioritize)\b(?:\s+[a-z0-9]+){0,8}\s+(?:(?:en|del|on|in)\s+)?(?:el\s+|the\s+)?router\b"""
        )
    )
    private val competitiveNegationPatterns = listOf(
        Regex("""\b(sin|excepto|menos)\s+(?:el\s+|la\s+)?(?:modo\s+|perfil\s+)?competitivo\b"""),
        Regex("""\b(without|except)\s+(?:the\s+)?(?:competitive mode|competitive profile|competitive)\b"""),
        Regex("""\b(no\s+quiero|no|dont\s+want|do\s+not\s+want|don\s+t\s+want|dont|do\s+not|don\s+t|not)\s+(?:actives|activar|enable|activate|use|usar|pongas|poner|set)?\s*(?:the\s+)?(?:modo\s+|perfil\s+)?(?:competitivo|competitive)(?:\s+(?:mode|profile))?\b""")
    )
    private val routerNegationPatterns = listOf(
        Regex("""\b(sin|excepto|menos)\s+(?:el\s+|la\s+)?(?:modo\s+)?(?:router gaming|gaming router|qos gaming|gaming qos)\b"""),
        Regex("""\b(without|except)\s+(?:(?:activating|activate|enabling|enable|using|use|setting|set)\s+)?(?:the\s+)?(?:router gaming|gaming router|qos gaming|gaming qos)\b"""),
        Regex("""\b(no|dont|do\s+not|don\s+t|not)\s+(?:quiero\s+|want\s+to\s+)?(?:prioriza|priorizar|prioritize|prioritise)\b(?:\s+[a-z0-9]+){0,8}\s+(?:(?:en|del|on|in)\s+)?(?:el\s+|the\s+)?router\b"""),
        Regex("""\b(no\s+quiero|no|dont\s+want|do\s+not\s+want|don\s+t\s+want|dont|do\s+not|don\s+t|not)\s+(?:actives|activar|enable|activate|use|usar|pongas|poner|set)?\s*(?:the\s+|el\s+|la\s+|modo\s+)?(?:router gaming|gaming router|qos gaming|gaming qos)\b""")
    )

    private fun splitClauses(clean: String): List<Clause> {
        val clauses = mutableListOf<Clause>()
        var start = 0
        var connectorBefore: String? = null

        clauseSeparator.findAll(clean).forEach { match ->
            val text = clean.substring(start, match.range.first).trim()
            if (text.isNotBlank()) {
                clauses += Clause(text = text, connectorBefore = connectorBefore)
            }
            connectorBefore = match.value
            start = match.range.last + 1
        }

        val tail = clean.substring(start).trim()
        if (tail.isNotBlank()) {
            clauses += Clause(text = tail, connectorBefore = connectorBefore)
        }
        return clauses
    }

    fun parse(transcript: String): UltraUtilityIntent.NetworkGamingControl? {
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        if (clean.isBlank()) return null

        var competitive = false
        var routerGaming = false
        var previousClauseHadActivation = false

        splitClauses(clean).forEach { clause ->
            val text = clause.text
            val isQuestion = questionPattern.containsMatchIn(text)
            val directAction = actionPattern.containsMatchIn(text)
            val inheritsAction =
                clause.connectorBefore in additiveConnectors && previousClauseHadActivation
            val activationApplies = !isQuestion && (directAction || inheritsAction)
            val inheritedStatusOnly =
                !directAction && inheritsAction && statusPattern.containsMatchIn(text)

            val competitiveMentioned =
                competitivePatterns.any { it.containsMatchIn(text) }
            val routerMentioned =
                routerGamingPatterns.any { it.containsMatchIn(text) }
            val competitiveNegated =
                competitiveNegationPatterns.any { it.containsMatchIn(text) }
            val routerNegated =
                routerNegationPatterns.any { it.containsMatchIn(text) }

            val affirmativeCompetitive =
                activationApplies && !inheritedStatusOnly &&
                    competitiveMentioned && !competitiveNegated
            val affirmativeRouter =
                activationApplies && !inheritedStatusOnly &&
                    routerMentioned && !routerNegated

            if (affirmativeCompetitive) {
                competitive = true
            }
            if (affirmativeRouter) {
                routerGaming = true
            }

            // Only carry an activation verb across an additive connector when
            // this clause actually requested at least one non-negated target.
            // This prevents "don't activate A and B" from enabling B.
            previousClauseHadActivation =
                !isQuestion && (affirmativeCompetitive || affirmativeRouter)
        }

        if (!competitive && !routerGaming) return null
        return UltraUtilityIntent.NetworkGamingControl(
            competitive = competitive,
            routerGaming = routerGaming
        )
    }
}

object UltraGeneralAssistant {
    private val timePatterns = listOf(
        Regex("""\b(que hora es|dime la hora|hora actual|current time|what time is it|tell me the time)\b""")
    )
    private val datePatterns = listOf(
        Regex("""\b(que dia es|que fecha es|fecha actual|current date|what day is it|what is the date)\b""")
    )
    private val temperaturePatterns = listOf(
        Regex("""\b(temperatura|temperature|estado termico|thermal status|thermal)\b""")
    )
    private val batteryPatterns = listOf(
        Regex("""\b(bateria|battery|nivel de bateria|battery level|carga restante)\b""")
    )
    private val refreshRatePatterns = listOf(
        Regex("""\b(hz|hercios|refresco|tasa de refresco|frecuencia de pantalla|refresh rate|screen refresh)\b""")
    )
    private val callShieldPatterns = listOf(
        Regex("""\b(llamada|llamadas|call|calls)\b"""),
        Regex("""\b(segundo plano|background|no interrumpan|no molesten|interrumpir|interfieran|interrupt)\b""")
    )
    private val capabilityPatterns = listOf(
        Regex("""\b(que puedes hacer|conversa conmigo|habla conmigo|no solo gaming|no sea de gaming|no sean de gaming|cosas que no sean de gaming|preguntas generales|general questions)\b""")
    )

    fun classify(transcript: String): UltraUtilityIntent? {
        val clean = VoiceCommandParser.normalize(transcript)
            .replace(Regex("""\bultra\b"""), " ")
            .trim()
        if (clean.isBlank()) return null
        if (timePatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.CurrentTime
        }
        if (datePatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.CurrentDate
        }
        if (temperaturePatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.DeviceTemperature
        }
        if (batteryPatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.BatteryStatus
        }
        if (refreshRatePatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.RefreshRate
        }
        if (callShieldPatterns.all { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.CallInterruptionShield
        }
        if (capabilityPatterns.any { it.containsMatchIn(clean) }) {
            return UltraUtilityIntent.GeneralCapabilityHelp
        }
        return null
    }

    fun answer(
        intent: UltraUtilityIntent,
        clock: Clock = Clock.systemDefaultZone(),
        telemetry: UltraRuntimeTelemetry? = null
    ): UltraAgentAnswer =
        when (intent) {
            UltraUtilityIntent.CurrentTime -> {
                val time = LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm"))
                UltraAgentAnswer(
                    message = "Son las $time.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.CurrentDate -> {
                val date = LocalDate.now(clock)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
                UltraAgentAnswer(
                    message = "Hoy es $date.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.DeviceTemperature -> {
                val thermal = telemetry?.thermalLabel?.takeIf { it.isNotBlank() }
                UltraAgentAnswer(
                    message = thermal?.let { "Estado térmico: $it." }
                        ?: "El estado térmico no está disponible en este dispositivo.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.BatteryStatus -> {
                val battery = telemetry?.batteryPercent?.takeIf { it in 0..100 }
                UltraAgentAnswer(
                    message = battery?.let { "Batería: $it por ciento." }
                        ?: "El nivel de batería no está disponible.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.RefreshRate -> {
                val refresh = telemetry?.refreshRateHz
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.roundToInt()
                UltraAgentAnswer(
                    message = refresh?.let { "La pantalla está funcionando a $it Hz." }
                        ?: "La frecuencia de refresco actual no está disponible.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.CallInterruptionShield ->
                UltraAgentAnswer(
                    message = "Android sí permite responder llamadas sin cortar el juego por rutas oficiales: rol de teléfono predeterminado con InCallService, permiso ANSWER_PHONE_CALLS cuando aplica o integración OEM como Game Booster. Ultra puede preparar esa ruta, explicar permisos y sugerir No molestar; no usaré privilegios de sistema sin autorización.",
                    intent = intent,
                    canRunDuringGame = true,
                    requiresPhoneRoleOrPermission = true
                )
            UltraUtilityIntent.GeneralCapabilityHelp ->
                UltraAgentAnswer(
                    message = "Puedo conversar de temas generales, responder hora y fecha, consultar batería, estado térmico y Hz, ayudarte con ajustes seguros y seguir controlando perfiles, biblioteca y estado del dispositivo.",
                    intent = intent,
                    canRunDuringGame = true
                )
            is UltraUtilityIntent.StudyMath ->
                UltraAgentAnswer(
                    message = "Resultado: ${intent.solution.resultText}. ${intent.solution.explanation}",
                    intent = intent,
                    canRunDuringGame = true
                )
            is UltraUtilityIntent.NetworkGamingControl -> {
                val requested = when {
                    intent.competitive && intent.routerGaming ->
                        "modo competitivo y Gaming Router"
                    intent.competitive ->
                        "modo competitivo"
                    else ->
                        "Gaming Router"
                }
                UltraAgentAnswer(
                    message = "Entendí la orden para activar $requested. El reconocimiento de voz ya está preparado, pero el motor Network Game Booster de CAR-72 todavía no está conectado a esta versión; no se aplicó ningún cambio de red ni QoS.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
        }
}

object UltraConversationPolicy {
    fun append(
        history: List<String>,
        entry: String,
        maxEntries: Int
    ): List<String> {
        val safeMaxEntries = maxEntries.coerceAtLeast(1)
        val cleanEntry = entry.trim()
        if (cleanEntry.isBlank()) return history.takeLast(safeMaxEntries)
        return (history + cleanEntry).takeLast(safeMaxEntries)
    }
}

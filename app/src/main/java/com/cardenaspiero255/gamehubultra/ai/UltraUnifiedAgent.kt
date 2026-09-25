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
    data class Chat(val message: String) : UltraAgentRoute
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
}

object UltraUnifiedAgentRouter {
    fun route(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null,
        telemetry: UltraRuntimeTelemetry? = null,
        clock: Clock = Clock.systemDefaultZone()
    ): UltraAgentRoute {
        // Memory commands must bypass profile/game parsing. Phrases such as
        // "elimina de tu memoria prefiero X4" contain profile keywords but are
        // conversational memory operations, not launch/profile commands.
        if (UltraMemoryCommandParser.parse(transcript) != null) {
            return UltraAgentRoute.Chat(transcript.trim())
        }

        if (!VoiceCommandParser.hasExplicitLaunchIntent(transcript)) {
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

        val command = VoiceCommandParser.parse(transcript, optionalResolver)
        return when {
            command is VoiceCommand.Unknown ->
                UltraAgentRoute.Chat(transcript.trim())
            command is VoiceCommand.OpenGame &&
                command.requestedProfile == null &&
                !VoiceCommandParser.hasExplicitLaunchIntent(transcript) ->
                UltraAgentRoute.Chat(transcript.trim())
            else ->
                UltraAgentRoute.Command(command)
        }
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

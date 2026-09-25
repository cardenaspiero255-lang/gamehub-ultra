package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.voice.NaturalLanguageIntentResolver
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandParser
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface UltraAgentRoute {
    data class Command(val command: VoiceCommand) : UltraAgentRoute
    data class Utility(val answer: UltraAgentAnswer) : UltraAgentRoute
    data class Chat(val message: String) : UltraAgentRoute
}

data class UltraAgentAnswer(
    val message: String,
    val intent: UltraUtilityIntent,
    val canRunDuringGame: Boolean,
    val requiresPrivilegedPermission: Boolean = false
)

sealed interface UltraUtilityIntent {
    data object CurrentTime : UltraUtilityIntent
    data object CurrentDate : UltraUtilityIntent
    data object CallInterruptionShield : UltraUtilityIntent
    data object GeneralCapabilityHelp : UltraUtilityIntent
}

object UltraUnifiedAgentRouter {
    fun route(
        transcript: String,
        optionalResolver: NaturalLanguageIntentResolver? = null,
        clock: Clock = Clock.systemDefaultZone()
    ): UltraAgentRoute {
        UltraGeneralAssistant.classify(transcript)?.let { intent ->
            return UltraAgentRoute.Utility(
                UltraGeneralAssistant.answer(
                    intent = intent,
                    clock = clock
                )
            )
        }

        val command = VoiceCommandParser.parse(transcript, optionalResolver)
        return when {
            command is VoiceCommand.Unknown ->
                UltraAgentRoute.Chat(transcript.trim())
            command is VoiceCommand.OpenGame && !hasExplicitLaunchIntent(transcript) ->
                UltraAgentRoute.Chat(transcript.trim())
            else ->
                UltraAgentRoute.Command(command)
        }
    }

    private fun hasExplicitLaunchIntent(transcript: String): Boolean {
        val clean = VoiceCommandParser.normalize(transcript)
            .replace(Regex("""\bultra\b"""), " ")
            .replace(Regex("""\bgamehub\b"""), " ")
            .trim()
        return Regex(
            """^(abre|abrir|abreme|lanza|lanzar|inicia|iniciar|ejecuta|ejecutar|juega|open|launch|start|run|play)\b"""
        ).containsMatchIn(clean)
    }
}

object UltraGeneralAssistant {
    private val timePatterns = listOf(
        Regex("""\b(que hora es|dime la hora|hora actual|current time|what time is it)\b""")
    )
    private val datePatterns = listOf(
        Regex("""\b(que dia es|que fecha es|fecha actual|current date|what day is it)\b""")
    )
    private val callShieldPatterns = listOf(
        Regex("""\b(llamada|llamadas|call|calls)\b"""),
        Regex("""\b(segundo plano|background|no interrumpan|no molesten|interrumpir|interfieran|interrupt)\b""")
    )
    private val capabilityPatterns = listOf(
        Regex("""\b(que puedes hacer|conversa conmigo|habla conmigo|no solo gaming|no sea de gaming|preguntas generales|general questions)\b""")
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
        clock: Clock = Clock.systemDefaultZone()
    ): UltraAgentAnswer =
        when (intent) {
            UltraUtilityIntent.CurrentTime -> {
                val time = LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm"))
                UltraAgentAnswer(
                    message = "Son las $time. Puedo responder esto sin salir del juego.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.CurrentDate -> {
                val date = LocalDate.now(clock)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
                UltraAgentAnswer(
                    message = "Hoy es $date. Puedo mantener esta conversación aunque no sea de gaming.",
                    intent = intent,
                    canRunDuringGame = true
                )
            }
            UltraUtilityIntent.CallInterruptionShield ->
                UltraAgentAnswer(
                    message = "Puedo ayudarte a reducir interrupciones durante el juego: mantener la sesión visible, sugerir modo No molestar y explicar llamadas sin cerrar Ultra. Responder llamadas automáticamente solo es posible si Android entrega permisos de app telefónica predeterminada o permisos del sistema; si no, no lo finjo.",
                    intent = intent,
                    canRunDuringGame = true,
                    requiresPrivilegedPermission = true
                )
            UltraUtilityIntent.GeneralCapabilityHelp ->
                UltraAgentAnswer(
                    message = "Puedo conversar de temas generales, responder utilidad básica como hora/fecha, ayudarte con ajustes seguros y seguir controlando perfiles, biblioteca y estado del dispositivo cuando sí sea gaming.",
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

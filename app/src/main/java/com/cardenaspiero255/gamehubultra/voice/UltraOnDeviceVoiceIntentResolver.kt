package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.LocalVoiceIntentCandidate
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

/**
 * A local LLM suggests one narrow, typed action. Untrusted model text is not
 * executable and cannot invent a game target absent from the user's words.
 */
object UltraOnDeviceVoiceIntentResolver {
    fun validate(
        transcript: String,
        candidate: LocalVoiceIntentCandidate
    ): VoiceCommand? {
        if (transcript.length !in 4..300) return null
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        if (clean.isBlank() || listOf(
                "ignora instrucciones", "ignore instructions", "disregard instructions",
                "elimina archivos", "borrar archivos", "ejecuta shell"
            ).any(clean::contains)
        ) return null
        // Explanations are always conversation, never device actions.
        if (listOf("que es ", "explica ", "hablame ", "cuentame ", "por que ")
                .any { clean.startsWith(it) }
        ) return null
        val profileRequested = Regex(
            """\b(pon|activa|selecciona|cambia|usar|usa|aplica|configura|perfil|modo|set|switch|activate)\b"""
        ).containsMatchIn(clean)
        return when (candidate.command.trim().uppercase(java.util.Locale.ROOT)) {
            "PROFILE_BALANCED" -> if (profileRequested &&
                listOf("balanceado", "equilibrado", "balanced").any(clean::contains)
            ) VoiceCommand.SelectProfile(PerformanceProfile.BALANCED) else null
            "PROFILE_X4" -> if (profileRequested && clean.contains("x4"))
                VoiceCommand.SelectProfile(PerformanceProfile.X4) else null
            "PROFILE_INTERPOLATION" -> if (profileRequested &&
                listOf("interpolacion", "interpolar", "interpolation").any(clean::contains)
            ) VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION) else null
            "OPEN_GAME" -> {
                val name = candidate.argument?.trim().orEmpty()
                val normalizedName = VoiceCommandParser.normalize(name)
                val launchRequested = Regex(
                    """\b(abre|abrir|abreme|lanza|inicia|ejecuta|juega|open|launch|play|start)\b"""
                ).containsMatchIn(clean)
                if (
                    launchRequested &&
                    name.length in 2..60 &&
                    normalizedName.length >= 2 &&
                    clean.contains(normalizedName) &&
                    !normalizedName.startsWith("com.") &&
                    !normalizedName.contains("android")
                ) VoiceCommand.OpenGame(name) else null
            }
            "DEVICE_STATUS" -> if (listOf(
                "bateria", "temperatura", "estado del dispositivo",
                "device status", "battery", "thermal"
            ).any(clean::contains)) VoiceCommand.DeviceStatus else null
            "HELP" -> if (listOf("ayuda", "comandos", "que puedes hacer", "help")
                    .any(clean::contains)) VoiceCommand.Help else null
            else -> null
        }
    }
}

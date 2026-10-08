package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.ai.LocalVoiceIntentCandidate
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile

/**
 * The model proposes data only. Actions require a direct request, and game
 * names must match the original spoken target at whole-word boundaries.
 */
object UltraOnDeviceVoiceIntentResolver {
    fun validate(
        transcript: String,
        candidate: LocalVoiceIntentCandidate
    ): VoiceCommand? {
        if (transcript.length !in 4..300) return null
        if (transcript.contains(';') || transcript.contains('\n') ||
            transcript.contains("&&")
        ) return null
        val clean = VoiceCommandParser.stripLeadingAssistantInvocation(transcript)
        if (clean.isBlank() || listOf(
                "ignora instrucciones", "ignore instructions",
                "elimina archivos", "borrar archivos", "ejecuta shell"
            ).any(clean::contains)
        ) return null

        val polite = """(?:(?:por favor|oye)\s+)?(?:(?:podrias|puedes|quiero que|necesito que)\s+)?"""
        val explicitLaunch = Regex(
            "^" + polite +
                """(?:abre|abrir|abreme|lanza|lanzar|inicia|iniciar|ejecuta|juega|open|launch|play|start)\s+(?:el juego\s+|al juego\s+)?(.+?)(?:\s+por favor)?$"""
        ).matchEntire(clean)
        val explicitProfile = Regex(
            "^" + polite +
                """(?:pon|poner|activa|activar|selecciona|seleccionar|cambia|cambiar|usa|usar|aplica|configura|set|switch|activate)\s+.+$"""
        ).matches(clean)

        return when (candidate.command.trim().uppercase(java.util.Locale.ROOT)) {
            "PROFILE_BALANCED" -> if (explicitProfile &&
                listOf("balanceado", "equilibrado", "balanced").any(clean::contains)
            ) VoiceCommand.SelectProfile(PerformanceProfile.BALANCED) else null
            "PROFILE_X4" -> if (explicitProfile &&
                Regex("""\bx4\b""").containsMatchIn(clean)
            ) VoiceCommand.SelectProfile(PerformanceProfile.X4) else null
            "PROFILE_INTERPOLATION" -> if (explicitProfile &&
                listOf("interpolacion", "interpolar", "interpolation").any(clean::contains)
            ) VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION) else null
            "OPEN_GAME" -> {
                val name = candidate.argument?.trim().orEmpty()
                val normalizedName = VoiceCommandParser.normalize(name)
                val spokenTarget = explicitLaunch?.groupValues?.getOrNull(1)?.trim()
                if (
                    name.length in 2..60 &&
                    normalizedName.length >= 2 &&
                    spokenTarget != null &&
                    normalizedName == spokenTarget &&
                    !normalizedName.startsWith("com ") &&
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

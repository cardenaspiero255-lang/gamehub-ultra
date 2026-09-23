package com.cardenaspiero255.gamehubultra.ai

/**
 * Validates free-form local-model output before it reaches the chat UI.
 * Model output is treated as untrusted text and cannot authorize arbitrary device actions.
 */
object AiChatSafetyFilter {
    private const val MAX_RESPONSE_LENGTH = 4_000

    private val unsafePatterns = listOf(
        Regex("(?i)\\badb\\s+shell\\b|\\bfastboot\\b|\\bsettings\\s+(put|delete|read)\\b|\\bam\\s+(start|force-stop)\\b|\\bpm\\s+(grant|revoke|install|uninstall)\\b"),
        Regex("(?i)\\b(startActivity|startService|sendBroadcast|Runtime\\.getRuntime|exec\\s*\\(|intent\\s*\\(|tool call|tool invocation)\\b"),
        Regex("(?i)\\b(i|yo|he|ya|acabo de)\\s+(activated|changed|modified|executed|opened|closed|applied|injected|overclocked|controlled|activé|cambié|modifiqué|ejecuté|abrí|cerré|apliqué|inyecté|overclockeé|controlé)\\b"),
        Regex("(?i)\\b(i|yo)\\s+(can|puedo)\\s+(execute|run|control|change|modify|inject|open|close|apply|ejecutar|correr|controlar|cambiar|modificar|inyectar|abrir|cerrar|aplicar)\\b")
    )

    fun sanitize(modelOutput: String, userMessage: String): String {
        val response = modelOutput.trim()
        if (response.isBlank()) return unsupportedMessage(userMessage)
        if (response.contains("```") || unsafePatterns.any { it.containsMatchIn(response) }) {
            return unsupportedMessage(userMessage)
        }
        return response.take(MAX_RESPONSE_LENGTH)
    }

    private fun unsupportedMessage(userMessage: String): String =
        if (looksSpanish(userMessage)) {
            "Ultra no ejecuta comandos ni controla otras aplicaciones. Puedo analizar el estado del dispositivo y explicarte opciones seguras disponibles dentro de GameHub Ultra."
        } else {
            "Ultra does not execute commands or control other apps. I can analyze the device state and explain safe options available inside GameHub Ultra."
        }

    private fun looksSpanish(value: String): Boolean {
        val normalized = value.lowercase()
        return normalized.any { it in "áéíóúñ¿¡" } ||
            listOf(" que ", " como ", " puedo ", " para ", " mi ", " del ", " esta ", " modo ").any(normalized::contains)
    }
}

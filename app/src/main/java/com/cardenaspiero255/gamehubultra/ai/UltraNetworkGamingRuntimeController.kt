package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome

/**
 * Executes the local part of Ultra's network-gaming request and reports the
 * observed runtime result instead of merely acknowledging the command.
 *
 * Router QoS remains separate because Android cannot safely change an
 * arbitrary router without an explicit compatible integration.
 */
object UltraNetworkGamingRuntimeController {
    fun execute(
        intent: UltraUtilityIntent.NetworkGamingControl,
        applyCompetitive: () -> NetworkOptimizationOutcome
    ): String {
        val messages = mutableListOf<String>()

        if (intent.competitive) {
            val outcome = runCatching(applyCompetitive)
                .getOrDefault(NetworkOptimizationOutcome.UNAVAILABLE)
            messages += when (outcome) {
                NetworkOptimizationOutcome.APPLIED ->
                    "Modo competitivo: ACTIVO. Network Game Booster aplicó la prioridad local de baja latencia."

                NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE ->
                    "Modo competitivo: PREPARADO, todavía no ACTIVO. La prioridad local quedará efectiva cuando GameHub Ultra esté en primer plano y el dispositivo esté interactivo."

                NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED ->
                    "Modo competitivo: NO ACTIVADO. La conexión actual no requiere o no permite mantener la prioridad local."

                NetworkOptimizationOutcome.UNAVAILABLE ->
                    "Modo competitivo: NO ACTIVADO. Android, el dispositivo o la red actual no permiten aplicar la prioridad local en este momento."

                NetworkOptimizationOutcome.NOT_REQUESTED ->
                    "Modo competitivo: NO ACTIVADO. No se llegó a solicitar la optimización local."
            }
        }

        if (intent.routerGaming) {
            messages +=
                "Gaming Router: NO ACTIVADO. El QoS del router requiere una integración autorizada y compatible con ese router."
        }

        return messages.joinToString(" ").ifBlank {
            "No se solicitó ningún cambio de red."
        }
    }
}

package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.network.NetworkOptimizationOutcome

/**
 * Executes Ultra's network-gaming request and reports the observed runtime
 * result instead of merely acknowledging the command.
 *
 * "Gaming Router" applies the safe local low-latency path toward the router.
 * Hardware-router QoS is never claimed unless a separate authorized router
 * integration actually exists.
 */
object UltraNetworkGamingRuntimeController {
    fun execute(
        intent: UltraUtilityIntent.NetworkGamingControl,
        applyCompetitive: () -> NetworkOptimizationOutcome
    ): String {
        val messages = mutableListOf<String>()
        val localOutcome =
            if (intent.competitive || intent.routerGaming) {
                runCatching(applyCompetitive)
                    .getOrDefault(NetworkOptimizationOutcome.UNAVAILABLE)
            } else {
                NetworkOptimizationOutcome.NOT_REQUESTED
            }

        if (intent.competitive) {
            messages += competitiveMessage(localOutcome)
        }

        if (intent.routerGaming) {
            messages += routerGamingMessage(localOutcome)
        }

        return messages.joinToString(" ").ifBlank {
            "No se solicitó ningún cambio de red."
        }
    }

    private fun competitiveMessage(outcome: NetworkOptimizationOutcome): String =
        when (outcome) {
            NetworkOptimizationOutcome.APPLIED ->
                "Modo competitivo: ACTIVO. Network Game Booster aplicó la prioridad local de baja latencia."

            NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE ->
                "Modo competitivo: PREPARADO. La prioridad local quedará activa cuando GameHub Ultra esté en primer plano y el dispositivo esté interactivo."

            NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED ->
                "Modo competitivo: NO ACTIVADO. La conexión actual no requiere o no permite mantener la prioridad local."

            NetworkOptimizationOutcome.UNAVAILABLE ->
                "Modo competitivo: NO ACTIVADO. Android, el dispositivo o la red actual no permiten aplicar la prioridad local en este momento."

            NetworkOptimizationOutcome.NOT_REQUESTED ->
                "Modo competitivo: NO ACTIVADO. No se llegó a solicitar la optimización local."
        }

    private fun routerGamingMessage(outcome: NetworkOptimizationOutcome): String =
        when (outcome) {
            NetworkOptimizationOutcome.APPLIED ->
                "Gaming Router: ACTIVO en modo local. GameHub Ultra aplicó prioridad local de baja latencia en la conexión Wi-Fi hacia el router. El QoS interno del router no se modifica sin una integración autorizada del equipo."

            NetworkOptimizationOutcome.LEASE_ACQUIRED_PENDING_INTERACTIVE ->
                "Gaming Router: PREPARADO en modo local. La prioridad hacia el router quedará activa cuando GameHub Ultra esté en primer plano y el dispositivo esté interactivo. El QoS interno del router no se modifica sin autorización."

            NetworkOptimizationOutcome.RELEASED_OR_NOT_NEEDED ->
                "Gaming Router: NO ACTIVADO. La conexión actual no requiere o no permite mantener prioridad local hacia el router."

            NetworkOptimizationOutcome.UNAVAILABLE ->
                "Gaming Router: NO ACTIVADO. Android, el Wi-Fi o la red actual no permiten aplicar prioridad local hacia el router en este momento."

            NetworkOptimizationOutcome.NOT_REQUESTED ->
                "Gaming Router: NO ACTIVADO. No se llegó a solicitar la optimización."
        }
}

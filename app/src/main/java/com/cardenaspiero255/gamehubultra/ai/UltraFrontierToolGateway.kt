package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.tools.UltraToolContract
import com.cardenaspiero255.gamehubultra.tools.UltraToolExecution
import com.cardenaspiero255.gamehubultra.tools.UltraToolResult
import com.cardenaspiero255.gamehubultra.tools.UltraToolSideEffect

data class UltraFrontierToolAuthorization(
    val userConfirmedMutation: Boolean = false
)

/**
 * Central safety gate for every typed Ultra tool.
 *
 * READ_ONLY capabilities can run directly. MIXED and MUTATES_STATE capabilities require
 * explicit mutation confirmation before the underlying contract is invoked.
 */
class UltraFrontierToolGateway {
    fun <I, O> execute(
        tool: UltraToolContract<I, O>,
        request: I,
        authorization: UltraFrontierToolAuthorization = UltraFrontierToolAuthorization()
    ): UltraToolResult<O> {
        val requiresConfirmation =
            tool.descriptor.sideEffect != UltraToolSideEffect.READ_ONLY

        if (requiresConfirmation && !authorization.userConfirmedMutation) {
            return UltraToolExecution.invalidInput(
                descriptor = tool.descriptor,
                message = "Esta acción puede cambiar el estado del dispositivo o de GameHub y requiere confirmación explícita."
            )
        }

        return tool.execute(request)
    }
}

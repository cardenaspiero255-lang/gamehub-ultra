package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.tools.UltraToolContract
import com.cardenaspiero255.gamehubultra.tools.UltraToolExecution
import com.cardenaspiero255.gamehubultra.tools.UltraToolResult
import com.cardenaspiero255.gamehubultra.tools.UltraToolSideEffect

data class UltraFrontierToolAuthorization(
    val userConfirmedMutation: Boolean = false,
    val confirmedToolId: String? = null
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

        val confirmationMatchesTool =
            authorization.userConfirmedMutation &&
                authorization.confirmedToolId == tool.descriptor.id

        if (requiresConfirmation && !confirmationMatchesTool) {
            return UltraToolExecution.invalidInput(
                descriptor = tool.descriptor,
                message =
                    "Esta acción puede cambiar el estado del dispositivo o de GameHub y " +
                        "requiere confirmación explícita para esta herramienta concreta."
            )
        }

        return tool.execute(request)
    }
}

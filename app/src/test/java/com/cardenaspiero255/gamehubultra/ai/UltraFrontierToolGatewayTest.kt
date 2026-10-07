package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.tools.UltraToolContract
import com.cardenaspiero255.gamehubultra.tools.UltraToolDescriptor
import com.cardenaspiero255.gamehubultra.tools.UltraToolFailureCode
import com.cardenaspiero255.gamehubultra.tools.UltraToolKind
import com.cardenaspiero255.gamehubultra.tools.UltraToolResult
import com.cardenaspiero255.gamehubultra.tools.UltraToolSideEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UltraFrontierToolGatewayTest {
    @Test
    fun mutatingToolIsBlockedUntilExplicitlyConfirmed() {
        var executions = 0
        val tool = fakeTool(UltraToolSideEffect.MUTATES_STATE) {
            executions += 1
            "changed"
        }
        val gateway = UltraFrontierToolGateway()

        val blocked = gateway.execute(
            tool = tool,
            request = "apply",
            authorization = UltraFrontierToolAuthorization(userConfirmedMutation = false)
        )

        val failure = assertIs<UltraToolResult.Failure>(blocked)
        assertEquals(UltraToolFailureCode.INVALID_INPUT, failure.failure.code)
        assertEquals(0, executions)

        val allowed = gateway.execute(
            tool = tool,
            request = "apply",
            authorization = UltraFrontierToolAuthorization(userConfirmedMutation = true)
        )

        assertEquals("changed", assertIs<UltraToolResult.Success<String>>(allowed).value)
        assertEquals(1, executions)
    }

    @Test
    fun mixedSideEffectToolAlsoRequiresConfirmation() {
        val tool = fakeTool(UltraToolSideEffect.MIXED) { "ok" }
        val gateway = UltraFrontierToolGateway()

        val result = gateway.execute(
            tool = tool,
            request = "mixed",
            authorization = UltraFrontierToolAuthorization()
        )

        assertIs<UltraToolResult.Failure>(result)
    }

    @Test
    fun readOnlyToolCanRunWithoutMutationConfirmation() {
        val tool = fakeTool(UltraToolSideEffect.READ_ONLY) { "observed" }
        val gateway = UltraFrontierToolGateway()

        val result = gateway.execute(
            tool = tool,
            request = "inspect",
            authorization = UltraFrontierToolAuthorization()
        )

        assertEquals("observed", assertIs<UltraToolResult.Success<String>>(result).value)
    }

    private fun fakeTool(
        sideEffect: UltraToolSideEffect,
        block: () -> String
    ): UltraToolContract<String, String> =
        object : UltraToolContract<String, String> {
            override val descriptor = UltraToolDescriptor(
                id = "frontier.test",
                kind = UltraToolKind.OPTIMIZER,
                sideEffect = sideEffect
            )

            override fun execute(request: String): UltraToolResult<String> =
                UltraToolResult.Success(block())
        }
}

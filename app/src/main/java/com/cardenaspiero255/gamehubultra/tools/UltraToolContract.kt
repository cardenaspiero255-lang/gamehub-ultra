package com.cardenaspiero255.gamehubultra.tools

import java.util.concurrent.CancellationException

enum class UltraToolKind {
    MEMORY,
    RESEARCH,
    COMMAND,
    OPTIMIZER,
    TELEMETRY
}

enum class UltraToolSideEffect {
    READ_ONLY,
    MUTATES_STATE,
    MIXED
}

enum class UltraToolFailureCode {
    INVALID_INPUT,
    UNAVAILABLE,
    EXECUTION_FAILED
}

data class UltraToolDescriptor(
    val id: String,
    val kind: UltraToolKind,
    val sideEffect: UltraToolSideEffect,
    val requiresNetwork: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Tool id must not be blank." }
        require(id == id.trim()) { "Tool id must not contain surrounding whitespace." }
        require(TOOL_ID_PATTERN.matches(id)) {
            "Tool id must use lowercase alphanumeric segments separated by '.', '-' or '_'."
        }
    }

    private companion object {
        val TOOL_ID_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
    }
}

data class UltraToolFailure(
    val toolId: String,
    val code: UltraToolFailureCode,
    val message: String,
    val causeType: String? = null
)

sealed interface UltraToolResult<out T> {
    data class Success<T>(val value: T) : UltraToolResult<T>
    data class Failure(val failure: UltraToolFailure) : UltraToolResult<Nothing>
}

interface UltraToolContract<in I, out O> {
    val descriptor: UltraToolDescriptor

    fun execute(request: I): UltraToolResult<O>
}

object UltraToolExecution {
    inline fun <T> protect(
        descriptor: UltraToolDescriptor,
        block: () -> T
    ): UltraToolResult<T> =
        try {
            UltraToolResult.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            UltraToolResult.Failure(
                UltraToolFailure(
                    toolId = descriptor.id,
                    code = UltraToolFailureCode.EXECUTION_FAILED,
                    message = "La herramienta no pudo completar la operación de forma segura.",
                    causeType = error.javaClass.simpleName.takeIf(String::isNotBlank)
                )
            )
        }
}

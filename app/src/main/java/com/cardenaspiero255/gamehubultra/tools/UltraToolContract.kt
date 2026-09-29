package com.cardenaspiero255.gamehubultra.tools

import io.sentry.Sentry
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

/**
 * Typed outcome shared by every Ultra tool boundary.
 *
 * Cancellation is intentionally distinct from failure. Execution helpers still propagate
 * [CancellationException] and thread interruption; [Cancelled] is for callers that need to
 * represent an already-observed cancellation without exposing a reason or internal detail.
 */
sealed interface UltraToolResult<out T> {
    data class Success<T>(val value: T) : UltraToolResult<T>
    data class Failure(val failure: UltraToolFailure) : UltraToolResult<Nothing>
    data class Cancelled(val toolId: String) : UltraToolResult<Nothing>
}

internal class UltraToolInvalidInputException(
    val safeMessage: String
) : IllegalArgumentException()

interface UltraToolContract<in I, out O> {
    val descriptor: UltraToolDescriptor

    fun execute(request: I): UltraToolResult<O>
}

object UltraToolExecution {
    /** Creates a sanitized typed cancellation associated only with the canonical tool id. */
    fun cancelled(descriptor: UltraToolDescriptor): UltraToolResult.Cancelled =
        UltraToolResult.Cancelled(toolId = descriptor.id)

    fun invalidInput(
        descriptor: UltraToolDescriptor,
        message: String = "La solicitud de la herramienta no es válida."
    ): UltraToolResult.Failure =
        UltraToolResult.Failure(
            UltraToolFailure(
                toolId = descriptor.id,
                code = UltraToolFailureCode.INVALID_INPUT,
                message = message
            )
        )

    fun <T> protect(
        descriptor: UltraToolDescriptor,
        reportFailure: (Throwable) -> Unit = ::reportInternalFailure,
        block: () -> T
    ): UltraToolResult<T> =
        try {
            UltraToolResult.Success(block())
        } catch (invalid: UltraToolInvalidInputException) {
            invalidInput(
                descriptor = descriptor,
                message = invalid.safeMessage
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Tool execution interrupted").apply {
                initCause(interrupted)
            }
        } catch (error: Exception) {
            runCatching { reportFailure(error) }
            UltraToolResult.Failure(
                UltraToolFailure(
                    toolId = descriptor.id,
                    code = UltraToolFailureCode.EXECUTION_FAILED,
                    message = "La herramienta no pudo completar la operación de forma segura.",
                    causeType = error.javaClass.simpleName.takeIf(String::isNotBlank)
                )
            )
        }

    private fun reportInternalFailure(error: Throwable) {
        Sentry.captureException(error)
    }
}

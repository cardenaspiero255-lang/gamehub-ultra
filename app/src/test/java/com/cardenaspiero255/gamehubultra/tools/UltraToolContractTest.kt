package com.cardenaspiero255.gamehubultra.tools

import android.content.Context
import com.cardenaspiero255.gamehubultra.GameInfo
import com.cardenaspiero255.gamehubultra.ai.UltraAnswerConfidence
import com.cardenaspiero255.gamehubultra.ai.UltraGeneralQueryKind
import com.cardenaspiero255.gamehubultra.ai.UltraGeneralQueryRequest
import com.cardenaspiero255.gamehubultra.ai.UltraLongTermMemoryGateway
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryRecall
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryScope
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryToolRequest
import com.cardenaspiero255.gamehubultra.ai.UltraMemoryToolResponse
import com.cardenaspiero255.gamehubultra.ai.UltraResearchGateway
import com.cardenaspiero255.gamehubultra.ai.UltraVerifiedResearchResult
import com.cardenaspiero255.gamehubultra.domain.AdaptivePerformanceEngine
import com.cardenaspiero255.gamehubultra.domain.AdaptiveRuntimeSnapshot
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnostics
import com.cardenaspiero255.gamehubultra.platform.RuntimeDiagnosticsProvider
import com.cardenaspiero255.gamehubultra.voice.VoiceActionResult
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandEngine
import com.cardenaspiero255.gamehubultra.voice.VoiceCommandExecutionRequest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraToolContractTest {

    @Test
    fun architectureExposesAllCoreCapabilitiesThroughOneToolContract() {
        val memory: UltraToolContract<UltraMemoryToolRequest, UltraMemoryToolResponse> =
            object : UltraLongTermMemoryGateway {
                override fun handleCommand(message: String, scope: UltraMemoryScope): String? = null

                override fun recallContext(
                    message: String,
                    scope: UltraMemoryScope,
                    limit: Int
                ): List<UltraMemoryRecall> = emptyList()
            }

        val research: UltraToolContract<UltraGeneralQueryRequest, UltraVerifiedResearchResult> =
            object : UltraResearchGateway {
                override fun answer(request: UltraGeneralQueryRequest) =
                    UltraVerifiedResearchResult(
                        message = "ok",
                        confidence = UltraAnswerConfidence.HIGH,
                        sources = listOf("source"),
                        abstained = false
                    )
            }

        val optimizer: UltraToolContract<AdaptiveRuntimeSnapshot, *> =
            AdaptivePerformanceEngine(confirmationsRequired = 1)

        val telemetry: UltraToolContract<Context, RuntimeDiagnostics> =
            RuntimeDiagnosticsProvider

        val commands: UltraToolContract<VoiceCommandExecutionRequest, VoiceActionResult> =
            VoiceCommandEngine

        val descriptors = listOf(
            memory.descriptor,
            research.descriptor,
            optimizer.descriptor,
            telemetry.descriptor,
            commands.descriptor
        )

        assertEquals(5, descriptors.map { it.id }.distinct().size)
        assertEquals(
            setOf(
                UltraToolKind.MEMORY,
                UltraToolKind.RESEARCH,
                UltraToolKind.COMMAND,
                UltraToolKind.OPTIMIZER,
                UltraToolKind.TELEMETRY
            ),
            descriptors.map { it.kind }.toSet()
        )
    }

    @Test
    fun researchBoundaryGetsTypedSuccessWithoutChangingItsExistingApi() {
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest) =
                UltraVerifiedResearchResult(
                    message = "dato verificado",
                    confidence = UltraAnswerConfidence.HIGH,
                    sources = listOf("source"),
                    abstained = false
                )
        }
        val request = UltraGeneralQueryRequest(
            originalText = "dato actual",
            kind = UltraGeneralQueryKind.CURRENT_DATA,
            requiresInternet = true,
            requiresFreshData = true,
            timeoutMillis = 5_000L
        )

        val result = gateway.execute(request)

        assertIs<UltraToolResult.Success<UltraVerifiedResearchResult>>(result)
        assertEquals("dato verificado", result.value.message)
        assertTrue(gateway.descriptor.requiresNetwork)
        assertEquals(UltraToolSideEffect.READ_ONLY, gateway.descriptor.sideEffect)
    }

    @Test
    fun memoryBoundaryUsesTypedRequestsForRecallAndCommands() {
        val gateway = object : UltraLongTermMemoryGateway {
            override fun handleCommand(message: String, scope: UltraMemoryScope): String? =
                if (message == "recuerda esto") "guardado" else null

            override fun recallContext(
                message: String,
                scope: UltraMemoryScope,
                limit: Int
            ): List<UltraMemoryRecall> = emptyList()
        }
        val scope = UltraMemoryScope(userId = "local", gamePackage = "game.a")

        val command = gateway.execute(
            UltraMemoryToolRequest.Command(
                message = "recuerda esto",
                scope = scope
            )
        )
        val recall = gateway.execute(
            UltraMemoryToolRequest.Recall(
                message = "algo",
                scope = scope,
                limit = 3
            )
        )

        assertIs<UltraToolResult.Success<UltraMemoryToolResponse>>(command)
        assertEquals(
            UltraMemoryToolResponse.CommandHandled("guardado"),
            command.value
        )
        assertIs<UltraToolResult.Success<UltraMemoryToolResponse>>(recall)
        assertEquals(UltraMemoryToolResponse.Recalled(emptyList()), recall.value)
        assertEquals(UltraToolSideEffect.MIXED, gateway.descriptor.sideEffect)
    }

    @Test
    fun optimizerAndCommandToolsPreserveExistingBehavior() {
        val optimizer = AdaptivePerformanceEngine(confirmationsRequired = 1)
        val decision = optimizer.execute(
            AdaptiveRuntimeSnapshot(
                thermalStatus = 0,
                thermalHeadroom = 0.2f,
                batteryPercent = 90,
                charging = false,
                powerSaveMode = false,
                sessionActive = true,
                sustainedPerformanceSupported = true,
                performanceHintsAvailable = true
            )
        )

        assertIs<UltraToolResult.Success<*>>(decision)
        assertEquals(UltraToolKind.OPTIMIZER, optimizer.descriptor.kind)

        val command = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(command = VoiceCommand.Help)
        )

        assertIs<UltraToolResult.Success<VoiceActionResult>>(command)
        assertIs<VoiceActionResult.Help>(command.value)
        assertEquals(UltraToolKind.COMMAND, VoiceCommandEngine.descriptor.kind)
    }

    @Test
    fun executionProtectionReturnsTypedFailureAndDoesNotLeakExceptionMessage() {
        val descriptor = UltraToolDescriptor(
            id = "test.failure",
            kind = UltraToolKind.COMMAND,
            sideEffect = UltraToolSideEffect.READ_ONLY
        )

        val result = UltraToolExecution.protect(descriptor) {
            error("secret-token-should-not-leak")
        }

        assertIs<UltraToolResult.Failure>(result)
        assertEquals(UltraToolFailureCode.EXECUTION_FAILED, result.failure.code)
        assertEquals("IllegalStateException", result.failure.causeType)
        assertTrue("secret-token-should-not-leak" !in result.failure.message)
    }

    @Test
    fun executionProtectionNeverSwallowsCancellation() {
        val descriptor = UltraToolDescriptor(
            id = "test.cancel",
            kind = UltraToolKind.TELEMETRY,
            sideEffect = UltraToolSideEffect.READ_ONLY
        )

        assertFailsWith<CancellationException> {
            UltraToolExecution.protect(descriptor) {
                throw CancellationException("cancel")
            }
        }
    }

    @Test
    fun researchToolConvertsProviderExceptionsToSafeTypedFailure() {
        val gateway = object : UltraResearchGateway {
            override fun answer(request: UltraGeneralQueryRequest): UltraVerifiedResearchResult {
                error("provider-secret-detail")
            }
        }
        val request = UltraGeneralQueryRequest(
            originalText = "dato actual",
            kind = UltraGeneralQueryKind.CURRENT_DATA,
            requiresInternet = true,
            requiresFreshData = true,
            timeoutMillis = 5_000L
        )

        val result = gateway.execute(request)

        assertIs<UltraToolResult.Failure>(result)
        assertEquals(UltraToolFailureCode.EXECUTION_FAILED, result.failure.code)
        assertEquals("IllegalStateException", result.failure.causeType)
        assertTrue("provider-secret-detail" !in result.failure.message)
    }

    @Test
    fun commandToolConvertsCallbackExceptionsToSafeTypedFailure() {
        val result = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(
                command = VoiceCommand.DeviceStatus,
                statusProvider = { error("runtime-private-detail") }
            )
        )

        assertIs<UltraToolResult.Failure>(result)
        assertEquals(UltraToolFailureCode.EXECUTION_FAILED, result.failure.code)
        assertEquals("IllegalStateException", result.failure.causeType)
        assertTrue("runtime-private-detail" !in result.failure.message)
    }

    @Test
    fun memoryRecallReturnsTypedInvalidInputForOutOfRangeLimit() {
        val gateway = object : UltraLongTermMemoryGateway {
            override fun handleCommand(message: String, scope: UltraMemoryScope): String? = null

            override fun recallContext(
                message: String,
                scope: UltraMemoryScope,
                limit: Int
            ): List<UltraMemoryRecall> = error("must not execute invalid request")
        }
        val scope = UltraMemoryScope(userId = "local", gamePackage = null)

        listOf(0, 51).forEach { invalidLimit ->
            val result = gateway.execute(
                UltraMemoryToolRequest.Recall(
                    message = "historial",
                    scope = scope,
                    limit = invalidLimit
                )
            )

            assertIs<UltraToolResult.Failure>(result)
            assertEquals(UltraToolFailureCode.INVALID_INPUT, result.failure.code)
        }
    }

    @Test
    fun stateChangingCommandRejectsMissingPersistenceCallback() {
        val result = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(
                command = VoiceCommand.SelectProfile(
                    com.cardenaspiero255.gamehubultra.domain.PerformanceProfile.BALANCED
                ),
                isProfileAvailable = { true }
            )
        )

        assertIs<UltraToolResult.Failure>(result)
        assertEquals(UltraToolFailureCode.INVALID_INPUT, result.failure.code)
    }

    @Test
    fun openGameWithCombinedPersistenceDoesNotRequireSeparateGameCallback() {
        val game = GameInfo(
            packageName = "game.a",
            label = "Game A"
        )
        var persisted: Pair<String, PerformanceProfile>? = null

        val result = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(
                command = VoiceCommand.OpenGame(
                    query = "Game A",
                    requestedProfile = PerformanceProfile.BALANCED
                ),
                gamesProvider = { listOf(game) },
                launchGame = { true },
                saveSelectedGameWithProfile = { packageName, profile ->
                    persisted = packageName to profile
                },
                isProfileAvailable = { true }
            )
        )

        assertIs<UltraToolResult.Success<VoiceActionResult>>(result)
        assertIs<VoiceActionResult.GameOpened>(result.value)
        assertEquals("game.a", persisted?.first)
        assertEquals(PerformanceProfile.BALANCED, persisted?.second)
    }

    @Test
    fun openGameWithUnavailableRequestedProfileOnlyRequiresGamePersistence() {
        val game = GameInfo(
            packageName = "game.a",
            label = "Game A"
        )
        var selectedGame: String? = null

        val result = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(
                command = VoiceCommand.OpenGame(
                    query = "Game A",
                    requestedProfile = PerformanceProfile.X4
                ),
                gamesProvider = { listOf(game) },
                launchGame = { true },
                saveSelectedGame = { selectedGame = it },
                isProfileAvailable = { false }
            )
        )

        assertIs<UltraToolResult.Success<VoiceActionResult>>(result)
        val opened = assertIs<VoiceActionResult.GameOpened>(result.value)
        assertEquals("game.a", selectedGame)
        assertTrue(opened.profileUnavailable)
        assertEquals(null, opened.profile)
    }

    @Test
    fun openGameWithAvailableProfileRejectsMissingProfilePersistenceBeforeAnyWrite() {
        val game = GameInfo(
            packageName = "game.a",
            label = "Game A"
        )
        var gamePersisted = false

        val result = VoiceCommandEngine.execute(
            VoiceCommandExecutionRequest(
                command = VoiceCommand.OpenGame(
                    query = "Game A",
                    requestedProfile = PerformanceProfile.BALANCED
                ),
                gamesProvider = { listOf(game) },
                launchGame = { true },
                saveSelectedGame = { gamePersisted = true },
                isProfileAvailable = { true }
            )
        )

        assertIs<UltraToolResult.Failure>(result)
        assertEquals(UltraToolFailureCode.INVALID_INPUT, result.failure.code)
        assertFalse(gamePersisted, "Invalid command must not partially persist the selected game.")
    }

    @Test
    fun executionProtectionReportsInternalFailureButKeepsPublicFailureSanitized() {
        val descriptor = UltraToolDescriptor(
            id = "test.observability",
            kind = UltraToolKind.COMMAND,
            sideEffect = UltraToolSideEffect.READ_ONLY
        )
        val captured = AtomicReference<Throwable?>()

        val result = UltraToolExecution.protect(
            descriptor = descriptor,
            reportFailure = captured::set
        ) {
            error("private-stack-detail")
        }

        assertIs<UltraToolResult.Failure>(result)
        assertEquals("private-stack-detail", captured.get()?.message)
        assertTrue("private-stack-detail" !in result.failure.message)
    }

    @Test
    fun interruptedExecutionRestoresInterruptAndPropagatesCancellation() {
        val descriptor = UltraToolDescriptor(
            id = "test.interrupt",
            kind = UltraToolKind.RESEARCH,
            sideEffect = UltraToolSideEffect.READ_ONLY
        )

        try {
            assertFailsWith<CancellationException> {
                UltraToolExecution.protect(descriptor) {
                    throw InterruptedException("stop")
                }
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun descriptorRejectsWhitespaceAndNonCanonicalIds() {
        assertFailsWith<IllegalArgumentException> {
            UltraToolDescriptor(
                id = " ultra.research",
                kind = UltraToolKind.RESEARCH,
                sideEffect = UltraToolSideEffect.READ_ONLY
            )
        }
        assertFailsWith<IllegalArgumentException> {
            UltraToolDescriptor(
                id = "Ultra Research",
                kind = UltraToolKind.RESEARCH,
                sideEffect = UltraToolSideEffect.READ_ONLY
            )
        }
    }

    @Test
    fun descriptorRejectsInvalidIds() {
        assertFailsWith<IllegalArgumentException> {
            UltraToolDescriptor(
                id = "  ",
                kind = UltraToolKind.RESEARCH,
                sideEffect = UltraToolSideEffect.READ_ONLY
            )
        }
    }
}

package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.voice.VoiceCommand
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UltraUnifiedAgentTest {
    @Test
    fun knownVoiceCommandStaysOnSafeActionPath() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, pon modo equilibrado",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.SelectProfile>(commandRoute.command)
        assertEquals(PerformanceProfile.BALANCED, command.profile)
    }

    @Test
    fun spanishExplicitGameLaunchUsesSafeCommandRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, abre Resident Evil 4 Remake",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
        assertEquals("resident evil 4 remake", command.query)
    }

    @Test
    fun englishExplicitGameLaunchUsesSafeCommandRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, open Minecraft",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
        assertEquals("minecraft", command.query)
    }

    @Test
    fun activationVerbGameLaunchUsesSafeCommandRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, activate Minecraft",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
        assertEquals("minecraft", command.query)
    }

    @Test
    fun freeFormSpeechFallsBackToConversationalAi() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, cuentame algo sobre mi juego",
            optionalResolver = null
        )

        val chatRoute = assertIs<UltraAgentRoute.Chat>(route)
        assertEquals("Ultra, cuentame algo sobre mi juego", chatRoute.message)
    }

    @Test
    fun explicitLaunchNamedLikeMetricStillOpensGame() {
        listOf(
            "Ultra, abre Hz" to "hz",
            "Ultra, open Refresh Rate" to "refresh rate"
        ).forEach { (transcript, expectedQuery) ->
            val route = UltraUnifiedAgentRouter.route(
                transcript = transcript,
                optionalResolver = null,
                telemetry = UltraRuntimeTelemetry(
                    batteryPercent = 80,
                    thermalLabel = "Normal",
                    refreshRateHz = 120f
                )
            )

            val commandRoute = assertIs<UltraAgentRoute.Command>(route)
            val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
            assertEquals(expectedQuery, command.query)
        }
    }

    @Test
    fun currentTimeQuestionUsesGeneralUtilityRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, qué hora es?",
            optionalResolver = null,
            clock = Clock.fixed(Instant.parse("2026-09-25T12:34:00Z"), ZoneOffset.UTC)
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.CurrentTime, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.canRunDuringGame)
        assertTrue(utilityRoute.answer.message.contains("12:34"))
    }

    @Test
    fun temperatureQuestionReturnsOnlyThermalUtility() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, dime la temperatura",
            optionalResolver = null,
            telemetry = UltraRuntimeTelemetry(
                batteryPercent = 79,
                thermalLabel = "Normal",
                refreshRateHz = 120f
            )
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.DeviceTemperature, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.message.contains("Normal"))
        assertFalse(utilityRoute.answer.message.contains("Batería"))
        assertFalse(utilityRoute.answer.message.contains("79"))
    }

    @Test
    fun batteryQuestionReturnsOnlyBatteryUtility() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, dime la batería",
            optionalResolver = null,
            telemetry = UltraRuntimeTelemetry(
                batteryPercent = 79,
                thermalLabel = "Normal",
                refreshRateHz = 120f
            )
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.BatteryStatus, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.message.contains("79"))
        assertFalse(utilityRoute.answer.message.contains("Normal"))
    }

    @Test
    fun refreshRateQuestionReturnsHzUtility() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, dime los hz",
            optionalResolver = null,
            telemetry = UltraRuntimeTelemetry(
                batteryPercent = 79,
                thermalLabel = "Normal",
                refreshRateHz = 120f
            )
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.RefreshRate, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.message.contains("120 Hz"))
    }

    @Test
    fun currentDateQuestionUsesGeneralUtilityRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, qué fecha es hoy?",
            optionalResolver = null,
            clock = Clock.fixed(Instant.parse("2026-09-25T12:34:00Z"), ZoneOffset.UTC)
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.CurrentDate, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.canRunDuringGame)
        assertTrue(utilityRoute.answer.message.contains("25/09/2026"))
    }

    @Test
    fun callInterruptionRequestExplainsOfficialAndroidRoutes() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra responde llamadas en segundo plano para que no interrumpan el juego",
            optionalResolver = null
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.CallInterruptionShield, utilityRoute.answer.intent)
        assertTrue(utilityRoute.answer.canRunDuringGame)
        assertTrue(utilityRoute.answer.requiresPhoneRoleOrPermission)
        assertTrue(utilityRoute.answer.message.contains("InCallService"))
        assertTrue(utilityRoute.answer.message.contains("ANSWER_PHONE_CALLS"))
        assertTrue(utilityRoute.answer.message.contains("Game Booster"))
    }

    @Test
    fun generalCapabilityRequestDoesNotOpenPhantomGame() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra quiero que hables conmigo de cosas que no sean de gaming",
            optionalResolver = null
        )

        val utilityRoute = assertIs<UltraAgentRoute.Utility>(route)
        assertEquals(UltraUtilityIntent.GeneralCapabilityHelp, utilityRoute.answer.intent)
        assertFalse(utilityRoute.answer.requiresPhoneRoleOrPermission)
        assertTrue(utilityRoute.answer.message.contains("temas generales"))
    }

    @Test
    fun sharedConversationKeepsRecentTurnsAcrossTextAndVoice() {
        var history = emptyList<String>()

        history = UltraConversationPolicy.append(history, "Tú: hola", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Ultra: hola", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Tú: abre RE4", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Ultra: abriendo RE4", maxEntries = 4)
        history = UltraConversationPolicy.append(history, "Tú: ¿y la temperatura?", maxEntries = 4)

        assertEquals(
            listOf(
                "Ultra: hola",
                "Tú: abre RE4",
                "Ultra: abriendo RE4",
                "Tú: ¿y la temperatura?"
            ),
            history
        )
    }

    @Test
    fun delayedAnswersAppendAgainstLatestConversationState() {
        var history = emptyList<String>()

        fun apply(entry: String) {
            history = UltraConversationPolicy.append(
                history = history,
                entry = entry,
                maxEntries = 8
            )
        }

        apply("Tú: voz")
        apply("Tú: texto")
        apply("Ultra: respuesta texto")
        apply("Ultra: respuesta voz")

        assertEquals(
            listOf(
                "Tú: voz",
                "Tú: texto",
                "Ultra: respuesta texto",
                "Ultra: respuesta voz"
            ),
            history
        )
    }

    @Test
    fun blankConversationEntriesAreIgnored() {
        assertEquals(
            listOf("Ultra: listo"),
            UltraConversationPolicy.append(
                history = listOf("Ultra: listo"),
                entry = "   ",
                maxEntries = 8
            )
        )
    }
    @Test
    fun ambiguousActionVerbsStayOnConversationPath() {
        listOf(
            "Ultra, set a timer",
            "Ultra, switch to dark mode",
            "Ultra, enable notifications",
            "Ultra, use the camera"
        ).forEach { transcript ->
            val route = UltraUnifiedAgentRouter.route(
                transcript = transcript,
                optionalResolver = null
            )
            val chat = assertIs<UltraAgentRoute.Chat>(route)
            assertEquals(transcript, chat.message)
        }
    }

    @Test
    fun profileActionTargetingGameRemainsCommandRoute() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "Ultra, switch to X4 for Minecraft",
            optionalResolver = null
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
        assertEquals("minecraft", command.query)
        assertEquals(PerformanceProfile.X4, command.requestedProfile)
    }

    @Test
    fun memoryCommandsContainingProfileNamesStayOnChatPath() {
        listOf(
            "Ultra elimina de tu memoria prefiero X4",
            "Ultra recuerda que prefiero balanced",
            "Ultra archiva mi preferencia de interpolación"
        ).forEach { transcript ->
            val route = UltraUnifiedAgentRouter.route(
                transcript = transcript,
                optionalResolver = null
            )
            val chat = assertIs<UltraAgentRoute.Chat>(route)
            assertEquals(transcript.trim(), chat.message)
        }
    }


    @Test
    fun bareLearnedAliasStaysOnCommandPath() {
        val route = UltraUnifiedAgentRouter.route(
            transcript = "BS",
            optionalResolver = null,
            knownGameAliases = setOf("bs")
        )

        val commandRoute = assertIs<UltraAgentRoute.Command>(route)
        val command = assertIs<VoiceCommand.OpenGame>(commandRoute.command)
        assertEquals("bs", command.query)
    }

}

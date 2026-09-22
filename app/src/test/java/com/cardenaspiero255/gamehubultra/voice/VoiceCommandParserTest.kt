package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VoiceCommandParserTest {
    @Test
    fun parsesSpanishGameOpen() {
        val command = VoiceCommandParser.parse("GameHub, abre Resident Evil 4 Remake")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("resident evil 4 remake", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun parsesGameOpenWithX4() {
        val command = VoiceCommandParser.parse("abre Resident Evil 4 Remake en X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("resident evil 4 remake", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun parsesProfileCommands() {
        assertEquals(VoiceCommand.SelectProfile(PerformanceProfile.BALANCED), VoiceCommandParser.parse("fps balanceado"))
        assertEquals(VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION), VoiceCommandParser.parse("prioriza interpolación"))
        assertEquals(VoiceCommand.SelectProfile(PerformanceProfile.X4), VoiceCommandParser.parse("configura todo"))
    }

    @Test
    fun parsesStatusAndHelp() {
        assertEquals(VoiceCommand.DeviceStatus, VoiceCommandParser.parse("dime la temperatura"))
        assertEquals(VoiceCommand.Help, VoiceCommandParser.parse("qué comandos puedo hacer"))
    }
}

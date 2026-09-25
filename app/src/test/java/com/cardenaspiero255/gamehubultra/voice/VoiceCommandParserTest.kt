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
    fun parsesEnglishGameOpenWithProfile() {
        val command = VoiceCommandParser.parse("open Resident Evil 4 Remake with X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("resident evil 4 remake", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun parsesSpanishGameOpenWithProfile() {
        val command = VoiceCommandParser.parse("abre Resident Evil 4 Remake en X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("resident evil 4 remake", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun preservesModeInsideGameTitleWhenProfileWasRequested() {
        val command = VoiceCommandParser.parse("abre Mode Runner en modo X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("mode runner", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun preservesProfileInsideGameTitleWhenProfileWasRequested() {
        val command = VoiceCommandParser.parse("open Player Profile Simulator with X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("player profile simulator", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun parsesEnglishProfiles() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            VoiceCommandParser.parse("balanced fps")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION),
            VoiceCommandParser.parse("prioritize interpolation")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("set X4")
        )
    }

    @Test
    fun parsesSpanishProfiles() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            VoiceCommandParser.parse("fps balanceado")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION),
            VoiceCommandParser.parse("prioriza interpolación")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("configura todo")
        )
    }

    @Test
    fun parsesStatusAndHelpInBothLanguages() {
        assertEquals(
            VoiceCommand.DeviceStatus,
            VoiceCommandParser.parse("dime la temperatura")
        )
        assertEquals(
            VoiceCommand.DeviceStatus,
            VoiceCommandParser.parse("what is the temperature")
        )
        assertEquals(
            VoiceCommand.Help,
            VoiceCommandParser.parse("qué comandos puedo hacer")
        )
        assertEquals(
            VoiceCommand.Help,
            VoiceCommandParser.parse("what can I do")
        )
    }

    @Test
    fun normalizesAccentsAndPunctuation() {
        assertEquals(
            VoiceCommand.DeviceStatus,
            VoiceCommandParser.parse("Dime, ¿la temperatura?")
        )
    }

    @Test
    fun parsesWakeWordSpanishCommand() {
        val command = VoiceCommandParser.parse("Ultra, abre Resident Evil 4 Remake")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("resident evil 4 remake", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun parsesWakeWordProfileCommands() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("Ultra, pon X4")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.FRAME_INTERPOLATION),
            VoiceCommandParser.parse("ULTRA prioriza interpolación")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            VoiceCommandParser.parse("Ultra FPS balanceado")
        )
    }

    @Test
    fun wakeWordIsRemovedBeforeNaturalLanguageResolution() {
        val command = VoiceCommandParser.parse("Ultra dime la temperatura")
        assertEquals(VoiceCommand.DeviceStatus, command)
    }

    @Test
    fun preservesModeAsGameTitleWhenNoProfileWasRequested() {
        val command = VoiceCommandParser.parse("open Mode")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("mode", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun preservesProfileInsideGameTitleWhenNoProfileWasRequested() {
        val command = VoiceCommandParser.parse("open Player Profile Simulator")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("player profile simulator", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun preservesTitleWordsWhenExplicitModeProfileIsRequested() {
        val command = VoiceCommandParser.parse("abre Mode Runner en modo X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("mode runner", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun removesCompleteX4ModeSuffixFromGameQuery() {
        val mode = assertIs<VoiceCommand.OpenGame>(VoiceCommandParser.parse("open Halo with X4 mode"))
        assertEquals("halo", mode.query)
        assertEquals(PerformanceProfile.X4, mode.requestedProfile)

        val profile = assertIs<VoiceCommand.OpenGame>(VoiceCommandParser.parse("open Halo with X4 profile"))
        assertEquals("halo", profile.query)
        assertEquals(PerformanceProfile.X4, profile.requestedProfile)
    }


    @Test
    fun preservesX4WhenItIsPartOfGameTitle() {
        val command = VoiceCommandParser.parse("open X4 Foundations")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("x4 foundations", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun unsafeShellLikeCommandsRemainUnknown() {
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("Ultra adb shell pm uninstall com.game"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("fastboot reboot"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("settings put global animator_duration_scale 0"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("am force-stop com.example.game"))
    }
}

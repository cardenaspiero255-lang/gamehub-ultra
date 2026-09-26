package com.cardenaspiero255.gamehubultra.voice

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VoiceCommandParserTest {
    @Test
    fun preservesUltraInsideExplicitGameTitle() {
        val direct = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("open Ultra Racing")
        )
        assertEquals("ultra racing", direct.query)

        val withWakeWord = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("Ultra, open Ultra Racing")
        )
        assertEquals("ultra racing", withWakeWord.query)
    }

    @Test
    fun preservesUltraInsideAliasTargetTitle() {
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "ur", gameQuery = "ultra racing"),
            VoiceCommandParser.parse("Ultra, when I say UR open Ultra Racing")
        )
    }

    @Test
    fun stripsOnlyLeadingAssistantInvocation() {
        val command = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("GameHub Ultra, open Ultra Racing")
        )
        assertEquals("ultra racing", command.query)

        assertEquals(
            VoiceCommand.DeviceStatus,
            VoiceCommandParser.parse("Ultra, dime la temperatura")
        )
    }

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
    fun parsesProfileToGameConnectorsForNewActionVerbs() {
        val apply = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("apply X4 to Minecraft")
        )
        assertEquals("minecraft", apply.query)
        assertEquals(PerformanceProfile.X4, apply.requestedProfile)

        val set = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("set X4 for Minecraft")
        )
        assertEquals("minecraft", set.query)
        assertEquals(PerformanceProfile.X4, set.requestedProfile)
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
    fun selectionVerbsWithProfileSuffixStayProfileCommands() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            VoiceCommandParser.parse("select balanced profile")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("activate X4 profile")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.BALANCED),
            VoiceCommandParser.parse("selecciona perfil balanceado")
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

    @Test
    fun profileActivationVerbDoesNotBecomeGameQuery() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("Ultra activa X4")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("Ultra activa el modo X4")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("Ultra activate X4")
        )
    }


    @Test
    fun profileOnlySwitchConnectorsStayProfileCommands() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("switch to X4 mode")
        )
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("cambia al modo X4")
        )
    }


    @Test
    fun openMeUsesCompleteLaunchVerb() {
        val command = VoiceCommandParser.parse("open me Minecraft")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("minecraft", parsed.query)
        assertEquals(null, parsed.requestedProfile)
    }

    @Test
    fun profileToGameWithLeadingAndTrailingConnectorsKeepsGameAndProfile() {
        val command = VoiceCommandParser.parse("switch to X4 for Minecraft")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("minecraft", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }


    @Test
    fun markerFirstProfileToGameCommandsConsumeTrailingConnector() {
        val english = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("apply profile X4 to Minecraft")
        )
        assertEquals("minecraft", english.query)
        assertEquals(PerformanceProfile.X4, english.requestedProfile)

        val spanish = assertIs<VoiceCommand.OpenGame>(
            VoiceCommandParser.parse("activa modo X4 para Minecraft")
        )
        assertEquals("minecraft", spanish.query)
        assertEquals(PerformanceProfile.X4, spanish.requestedProfile)
    }

    @Test
    fun ambiguousActionVerbsWithoutGamingContextStayUnknown() {
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("Ultra set a timer"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("Ultra switch to dark mode"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("Ultra enable notifications"))
        assertIs<VoiceCommand.Unknown>(VoiceCommandParser.parse("Ultra use the camera"))
    }


    @Test
    fun opensGameAndAppliesX4FromSingleSpanishCommand() {
        val command = VoiceCommandParser.parse("Ultra, abre Brawl Stars y activa X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("brawl stars", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }


    @Test
    fun combinedProfileActionPreservesProfileTokenInsideGameTitle() {
        val command = VoiceCommandParser.parse("Ultra, abre X4 Foundations y activa X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("x4 foundations", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun repeatedProfileOnlyCommandDoesNotBecomeGameLaunch() {
        assertEquals(
            VoiceCommand.SelectProfile(PerformanceProfile.X4),
            VoiceCommandParser.parse("Ultra activa el modo X4 y activa X4")
        )
    }

    @Test
    fun combinedSpanishProfileActionAcceptsArticleBeforeMode() {
        val command = VoiceCommandParser.parse("Ultra abre Halo y activa el modo X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("halo", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun combinedEnglishProfileActionAcceptsSwitchToConnector() {
        val command = VoiceCommandParser.parse("Ultra open Halo and switch to X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("halo", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }

    @Test
    fun appendedProfileWinsOverProfileWordInsideGameTitle() {
        val command = VoiceCommandParser.parse("Ultra, open Balanced Adventure and activate X4")
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("balanced adventure", parsed.query)
        assertEquals(PerformanceProfile.X4, parsed.requestedProfile)
    }


    @Test
    fun parsesUserDefinedGameAliasInstruction() {
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "bs", gameQuery = "brawl stars"),
            VoiceCommandParser.parse("Ultra, cuando diga BS abre Brawl Stars")
        )
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "re4r", gameQuery = "resident evil 4 remake"),
            VoiceCommandParser.parse("cuando diga RE4R quiero que abras Resident Evil 4 Remake")
        )
    }



    @Test
    fun cleansLaunchFillerFromAliasTarget() {
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "bs", gameQuery = "brawl stars"),
            VoiceCommandParser.parse("cuando diga BS abre el juego Brawl Stars por favor")
        )
    }


    @Test
    fun stripsAssistantPrefixWhenDefiningAlias() {
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "bs", gameQuery = "brawl stars"),
            VoiceCommandParser.parse("when I say GameHub BS open Brawl Stars")
        )
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "bs", gameQuery = "brawl stars"),
            VoiceCommandParser.parse("cuando diga Ultra B S abre Brawl Stars")
        )
    }

    @Test
    fun canonicalizesSpacedInitialsWhenDefiningAlias() {
        assertEquals(
            VoiceCommand.DefineGameAlias(alias = "bs", gameQuery = "brawl stars"),
            VoiceCommandParser.parse("Ultra, cuando diga B S abre Brawl Stars")
        )
    }



    @Test
    fun resolverOwnedPhrasePreemptsPersistedAlias() {
        val resolver = object : NaturalLanguageIntentResolver {
            override fun resolve(transcript: String): VoiceCommand? =
                if (transcript == "optimize my game") VoiceCommand.AskAi(transcript) else null
        }

        assertEquals(
            VoiceCommand.AskAi("optimize my game"),
            VoiceCommandParser.parse(
                transcript = "optimize my game",
                optionalResolver = resolver,
                knownGameAliases = setOf("optimize my game")
            )
        )
    }


    @Test
    fun bareLearnedAliasParsesAsGameLaunch() {
        val command = VoiceCommandParser.parse(
            transcript = "BS",
            knownGameAliases = setOf("bs")
        )
        val parsed = assertIs<VoiceCommand.OpenGame>(command)
        assertEquals("bs", parsed.query)
    }

}

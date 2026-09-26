package com.cardenaspiero255.gamehubultra.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VoiceAliasProcessConfigTest {
    @Test
    fun voiceSessionSharesMainProcessWithAliasStore() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull(File::isFile)

        assertNotNull(manifest, "AndroidManifest.xml must be available to this regression test")
        val xml = manifest.readText()
        val service = Regex(
            """<service\s+[^>]*android:name="\.voice\.GameHubVoiceInteractionSessionService"[^>]*/>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(xml)?.value

        assertNotNull(service, "Voice interaction session service declaration must exist")
        assertFalse(
            service.contains("android:process="),
            "Alias storage is process-local; the voice session must share the app process"
        )
    }

    @Test
    fun aliasConfirmationIsLocalizedAcrossVoiceServices() {
        val sourceRoots = sequenceOf(
            File("src/main/java"),
            File("app/src/main/java")
        )
        val sourceRoot = sourceRoots.firstOrNull(File::isDirectory)
        assertNotNull(sourceRoot, "Main Java/Kotlin source root must be available")

        val files = listOf(
            File(sourceRoot, "com/cardenaspiero255/gamehubultra/voice/GameHubVoiceInteractionService.kt"),
            File(sourceRoot, "com/cardenaspiero255/gamehubultra/voice/UltraWakeService.kt")
        )

        files.forEach { file ->
            assertTrue(file.isFile, "${file.name} must exist")
            val source = file.readText()
            assertTrue(
                source.contains("R.string.voice_result_game_alias_saved"),
                "${file.name} must use the localized alias confirmation resource"
            )
            assertFalse(
                source.contains("\"Alias ${result.alias.uppercase()} guardado para ${result.game.label}.\""),
                "${file.name} must not hard-code the Spanish alias confirmation"
            )
        }
    }

}

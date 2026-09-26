package com.cardenaspiero255.gamehubultra.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

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
}

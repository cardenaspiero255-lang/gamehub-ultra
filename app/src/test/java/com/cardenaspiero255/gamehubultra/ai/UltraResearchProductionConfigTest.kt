package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraResearchProductionConfigTest {
    @Test
    fun blankConfiguredUrlUsesPublicProjectFallback() {
        assertEquals(
            "https://upkmszocqiqslrxuxevx.supabase.co",
            UltraResearchProductionConfig.resolveSupabaseUrl("")
        )
        assertEquals(
            "https://upkmszocqiqslrxuxevx.supabase.co",
            UltraResearchProductionConfig.resolveSupabaseUrl("   ")
        )
    }

    @Test
    fun explicitConfiguredUrlIsPreserved() {
        assertEquals(
            "https://custom-project.supabase.co",
            UltraResearchProductionConfig.resolveSupabaseUrl(
                "  https://custom-project.supabase.co  "
            )
        )
    }
    @Test
    fun blankPublishableKeyUsesPackagedPublicClientFallback() {
        val key = UltraResearchProductionConfig.resolvePublishableKey("")
        assertTrue(key.startsWith("sb_publishable_"))
        assertTrue(key.length > "sb_publishable_".length)
    }

    @Test
    fun explicitPublishableKeyIsPreserved() {
        assertEquals(
            "sb_publishable_test-key",
            UltraResearchProductionConfig.resolvePublishableKey("  sb_publishable_test-key  ")
        )
    }
}

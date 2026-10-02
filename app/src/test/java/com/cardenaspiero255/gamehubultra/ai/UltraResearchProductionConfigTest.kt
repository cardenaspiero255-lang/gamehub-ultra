package com.cardenaspiero255.gamehubultra.ai

import kotlin.test.Test
import kotlin.test.assertEquals

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
}

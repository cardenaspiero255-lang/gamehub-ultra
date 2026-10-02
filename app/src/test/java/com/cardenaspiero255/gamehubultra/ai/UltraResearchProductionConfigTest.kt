package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltraResearchProductionConfigTest {
    @Test
    fun productionResearchUrlHasPublicProjectFallback() {
        assertTrue(BuildConfig.SUPABASE_URL.isNotBlank())
        assertEquals(
            "https://upkmszocqiqslrxuxevx.supabase.co",
            BuildConfig.SUPABASE_URL
        )
    }
}

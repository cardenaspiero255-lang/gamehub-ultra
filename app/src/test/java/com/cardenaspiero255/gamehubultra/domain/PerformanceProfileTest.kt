package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.CpuInfoParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceProfileTest {
    @Test
    fun profilesExposeStableTitlesAndSafeIntents() {
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)

        assertFalse(PerformanceProfile.BALANCED.sustainedPerformanceIntent)
        assertTrue(PerformanceProfile.FRAME_INTERPOLATION.frameInterpolationIntent)
        assertTrue(PerformanceProfile.X4.sustainedPerformanceIntent)
    }

    @Test
    fun interpolationProfileDoesNotClaimUnsupportedControl() {
        val description = PerformanceProfile.FRAME_INTERPOLATION.description
        assertTrue(description.contains("API compatible"))
        assertTrue(description.contains("no expone una API pública"))
        assertFalse(description.contains("activa interpolación"))
    }

    @Test
    fun x4ProfileIsExplicitAboutItsScope() {
        val description = PerformanceProfile.X4.description
        assertTrue(description.contains("GameHub Ultra"))
        assertTrue(description.contains("solo cuando Android y el dispositivo lo soportan"))
        assertFalse(description.contains("multiplicador de frames"))
    }

    @Test
    fun parsesCpuModelFromCommonLinuxFormats() {
        val info = """
            processor : 0
            model name : ARMv8 Processor rev 4 (v8l)
            Hardware : Generic Device
        """.trimIndent()

        assertEquals("ARMv8 Processor rev 4 (v8l)", CpuInfoParser.parseModel(info))
    }

    @Test
    fun parsesHardwareWhenModelNameIsMissing() {
        val info = "Hardware : Qualcomm Technologies, Inc. SM8650"
        assertEquals("Qualcomm Technologies, Inc. SM8650", CpuInfoParser.parseModel(info))
    }

    @Test
    fun returnsNullWhenCpuInfoHasNoKnownModelKey() {
        assertNull(CpuInfoParser.parseModel("processor : 0\nflags : neon"))
    }
}

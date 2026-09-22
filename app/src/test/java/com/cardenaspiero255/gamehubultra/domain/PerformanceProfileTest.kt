package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.CpuInfoParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceProfileTest {
    @Test
    fun profilesHaveStableTitles() {
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)
    }

    @Test
    fun interpolationProfileDoesNotClaimUnsupportedControl() {
        assertTrue(PerformanceProfile.FRAME_INTERPOLATION.description.contains("No puede forzar"))
        assertFalse(PerformanceProfile.FRAME_INTERPOLATION.description.contains("activa interpolación"))
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

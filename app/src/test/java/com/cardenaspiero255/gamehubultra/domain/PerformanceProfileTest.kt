package com.cardenaspiero255.gamehubultra.domain

import com.cardenaspiero255.gamehubultra.platform.CpuInfoParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceProfileTest {
    @Test
    fun profilesExposeCompleteIntentMatrix() {
        assertEquals("FPS balanceado", PerformanceProfile.BALANCED.title)
        assertEquals("Priorizar interpolación", PerformanceProfile.FRAME_INTERPOLATION.title)
        assertEquals("X4", PerformanceProfile.X4.title)

        assertFalse(PerformanceProfile.BALANCED.sustainedPerformanceIntent)
        assertFalse(PerformanceProfile.BALANCED.frameInterpolationIntent)
        assertFalse(PerformanceProfile.BALANCED.acceptsHigherTemperature)

        assertFalse(PerformanceProfile.FRAME_INTERPOLATION.sustainedPerformanceIntent)
        assertTrue(PerformanceProfile.FRAME_INTERPOLATION.frameInterpolationIntent)
        assertTrue(PerformanceProfile.FRAME_INTERPOLATION.acceptsHigherTemperature)

        assertTrue(PerformanceProfile.X4.sustainedPerformanceIntent)
        assertFalse(PerformanceProfile.X4.frameInterpolationIntent)
        assertFalse(PerformanceProfile.X4.acceptsHigherTemperature)
    }

    @Test
    fun interpolationProfileCommunicatesThermalTradeoffAndPlatformLimit() {
        val description = PerformanceProfile.FRAME_INTERPOLATION.description
        assertTrue(description.contains("mayor coste térmico"))
        assertTrue(description.contains("API compatible"))
        assertTrue(description.contains("no expone una API pública"))
        assertFalse(description.contains("activa interpolación"))
    }

    @Test
    fun x4ProfileIsExplicitAboutItsScope() {
        val description = PerformanceProfile.X4.description
        assertTrue(description.contains("GameHub Ultra"))
        assertTrue(description.contains("solo cuando Android y el dispositivo lo soportan"))
        assertTrue(description.contains("No proporciona generación de frames"))
    }

    @Test
    fun sustainedModeRequiresProfileIntentAndBothCapabilityGuards() {
        assertTrue(
            PerformanceController.shouldEnableSustainedMode(
                PerformanceProfile.X4,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                PerformanceProfile.X4,
                platformSupportsSustainedMode = false,
                deviceSupportsSustainedMode = true
            )
        )
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                PerformanceProfile.X4,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = false
            )
        )
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                PerformanceProfile.BALANCED,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
        assertFalse(
            PerformanceController.shouldEnableSustainedMode(
                PerformanceProfile.FRAME_INTERPOLATION,
                platformSupportsSustainedMode = true,
                deviceSupportsSustainedMode = true
            )
        )
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

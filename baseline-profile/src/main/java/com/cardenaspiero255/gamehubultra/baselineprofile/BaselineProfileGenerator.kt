package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreNavigation() = baselineProfileRule.collect(
        packageName = "com.cardenaspiero255.gamehubultra"
    ) {
        // Baseline collection must not depend on UI text, resource IDs, locale,
        // accessibility merging, or emulator timing after startup. The dedicated
        // macrobenchmark suite validates navigation separately.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}

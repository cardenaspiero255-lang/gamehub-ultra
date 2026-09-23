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
    fun startup() = baselineProfileRule.collect(
        packageName = "com.cardenaspiero255.gamehubultra"
    ) {
        // Baseline collection must not depend on UI text, resource IDs, locale,
        // accessibility merging, or a single fragile emulator timing window.
        // Navigation is covered independently by Macrobenchmark tests.
        var started = false
        repeat(3) {
            if (started) return@repeat
            pressHome()
            runCatching {
                startActivityAndWait()
                device.waitForIdle()
            }.onSuccess {
                started = true
            }.onFailure {
                Thread.sleep(750)
            }
        }
        check(started) { "GameHub Ultra failed to start after 3 attempts" }
    }
}

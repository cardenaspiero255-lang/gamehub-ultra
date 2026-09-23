package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
        startActivityAndWait()
        device.waitForIdle()

        val navigation = device.wait(
            Until.findObject(By.res("com.cardenaspiero255.gamehubultra:id/nav_biblioteca")),
            10_000
        ) ?: device.wait(
            Until.findObject(By.desc("nav_biblioteca")),
            10_000
        ) ?: device.wait(
            Until.findObject(By.text("BIBLIOTECA")),
            10_000
        ) ?: error("Library navigation item not found after startup")

        navigation.click()
        device.waitForIdle()
    }
}

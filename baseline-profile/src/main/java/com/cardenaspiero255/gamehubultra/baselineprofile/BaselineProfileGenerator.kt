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
        val libraryNavigation = By.res("nav_biblioteca")
        check(device.wait(Until.hasObject(libraryNavigation), 5_000)) {
            "Library navigation item not found"
        }
        device.findObject(libraryNavigation).click()
        device.waitForIdle()
    }
}

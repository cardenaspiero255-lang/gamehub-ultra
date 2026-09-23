package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreNavigation() = baselineProfileRule.collect(
        packageName = "com.cardenaspiero255.gamehubultra"
    ) {
        startActivityAndWait()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val libraryId = targetContext.resources.getIdentifier(
            "nav_biblioteca",
            "string",
            targetContext.packageName
        )
        check(libraryId != 0) { "Missing target string resource: nav_biblioteca" }
        val libraryLabel = targetContext.getString(libraryId)
        check(device.wait(Until.hasObject(By.text(libraryLabel)), 5_000)) {
            "Library navigation item not found"
        }
        device.findObject(By.text(libraryLabel)).click()
        device.waitForIdle()
    }
}

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

        val appPackage = "com.cardenaspiero255.gamehubultra"
        check(device.wait(Until.hasObject(By.pkg(appPackage)), 15_000)) {
            "GameHub Ultra activity did not become visible"
        }

        // The release test process can resolve resources differently from the target
        // app context. Use the user-visible label as the stable UI contract instead.
        val libraryLabel = "BIBLIOTECA"
        val resourceSelector = By.res("nav_biblioteca")
        val textSelector = By.text(libraryLabel)

        val navigationFound =
            device.wait(Until.hasObject(resourceSelector), 15_000) ||
                device.wait(Until.hasObject(textSelector), 5_000)

        check(navigationFound) {
            "Library navigation item not found. label=$libraryLabel"
        }

        val navigation = if (device.hasObject(resourceSelector)) {
            device.findObject(resourceSelector)
        } else {
            device.findObject(textSelector)
        }

        navigation.click()
        device.waitForIdle()
    }
}

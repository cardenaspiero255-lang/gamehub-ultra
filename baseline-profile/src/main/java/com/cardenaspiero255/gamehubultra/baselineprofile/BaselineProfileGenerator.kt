package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        val appPackage = "com.cardenaspiero255.gamehubultra"
        check(device.wait(Until.hasObject(By.pkg(appPackage)), 15_000)) {
            "GameHub Ultra activity did not become visible"
        }

        startActivityAndWait()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val libraryStringId = targetContext.resources.getIdentifier(
            "nav_biblioteca",
            "string",
            appPackage
        )
        val libraryLabel = checkNotNull(
            libraryStringId.takeIf { it != 0 }?.let(targetContext.resources::getString)
        ) {
            "Library navigation label resource not found"
        }

        val resourceSelector = By.res("nav_biblioteca")
        val descriptionSelector = By.desc(libraryLabel)
        val textSelector = By.text(libraryLabel)

        val navigationFound =
            device.wait(Until.hasObject(resourceSelector), 15_000) ||
                device.wait(Until.hasObject(descriptionSelector), 5_000) ||
                device.wait(Until.hasObject(textSelector), 5_000)

        check(navigationFound) {
            "Library navigation item not found. label=$libraryLabel"
        }

        val navigation = when {
            device.hasObject(resourceSelector) -> device.findObject(resourceSelector)
            device.hasObject(descriptionSelector) -> device.findObject(descriptionSelector)
            else -> device.findObject(textSelector)
        }

        navigation.click()
        device.waitForIdle()
    }
}

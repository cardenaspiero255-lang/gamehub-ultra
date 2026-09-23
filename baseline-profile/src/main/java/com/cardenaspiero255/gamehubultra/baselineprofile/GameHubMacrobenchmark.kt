package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHubMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.cardenaspiero255.gamehubultra",
        metrics = listOf(
            androidx.benchmark.macro.StartupTimingMetric()
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
        }
    )

    @Test
    fun navigationToLibrary() = benchmarkRule.measureRepeated(
        packageName = "com.cardenaspiero255.gamehubultra",
        metrics = listOf(
            androidx.benchmark.macro.FrameTimingMetric()
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
            val libraryLabel = targetString("nav_biblioteca")
            check(device.wait(Until.hasObject(By.text(libraryLabel)), 5_000)) {
                "Library navigation item not found"
            }
            device.findObject(By.text(libraryLabel)).click()
            device.waitForIdle()
        }
    )

    @Test
    fun navigationToSettings() = benchmarkRule.measureRepeated(
        packageName = "com.cardenaspiero255.gamehubultra",
        metrics = listOf(
            androidx.benchmark.macro.FrameTimingMetric()
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()
            val settingsLabel = targetString("nav_ajustes")
            check(device.wait(Until.hasObject(By.text(settingsLabel)), 5_000)) {
                "Settings navigation item not found"
            }
            device.findObject(By.text(settingsLabel)).click()
            device.waitForIdle()
        }
    )
}

private fun targetString(resourceName: String): String {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val resourceId = targetContext.resources.getIdentifier(
        resourceName,
        "string",
        targetContext.packageName
    )
    check(resourceId != 0) { "Missing target string resource: $resourceName" }
    return targetContext.getString(resourceId)
}

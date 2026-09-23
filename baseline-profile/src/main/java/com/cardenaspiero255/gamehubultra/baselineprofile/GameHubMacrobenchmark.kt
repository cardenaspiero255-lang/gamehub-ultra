package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            startActivityAndWait()
            val libraryNavigation = By.res("nav_biblioteca")
            check(device.wait(Until.hasObject(libraryNavigation), 5_000)) {
                "Library navigation item not found"
            }
        },
        measureBlock = {
            check(device.wait(Until.hasObject(libraryNavigation), 5_000)) {
                "Library navigation item not found"
            }
            device.findObject(libraryNavigation).click()
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
            startActivityAndWait()
            val settingsNavigation = By.res("nav_ajustes")
            check(device.wait(Until.hasObject(settingsNavigation), 5_000)) {
                "Settings navigation item not found"
            }
        },
        measureBlock = {
            check(device.wait(Until.hasObject(settingsNavigation), 5_000)) {
                "Settings navigation item not found"
            }
            device.findObject(settingsNavigation).click()
            device.waitForIdle()
        }
    )
}

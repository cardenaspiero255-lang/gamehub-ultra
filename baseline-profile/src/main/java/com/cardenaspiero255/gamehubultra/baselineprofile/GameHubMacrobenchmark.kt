package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHubMacrobenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun requireNavigationTarget(description: String, visibleText: String): androidx.test.uiautomator.UiObject2 =
        requireNotNull(
            device.wait(
                Until.findObject(By.res("com.cardenaspiero255.gamehubultra:id/$description")),
                3_000
            ) ?: device.wait(
                Until.findObject(By.desc(description)),
                3_000
            ) ?: device.wait(
                Until.findObject(By.text(visibleText)),
                3_000
            )
        ) {
            "Navigation target not found: $description / $visibleText"
        }

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "com.cardenaspiero255.gamehubultra",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() }
    )

    @Test
    fun navigationToLibrary() {
        val targetDescription = "nav_biblioteca"
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                requireNavigationTarget(targetDescription, "BIBLIOTECA")
            },
            measureBlock = {
                requireNavigationTarget(targetDescription, "BIBLIOTECA").click()
                device.waitForIdle()
            }
        )
    }

    @Test
    fun navigationToSettings() {
        val targetDescription = "nav_ajustes"
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                requireNavigationTarget(targetDescription, "⚙")
            },
            measureBlock = {
                requireNavigationTarget(targetDescription, "⚙").click()
                device.waitForIdle()
            }
        )
    }
}

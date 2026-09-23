package com.cardenaspiero255.gamehubultra.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.FrameTimingMetric
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
        val target = By.res("nav_biblioteca")
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                check(device.wait(Until.hasObject(target), 5_000))
            },
            measureBlock = { device.findObject(target).click(); device.waitForIdle() }
        )
    }

    @Test
    fun navigationToSettings() {
        val target = By.res("nav_ajustes")
        benchmarkRule.measureRepeated(
            packageName = "com.cardenaspiero255.gamehubultra",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                check(device.wait(Until.hasObject(target), 5_000))
            },
            measureBlock = { device.findObject(target).click(); device.waitForIdle() }
        )
    }
}
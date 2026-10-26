package com.cardenaspiero255.gamehubultra.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.cardenaspiero255.gamehubultra.performance.pressure.PressureLevel
import com.cardenaspiero255.gamehubultra.performance.pressure.PressureTrend
import com.cardenaspiero255.gamehubultra.performance.pressure.ResourcePressureReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResourcePressureCardTest {
    @get:Rule val rule = createComposeRule()

    private fun report(
        ram: PressureLevel,
        disk: PressureLevel,
        trend: PressureTrend,
        ramPercent: Int? = 50,
        diskPercent: Int? = 45,
        notes: List<String> = emptyList()
    ) = ResourcePressureReport(ram, disk, ramPercent, diskPercent, trend, notes)

    @Test fun missingDiagnosticsShowsNoFakeHealthyState() {
        rule.setContent { ResourcePressureCard(null) }
        rule.onNodeWithTag("car52_pressure_card").assertExists()
        rule.onNodeWithText("Esperando telemetría real de Android").assertExists()
    }

    @Test fun realNormalRamAndLimitedStorageAreVisible() {
        rule.setContent {
            ResourcePressureCard(report(PressureLevel.NORMAL, PressureLevel.ELEVATED,
                PressureTrend.INCREASING, notes = listOf("Almacenamiento limitado")))
        }
        rule.onNodeWithText("RAM disponible: 50% · presión normal").assertExists()
        rule.onNodeWithText("Almacenamiento libre: 45% · presión elevada").assertExists()
        rule.onNodeWithText("Tendencia de presión RAM: en aumento").assertExists()
        rule.onNodeWithText("Almacenamiento limitado").assertExists()
    }

    @Test fun criticalRamAndRecoveryAreDisplayedWithoutPrivilegeClaims() {
        rule.setContent {
            ResourcePressureCard(report(PressureLevel.CRITICAL, PressureLevel.CRITICAL,
                PressureTrend.RECOVERING, ramPercent = 3, diskPercent = 1))
        }
        rule.onNodeWithText("RAM disponible: 3% · presión crítica").assertExists()
        rule.onNodeWithText("Almacenamiento libre: 1% · presión crítica").assertExists()
        rule.onNodeWithText("Tendencia de presión RAM: disminuyendo").assertExists()
    }

    @Test fun absentMetricsStayUnknownAndNotMeasured() {
        rule.setContent {
            ResourcePressureCard(report(PressureLevel.UNKNOWN, PressureLevel.UNKNOWN,
                PressureTrend.INSUFFICIENT_HISTORY, null, null))
        }
        rule.onNodeWithText("RAM disponible: no medida · presión desconocida").assertExists()
        rule.onNodeWithText("Almacenamiento libre: no medido · presión desconocida").assertExists()
        rule.onNodeWithText("Tendencia de presión RAM: historial insuficiente").assertExists()
    }

    @Test fun elevatedRamStableHistoryIsReadable() {
        rule.setContent {
            ResourcePressureCard(report(PressureLevel.ELEVATED, PressureLevel.NORMAL, PressureTrend.STABLE))
        }
        rule.onNodeWithText("RAM disponible: 50% · presión elevada").assertExists()
        rule.onNodeWithText("Almacenamiento libre: 45% · presión normal").assertExists()
        rule.onNodeWithText("Tendencia de presión RAM: estable").assertExists()
    }
}

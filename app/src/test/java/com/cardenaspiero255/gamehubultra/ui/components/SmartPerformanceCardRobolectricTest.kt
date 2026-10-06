package com.cardenaspiero255.gamehubultra.ui.components

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.cardenaspiero255.gamehubultra.R
import com.cardenaspiero255.gamehubultra.domain.DriverStrategy
import com.cardenaspiero255.gamehubultra.domain.GpuFamily
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.domain.RecommendationConfidenceBand
import com.cardenaspiero255.gamehubultra.domain.RecommendationEvidence
import com.cardenaspiero255.gamehubultra.domain.RecommendationEvidenceProvenance
import com.cardenaspiero255.gamehubultra.domain.RecommendationOutcome
import com.cardenaspiero255.gamehubultra.domain.RecommendationOutcomeObjective
import com.cardenaspiero255.gamehubultra.domain.SmartPerformanceRecommendation
import com.cardenaspiero255.gamehubultra.domain.SmartRecommendationExplanation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmartPerformanceCardRobolectricTest {
    private fun recommendation(
        strategy: DriverStrategy = DriverStrategy.SYSTEM_ONLY,
        explanation: SmartRecommendationExplanation =
            SmartRecommendationExplanation.legacy(
                reason = "Mantener estabilidad térmica.",
                evidence = listOf("battery=80", "thermal=normal")
            )
    ) = SmartPerformanceRecommendation(
        profile = PerformanceProfile.BALANCED,
        reason = explanation.reason,
        safeFallback = PerformanceProfile.BALANCED,
        evidence = explanation.evidence.map { it.text },
        score = 92,
        driverStrategy = strategy,
        gpuFamily = GpuFamily.MALI,
        explanation = explanation
    )

    @Test
    fun `smart performance actions render with real compose wiring`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()

        activity.setContent {
            MaterialTheme {
                SmartPerformanceCard(
                    recommendation = recommendation(),
                    observations = emptyList(),
                    canRevert = true,
                    onApply = {},
                    onReject = {},
                    onRevert = {},
                    onClearMemory = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `expanded explanation renders every evidence section`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .setup()
            .get()
        val explanation = SmartRecommendationExplanation(
            reason = "Razón",
            evidence = listOf(
                RecommendationEvidence("Medido", RecommendationEvidenceProvenance.MEASURED),
                RecommendationEvidence("Inferido", RecommendationEvidenceProvenance.INFERRED),
                RecommendationEvidence("Recordado", RecommendationEvidenceProvenance.REMEMBERED),
                RecommendationEvidence("Investigado", RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED)
            ),
            unavailableData = listOf("Latencia"),
            contradictions = listOf("Dos fuentes no coinciden"),
            confidence = RecommendationConfidenceBand.HIGH,
            outcomes = listOf(
                RecommendationOutcome(
                    RecommendationOutcomeObjective.RECOMMENDED,
                    PerformanceProfile.X4,
                    "Prioriza rendimiento medible."
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BALANCED,
                    PerformanceProfile.BALANCED,
                    "Prioriza estabilidad."
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BATTERY,
                    PerformanceProfile.BALANCED,
                    "Prioriza menor carga sostenida."
                )
            ),
            changeExplanation = "Cambió por nueva evidencia.",
            withheldReasons = listOf("X4 no se usa por seguridad."),
            conciseSummary = "Resumen"
        )

        activity.setContent {
            MaterialTheme {
                SmartPerformanceCard(
                    recommendation = recommendation(
                        strategy = DriverStrategy.TURNIP_CANDIDATE,
                        explanation = explanation
                    ),
                    observations = emptyList(),
                    canRevert = true,
                    onApply = {},
                    onReject = {},
                    onRevert = {},
                    onClearMemory = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `presentation builder exposes all detail sections and stays deterministic`() {
        val explanation = SmartRecommendationExplanation(
            reason = "Razón",
            evidence = listOf(
                RecommendationEvidence("Medido", RecommendationEvidenceProvenance.MEASURED),
                RecommendationEvidence("Inferido", RecommendationEvidenceProvenance.INFERRED),
                RecommendationEvidence("Recordado", RecommendationEvidenceProvenance.REMEMBERED),
                RecommendationEvidence("Investigado", RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED)
            ),
            unavailableData = listOf("Latencia"),
            contradictions = listOf("Contradicción"),
            confidence = RecommendationConfidenceBand.HIGH,
            outcomes = listOf(
                RecommendationOutcome(
                    RecommendationOutcomeObjective.RECOMMENDED,
                    PerformanceProfile.X4,
                    "Rendimiento"
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BALANCED,
                    PerformanceProfile.BALANCED,
                    "Equilibrio"
                ),
                RecommendationOutcome(
                    RecommendationOutcomeObjective.BATTERY,
                    PerformanceProfile.BALANCED,
                    "Batería"
                )
            ),
            changeExplanation = "Cambió",
            withheldReasons = listOf("No X4"),
            conciseSummary = "Resumen"
        )

        val presentation = smartPerformancePresentation(
            recommendation(
                strategy = DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE,
                explanation = explanation
            )
        )

        assertEquals(R.string.smart_performance_confidence_high, presentation.confidenceLabelRes)
        assertEquals(R.string.driver_native_candidate, presentation.driverLabelRes)
        assertEquals(
            listOf(
                R.string.smart_performance_evidence,
                R.string.smart_performance_unavailable,
                R.string.smart_performance_contradictions,
                R.string.smart_performance_changed,
                R.string.smart_performance_not_recommended,
                R.string.smart_performance_comparison
            ),
            presentation.sections.map { it.titleRes }
        )
        assertEquals(
            listOf(
                R.string.smart_performance_evidence_measured,
                R.string.smart_performance_evidence_inferred,
                R.string.smart_performance_evidence_remembered,
                R.string.smart_performance_evidence_external
            ),
            presentation.sections.first().lines.map { it.prefixRes }
        )
        assertEquals(
            listOf(
                R.string.smart_performance_outcome_recommended,
                R.string.smart_performance_outcome_balanced,
                R.string.smart_performance_outcome_battery
            ),
            presentation.sections.last().lines.map { it.prefixRes }
        )
    }

    @Test
    fun `presentation builder omits empty optional sections`() {
        val presentation = smartPerformancePresentation(
            recommendation(
                explanation = SmartRecommendationExplanation(
                    reason = "Razón",
                    evidence = emptyList(),
                    unavailableData = emptyList(),
                    contradictions = emptyList(),
                    confidence = RecommendationConfidenceBand.LOW,
                    outcomes = emptyList(),
                    changeExplanation = null,
                    withheldReasons = emptyList(),
                    conciseSummary = "Resumen"
                )
            )
        )

        assertEquals(R.string.smart_performance_confidence_low, presentation.confidenceLabelRes)
        assertTrue(presentation.sections.isEmpty())
    }

    @Test
    fun `label mappings cover every confidence provenance outcome and driver strategy`() {
        assertEquals(
            R.string.smart_performance_confidence_low,
            smartPerformanceConfidenceLabelRes(RecommendationConfidenceBand.LOW)
        )
        assertEquals(
            R.string.smart_performance_confidence_medium,
            smartPerformanceConfidenceLabelRes(RecommendationConfidenceBand.MEDIUM)
        )
        assertEquals(
            R.string.smart_performance_confidence_high,
            smartPerformanceConfidenceLabelRes(RecommendationConfidenceBand.HIGH)
        )

        assertEquals(
            R.string.smart_performance_evidence_measured,
            smartPerformanceEvidenceLabelRes(RecommendationEvidenceProvenance.MEASURED)
        )
        assertEquals(
            R.string.smart_performance_evidence_inferred,
            smartPerformanceEvidenceLabelRes(RecommendationEvidenceProvenance.INFERRED)
        )
        assertEquals(
            R.string.smart_performance_evidence_remembered,
            smartPerformanceEvidenceLabelRes(RecommendationEvidenceProvenance.REMEMBERED)
        )
        assertEquals(
            R.string.smart_performance_evidence_external,
            smartPerformanceEvidenceLabelRes(RecommendationEvidenceProvenance.EXTERNALLY_RESEARCHED)
        )

        assertEquals(
            R.string.smart_performance_outcome_recommended,
            smartPerformanceOutcomeLabelRes(RecommendationOutcomeObjective.RECOMMENDED)
        )
        assertEquals(
            R.string.smart_performance_outcome_balanced,
            smartPerformanceOutcomeLabelRes(RecommendationOutcomeObjective.BALANCED)
        )
        assertEquals(
            R.string.smart_performance_outcome_battery,
            smartPerformanceOutcomeLabelRes(RecommendationOutcomeObjective.BATTERY)
        )

        assertEquals(
            R.string.driver_system_only,
            smartPerformanceDriverLabelRes(DriverStrategy.SYSTEM_ONLY)
        )
        assertEquals(
            R.string.driver_turnip_candidate,
            smartPerformanceDriverLabelRes(DriverStrategy.TURNIP_CANDIDATE)
        )
        assertEquals(
            R.string.driver_native_candidate,
            smartPerformanceDriverLabelRes(DriverStrategy.NATIVE_OR_VENDOR_CANDIDATE)
        )
    }
}

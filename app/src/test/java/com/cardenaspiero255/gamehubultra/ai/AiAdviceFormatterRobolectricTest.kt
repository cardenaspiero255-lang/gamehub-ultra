package com.cardenaspiero255.gamehubultra.ai

import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiAdviceFormatterRobolectricTest {
    @Test
    fun `full response includes localized advice and recovery explanation`() {
        val context = RuntimeEnvironment.getApplication()
        val response = AiAdviceFormatter.fullResponse(
            context,
            GameHubAiAdvice(
                readiness = 91,
                suggestedProfile = PerformanceProfile.BALANCED,
                reason = AiAdviceReason.BALANCED_GENERAL,
                localModelUsed = false,
                fallbackUsed = true,
                recoveryExplanation = "Ajusté la recomendación con tu historial."
            )
        )

        assertTrue(response.contains("91"))
        assertTrue(response.contains("Ajusté la recomendación con tu historial."))
    }
}

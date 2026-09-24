package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.AssistantPreset
import com.cardenaspiero255.gamehubultra.domain.ResolutionAdvice
import com.cardenaspiero255.gamehubultra.domain.SmartGameAssistantSuggestion
import com.cardenaspiero255.gamehubultra.domain.ThermalPreference

@Composable
fun SmartGameAssistantCard(
    suggestions: List<SmartGameAssistantSuggestion>,
    onApply: (SmartGameAssistantSuggestion) -> Unit
) {
    var selectedPreset by remember(suggestions) {
        mutableStateOf(suggestions.firstOrNull()?.preset ?: AssistantPreset.RECOMMENDED)
    }
    val selected = suggestions.firstOrNull { it.preset == selectedPreset }
        ?: suggestions.firstOrNull()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("SMART GAME ASSISTANT")
            Text(
                "Recomendaciones locales por juego usando solo datos expuestos por Android y el historial local."
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { suggestion ->
                    TextButton(
                        onClick = { selectedPreset = suggestion.preset },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            if (suggestion.preset == selectedPreset) {
                                "✓ " + suggestion.preset.title
                            } else {
                                suggestion.preset.title
                            }
                        )
                    }
                }
            }

            selected?.let { suggestion ->
                Text("Perfil: " + suggestion.profile.title)
                Text("Térmica: " + suggestion.thermalPreference.title)
                Text(
                    "Refresco: " +
                        (suggestion.refreshRateTargetHz?.let { "$it Hz" } ?: "no disponible")
                )
                Text(
                    "Resolución: " +
                        when (suggestion.resolutionAdvice) {
                            ResolutionAdvice.KEEP -> "mantener"
                            ResolutionAdvice.REDUCE_ONE_STEP -> "reducir un nivel si el juego lo permite"
                            ResolutionAdvice.AUTO -> "automática / nativa"
                        }
                )
                Text(suggestion.reason)
                Text(
                    "Evidencia: " + suggestion.evidence.take(5).joinToString(" · ")
                )
                Button(
                    onClick = { onApply(suggestion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Aplicar recomendación")
                }
            }
        }
    }
}

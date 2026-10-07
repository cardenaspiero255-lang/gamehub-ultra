package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardenaspiero255.gamehubultra.domain.AiProfileProposal

@Composable
fun AiProfileBuilderCard(
    proposal: AiProfileProposal?,
    canRollback: Boolean,
    onApply: () -> Unit,
    onRollback: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PERFIL IA")
            Text(
                "Construye una propuesta por juego usando telemetría real, historial local y capacidades verificadas. Nunca se aplica sola."
            )

            if (proposal == null) {
                Text("No hay cambios nuevos que proponer con la evidencia actual.")
            } else {
                Text("Propuesta V${proposal.version}")
                Text("Perfil: ${proposal.proposedConfig.performanceProfile.title}")
                proposal.proposedConfig.refreshRateTargetHz?.let {
                    Text("Refresco verificado: $it Hz")
                }
                proposal.reasons.take(4).forEach { reason ->
                    Text("• $reason")
                }
                proposal.disabledSettings.take(4).forEach { disabled ->
                    Text("• $disabled")
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Aplicar propuesta IA")
                }
            }

            if (canRollback) {
                OutlinedButton(
                    onClick = onRollback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Revertir última propuesta IA")
                }
            }
        }
    }
}

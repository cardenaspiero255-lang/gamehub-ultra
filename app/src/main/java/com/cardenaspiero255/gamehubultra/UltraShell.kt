package com.cardenaspiero255.gamehubultra

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardenaspiero255.gamehubultra.domain.PerformanceProfile
import com.cardenaspiero255.gamehubultra.platform.DeviceInfo

internal const val ULTRA_PLAYER_NAME = "ejecutor3.0"

@Composable
internal fun UltraShellHeader(
    playerName: String
) {
    Surface(
        color = Color(0xFF030306),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "GAMEHUB ULTRA",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        playerName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "PERFIL ULTRA",
                        color = Color(0xFF9696A2),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Surface(
                    color = Color(0xFF130207),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFFFF1630),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Text(
                        "U",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = Color(0xFFFF3048),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
internal fun UltraProfileScreen(
    modifier: Modifier,
    playerName: String,
    activeProfile: PerformanceProfile,
    favoriteCount: Int,
    recentCount: Int,
    sessionCount: Int,
    device: DeviceInfo
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "PERFIL",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Surface(
            color = Color(0xFF0B0B0E),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFF1630).copy(alpha = .55f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(playerName, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text(
                    "GameHub Ultra Player",
                    color = Color(0xFFFF3048),
                    fontWeight = FontWeight.Bold
                )
                Text("Perfil activo · ${activeProfile.title}", color = Color(0xFFBDBDC8))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileMetric("FAVORITOS", favoriteCount.toString(), Modifier.weight(1f))
            ProfileMetric("RECIENTES", recentCount.toString(), Modifier.weight(1f))
            ProfileMetric("SESIONES", sessionCount.toString(), Modifier.weight(1f))
        }

        Surface(
            color = Color(0xFF0B0B0E),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("DISPOSITIVO", color = Color(0xFFFF3048), fontWeight = FontWeight.Bold)
                Text("${device.manufacturer} ${device.model}", fontWeight = FontWeight.Bold)
                Text(
                    "${device.cpuCores} núcleos · ${device.totalRamMb / 1024L} GB RAM",
                    color = Color(0xFFBDBDC8)
                )
            }
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        color = Color(0xFF111116),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color(0xFFFF3048), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

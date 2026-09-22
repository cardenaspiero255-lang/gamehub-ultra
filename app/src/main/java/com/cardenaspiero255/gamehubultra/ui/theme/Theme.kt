package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UltraColors = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color(0xFF181200),
    secondary = Color(0xFFFF5252),
    onSecondary = Color(0xFF2B0000),
    background = Color(0xFF090909),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF211C1C),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5)
)

@Composable
fun GameHubUltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UltraColors,
        typography = Typography(),
        content = content
    )
}

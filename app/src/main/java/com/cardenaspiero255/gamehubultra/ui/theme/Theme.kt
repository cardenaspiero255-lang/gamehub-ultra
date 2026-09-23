package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// GameHub Ultra: red/black gaming palette.
private val UltraColors = darkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C0005),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFFF3B30),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A0002),
    onSecondaryContainer = Color(0xFFFFDAD9),
    background = Color(0xFF050505),
    surface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFF171717),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF3A3A3A)
)

@Composable
fun GameHubUltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UltraColors,
        typography = Typography(),
        content = content
    )
}

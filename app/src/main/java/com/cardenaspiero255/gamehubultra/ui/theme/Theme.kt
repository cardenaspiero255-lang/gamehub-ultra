package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes

internal object GameHubIdentityColors {
    val Black = Color(0xFF030306)
    val PanelBlack = Color(0xFF08080D)
    val Red = Color(0xFFFF1630)
    val RedBright = Color(0xFFFF3048)
    val RedDark = Color(0xFF7A0612)
    val TextPrimary = Color(0xFFF8F8F8)
    val TextMuted = Color(0xFFBDBDC8)
    val Outline = Color(0xFF34343A)
}

// GameHub Ultra CAR-31: Canva red-neon/black gaming palette.
private val UltraColors = darkColorScheme(
    primary = GameHubIdentityColors.Red,
    onPrimary = Color.White,
    primaryContainer = GameHubIdentityColors.RedDark,
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = GameHubIdentityColors.RedBright,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A0002),
    onSecondaryContainer = Color(0xFFFFDAD9),
    background = GameHubIdentityColors.Black,
    surface = GameHubIdentityColors.PanelBlack,
    surfaceVariant = Color(0xFF111116),
    onBackground = GameHubIdentityColors.TextPrimary,
    onSurface = GameHubIdentityColors.TextPrimary,
    onSurfaceVariant = GameHubIdentityColors.TextMuted,
    outline = GameHubIdentityColors.Outline
)

private val DefaultTypography = Typography()

private val UltraTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontSize = GameHubUiTokens.displayLarge),
    displayMedium = DefaultTypography.displayMedium.copy(fontSize = GameHubUiTokens.displayMedium),
    displaySmall = DefaultTypography.displaySmall.copy(fontSize = GameHubUiTokens.displaySmall),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontSize = GameHubUiTokens.headlineLarge),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontSize = GameHubUiTokens.headlineMedium),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontSize = GameHubUiTokens.headlineSmall),
    titleLarge = DefaultTypography.titleLarge.copy(fontSize = GameHubUiTokens.titleLarge),
    titleMedium = DefaultTypography.titleMedium.copy(fontSize = GameHubUiTokens.titleMedium),
    titleSmall = DefaultTypography.titleSmall.copy(fontSize = GameHubUiTokens.titleSmall),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = GameHubUiTokens.bodyLarge),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontSize = GameHubUiTokens.bodyMedium),
    bodySmall = DefaultTypography.bodySmall.copy(fontSize = GameHubUiTokens.bodySmall),
    labelLarge = DefaultTypography.labelLarge.copy(fontSize = GameHubUiTokens.labelLarge),
    labelMedium = DefaultTypography.labelMedium.copy(fontSize = GameHubUiTokens.labelMedium),
    labelSmall = DefaultTypography.labelSmall.copy(fontSize = GameHubUiTokens.labelSmall)
)

private val UltraShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
)

@Composable
fun GameHubUltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UltraColors,
        typography = UltraTypography,
        shapes = UltraShapes,
        content = content
    )
}

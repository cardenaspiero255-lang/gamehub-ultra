package com.cardenaspiero255.gamehubultra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Shapes

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

private val DefaultTypography = Typography()

private val UltraTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontSize = 34.sp),
    displayMedium = DefaultTypography.displayMedium.copy(fontSize = 28.sp),
    displaySmall = DefaultTypography.displaySmall.copy(fontSize = 24.sp),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontSize = 24.sp),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontSize = 21.sp),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontSize = 20.sp),
    titleLarge = DefaultTypography.titleLarge.copy(fontSize = 18.sp),
    titleMedium = DefaultTypography.titleMedium.copy(fontSize = 15.sp),
    titleSmall = DefaultTypography.titleSmall.copy(fontSize = 14.sp),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontSize = 14.sp),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontSize = 13.sp),
    bodySmall = DefaultTypography.bodySmall.copy(fontSize = 12.sp),
    labelLarge = DefaultTypography.labelLarge.copy(fontSize = 12.sp),
    labelMedium = DefaultTypography.labelMedium.copy(fontSize = 11.sp),
    labelSmall = DefaultTypography.labelSmall.copy(fontSize = 10.sp)
)

private val UltraShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
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

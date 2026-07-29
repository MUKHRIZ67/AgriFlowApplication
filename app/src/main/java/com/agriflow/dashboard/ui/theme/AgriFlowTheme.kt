package com.agriflow.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Light color scheme — agricultural green primary, minimalist off-white surface
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB9F281),
    onPrimaryContainer = Color(0xFF082100),
    secondary = Color(0xFF38656A),
    secondaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFFFCFDF6),
    surface = Color(0xFFFCFDF6),
    surfaceVariant = Color(0xFFEFF2E5),
    onSurface = Color(0xFF1A1C16),
    onSurfaceVariant = Color(0xFF43483D),
    outline = Color(0xFF73796B),
    outlineVariant = Color(0xFFC2C8B7),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// Dark scheme kept minimal — the brief calls for a light minimalist aesthetic,
// but a dark fallback avoids the app looking broken if system dark mode forces it.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD67D),
    onPrimary = Color(0xFF0B3B0F),
    primaryContainer = Color(0xFF25501C),
    onPrimaryContainer = Color(0xFFB9F281),
    secondary = Color(0xFFA0CFD4),
    background = Color(0xFF12140E),
    surface = Color(0xFF12140E),
    surfaceVariant = Color(0xFF262A20),
    onSurface = Color(0xFFE3E6D9),
    onSurfaceVariant = Color(0xFFC2C8B7),
    outline = Color(0xFF8D9385),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AgriFlowTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)
)

/**
 * App-wide theme. Wrap the Compose tree in this from MainActivity.
 * @param useDarkTheme defaults to the system setting, but the dashboard is
 * designed primarily for light mode per the product spec.
 */
@Composable
fun AgriFlowTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AgriFlowTypography,
        content = content
    )
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.ThemeMode

private val StarMaxDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF4D7D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1325),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = StarMaxIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF20223A),
    onSecondaryContainer = Color(0xFFE0E2FF),
    tertiary = StarMaxAmber,
    onTertiary = Color(0xFF1A1200),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1E202C),
    error = Color(0xFFFF5C77),
    onError = Color.White
)

private val StarMaxLightColorScheme = lightColorScheme(
    primary = StarMaxMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5EE),
    onPrimaryContainer = Color(0xFF4A071E),
    secondary = StarMaxIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFFF),
    onSecondaryContainer = Color(0xFF1A1D52),
    tertiary = StarMaxAmber,
    onTertiary = Color(0xFF231800),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFECEEF4),
    error = Color(0xFFD92D4B),
    onError = Color.White
)

@Immutable
data class StarMaxExtendedColors(
    val isDark: Boolean,
    val storyRingBrush: Brush,
    val brandGradientBrush: Brush,
    val heartRed: Color,
    val verifiedBlue: Color,
    val onlineGreen: Color,
    val subtleDivider: Color,
    val shimmerBase: Color
)

val LocalStarMaxColors = staticCompositionLocalOf {
    StarMaxExtendedColors(
        isDark = false,
        storyRingBrush = Brush.linearGradient(
            listOf(StarMaxAmber, StarMaxCoral, StarMaxMagenta, StarMaxViolet)
        ),
        brandGradientBrush = Brush.linearGradient(
            listOf(StarMaxMagenta, StarMaxCoral, StarMaxAmber)
        ),
        heartRed = StarMaxHeartRed,
        verifiedBlue = StarMaxVerifiedBlue,
        onlineGreen = StarMaxOnlineGreen,
        subtleDivider = LightOutline,
        shimmerBase = LightSurfaceVariant
    )
}

@Composable
fun StarMaxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) StarMaxDarkColorScheme else StarMaxLightColorScheme

    val extendedColors = StarMaxExtendedColors(
        isDark = darkTheme,
        storyRingBrush = Brush.linearGradient(
            listOf(StarMaxAmber, StarMaxCoral, StarMaxMagenta, StarMaxViolet)
        ),
        brandGradientBrush = Brush.linearGradient(
            listOf(StarMaxMagenta, StarMaxCoral, StarMaxAmber)
        ),
        heartRed = StarMaxHeartRed,
        verifiedBlue = StarMaxVerifiedBlue,
        onlineGreen = StarMaxOnlineGreen,
        subtleDivider = if (darkTheme) DarkOutline else LightOutline,
        shimmerBase = if (darkTheme) DarkSurfaceVariant else LightSurfaceVariant
    )

    CompositionLocalProvider(LocalStarMaxColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StarMaxTypography,
            content = content
        )
    }
}

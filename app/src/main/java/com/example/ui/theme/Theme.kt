package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = CyberObsidianBg,
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = CyberCyanOnContainer,
    secondary = CyberEmerald,
    onSecondary = CyberObsidianBg,
    secondaryContainer = CyberEmeraldContainer,
    onSecondaryContainer = CyberEmeraldOnContainer,
    tertiary = CyberPurple,
    onTertiary = Color.White,
    tertiaryContainer = CyberPurpleContainer,
    onTertiaryContainer = CyberPurpleOnContainer,
    background = CyberObsidianBg,
    onBackground = SlateTextPrimary,
    surface = CyberObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = CyberObsidianCardHigh,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = CyberObsidianBg,
    surfaceContainerLow = CyberObsidianSurface,
    surfaceContainer = CyberObsidianCardHigh,
    surfaceContainerHigh = CyberObsidianCardHigh,
    surfaceContainerHighest = CyberObsidianCardHighest,
    outline = CyberObsidianBorder,
    outlineVariant = CyberObsidianBorder,
    error = CoralError,
    onError = CyberObsidianBg,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralOnErrorContainer
)

private val StudioLightColorScheme = lightColorScheme(
    primary = GlacierIndigo,
    onPrimary = Color.White,
    primaryContainer = GlacierIndigoContainer,
    onPrimaryContainer = GlacierIndigoOnContainer,
    secondary = GlacierEmerald,
    onSecondary = Color.White,
    secondaryContainer = GlacierEmeraldContainer,
    onSecondaryContainer = GlacierEmeraldOnContainer,
    tertiary = GlacierMagenta,
    onTertiary = Color.White,
    tertiaryContainer = GlacierMagentaContainer,
    onTertiaryContainer = GlacierMagentaOnContainer,
    background = GlacierBg,
    onBackground = GlacierTextPrimary,
    surface = GlacierSurface,
    onSurface = GlacierTextPrimary,
    surfaceVariant = GlacierSurfaceHigh,
    onSurfaceVariant = GlacierTextSecondary,
    surfaceContainerLowest = GlacierSurface,
    surfaceContainerLow = GlacierSurfaceLow,
    surfaceContainer = GlacierSurface,
    surfaceContainerHigh = GlacierSurfaceHigh,
    surfaceContainerHighest = GlacierSurfaceHighest,
    outline = GlacierBorder,
    outlineVariant = GlacierBorder
)

@Immutable
data class StudioCyberPalette(
    val isDark: Boolean,
    val background: Color,
    val cardSurface: Color,
    val cardSurfaceHigh: Color,
    val cardBorder: Color,
    val accentCyan: Color,
    val accentPurple: Color,
    val accentEmerald: Color,
    val gradientStops: List<Color>,
    val terminalSurface: Color,
    val terminalText: Color
)

private val DarkCyberPalette = StudioCyberPalette(
    isDark = true,
    background = CyberObsidianBg,
    cardSurface = CyberObsidianSurface,
    cardSurfaceHigh = CyberObsidianCardHigh,
    cardBorder = CyberObsidianBorder,
    accentCyan = CyberCyan,
    accentPurple = CyberPurple,
    accentEmerald = CyberEmerald,
    gradientStops = listOf(CyberPurple, CyberCyan, CyberEmerald, CyberPurple),
    terminalSurface = Color(0xFF06080E),
    terminalText = Color(0xFFD6F9FF)
)

private val LightCyberPalette = StudioCyberPalette(
    isDark = false,
    background = GlacierBg,
    cardSurface = GlacierSurface,
    cardSurfaceHigh = GlacierSurfaceHigh,
    cardBorder = GlacierBorder,
    accentCyan = GlacierIndigo,
    accentPurple = GlacierMagenta,
    accentEmerald = GlacierEmerald,
    gradientStops = listOf(GlacierMagenta, GlacierIndigo, GlacierEmerald, GlacierMagenta),
    terminalSurface = Color(0xFF0E1324),
    terminalText = Color(0xFFE2F5FF)
)

val LocalStudioCyberPalette = staticCompositionLocalOf { DarkCyberPalette }

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun resolveDarkTheme(themeMode: AppThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

@Composable
fun AiAppStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    val cyberPalette = if (darkTheme) DarkCyberPalette else LightCyberPalette
    CompositionLocalProvider(LocalStudioCyberPalette provides cyberPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}

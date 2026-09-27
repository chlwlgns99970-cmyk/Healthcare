package com.example.healthcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    primary = NightInk,
    onPrimary = Ink,
    primaryContainer = NightHero,
    onPrimaryContainer = NightText,
    secondary = NightOlive,
    onSecondary = NightBackground,
    secondaryContainer = NightOliveSurface,
    onSecondaryContainer = NightText,
    tertiary = NightCoral,
    onTertiary = NightBackground,
    tertiaryContainer = NightCoralSurface,
    onTertiaryContainer = NightText,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightStrong,
    onSurfaceVariant = NightMuted,
    outline = NightMuted,
    outlineVariant = NightDivider,
    error = NightError,
    onError = NightBackground
)

private val LightColorScheme = lightColorScheme(
    primary = HomePurple,
    onPrimary = PaperSurface,
    primaryContainer = HomeLavender,
    onPrimaryContainer = EditorialText,
    secondary = MintDeep,
    onSecondary = PaperSurface,
    secondaryContainer = MintSoft,
    onSecondaryContainer = EditorialText,
    tertiary = CoralAction,
    onTertiary = PaperSurface,
    tertiaryContainer = CoralSoft,
    onTertiaryContainer = EditorialText,
    background = Paper,
    onBackground = EditorialText,
    surface = PaperSurface,
    onSurface = EditorialText,
    surfaceVariant = PaperStrong,
    onSurfaceVariant = EditorialMuted,
    outline = Color(0xFF9C9B92),
    outlineVariant = EditorialDivider,
    error = EditorialError,
    onError = PaperSurface
)

@Composable
fun HealthCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appFontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val systemDensity = LocalDensity.current
    val safeAppScale = appFontScale.takeIf { it.isFinite() }?.coerceIn(0.90f, 1.30f) ?: 1f
    val combinedDensity = remember(systemDensity.density, systemDensity.fontScale, safeAppScale) {
        Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale * safeAppScale
        )
    }
    CompositionLocalProvider(LocalDensity provides combinedDensity) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = WellnessShapes,
            content = content
        )
    }
}

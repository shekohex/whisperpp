package com.github.shekohex.whisperpp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Branded fallback palette (used below Android 12 or when dynamic colour is off).
// Seeded from the app-icon blue (#4285F4); tonal roles follow the Material Theme Builder
// tone mapping (light: 40/90/10, dark: 80/30/90). Every on* pair has contrast >= 4.5:1.

private val LightPrimary = Color(0xFF0B57D0)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD3E3FD)
private val LightOnPrimaryContainer = Color(0xFF041E49)
private val LightSecondary = Color(0xFF565F71)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFDAE2F9)
private val LightOnSecondaryContainer = Color(0xFF131C2B)
private val LightTertiary = Color(0xFF146C2E)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFC4EED0)
private val LightOnTertiaryContainer = Color(0xFF072711)
private val LightError = Color(0xFFB3261E)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFF9DEDC)
private val LightOnErrorContainer = Color(0xFF410E0B)
private val LightSurface = Color(0xFFFAF9FD)
private val LightOnSurface = Color(0xFF1A1B20)
private val LightSurfaceVariant = Color(0xFFE1E2EC)
private val LightOnSurfaceVariant = Color(0xFF44474F)
private val LightOutline = Color(0xFF74777F)
private val LightOutlineVariant = Color(0xFFC4C6D0)
private val LightInverseSurface = Color(0xFF2F3036)
private val LightInverseOnSurface = Color(0xFFF1F0F7)
private val LightInversePrimary = Color(0xFFA8C7FA)
private val LightSurfaceDim = Color(0xFFDAD9DD)
private val LightSurfaceBright = Color(0xFFFAF9FD)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF4F3F7)
private val LightSurfaceContainer = Color(0xFFEEEDF1)
private val LightSurfaceContainerHigh = Color(0xFFE8E7EC)
private val LightSurfaceContainerHighest = Color(0xFFE3E2E6)

private val DarkPrimary = Color(0xFFA8C7FA)
private val DarkOnPrimary = Color(0xFF062E6F)
private val DarkPrimaryContainer = Color(0xFF0842A0)
private val DarkOnPrimaryContainer = Color(0xFFD3E3FD)
private val DarkSecondary = Color(0xFFBEC6DC)
private val DarkOnSecondary = Color(0xFF283141)
private val DarkSecondaryContainer = Color(0xFF3E4759)
private val DarkOnSecondaryContainer = Color(0xFFDAE2F9)
private val DarkTertiary = Color(0xFF6DD58C)
private val DarkOnTertiary = Color(0xFF003915)
private val DarkTertiaryContainer = Color(0xFF0F5223)
private val DarkOnTertiaryContainer = Color(0xFFC4EED0)
private val DarkError = Color(0xFFF2B8B5)
private val DarkOnError = Color(0xFF601410)
private val DarkErrorContainer = Color(0xFF8C1D18)
private val DarkOnErrorContainer = Color(0xFFF9DEDC)
private val DarkSurface = Color(0xFF111318)
private val DarkOnSurface = Color(0xFFE2E2E9)
private val DarkSurfaceVariant = Color(0xFF44474F)
private val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
private val DarkOutline = Color(0xFF8E9099)
private val DarkOutlineVariant = Color(0xFF44474F)
private val DarkInverseSurface = Color(0xFFE2E2E9)
private val DarkInverseOnSurface = Color(0xFF2F3036)
private val DarkInversePrimary = Color(0xFF0B57D0)
private val DarkSurfaceDim = Color(0xFF111318)
private val DarkSurfaceBright = Color(0xFF37393E)
private val DarkSurfaceContainerLowest = Color(0xFF0C0E13)
private val DarkSurfaceContainerLow = Color(0xFF1A1B20)
private val DarkSurfaceContainer = Color(0xFF1E1F25)
private val DarkSurfaceContainerHigh = Color(0xFF282A2F)
private val DarkSurfaceContainerHighest = Color(0xFF33353A)

internal val BrandLightColorScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

internal val BrandDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

// Warning has no M3 role; amber from the icon's yellow bar, tuned per brightness.
internal val WarningContainerLight = Color(0xFFFFE08A)
internal val OnWarningContainerLight = Color(0xFF3D2E00)
internal val WarningContainerDark = Color(0xFF5C4400)
internal val OnWarningContainerDark = Color(0xFFFFE08A)

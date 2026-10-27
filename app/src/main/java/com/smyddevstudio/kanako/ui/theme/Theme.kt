package com.smyddevstudio.kanako.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MushroomLightColorScheme = lightColorScheme(
    primary = PrimaryCrimson,
    onPrimary = Color.White,
    primaryContainer = PrimaryCrimsonLight,
    onPrimaryContainer = PrimaryCrimson,
    secondary = ForestGreen,
    onSecondary = Color.White,
    secondaryContainer = IconSage,
    onSecondaryContainer = TextPrimary,
    tertiary = PineGreen,
    onTertiary = Color.White,
    tertiaryContainer = GreenStatus,
    onTertiaryContainer = Color.White,
    background = CreamBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSecondary,
    onSurfaceVariant = TextMuted,
    outline = TextMuted,
    surfaceDim = CardSecondary,
    surfaceBright = CardSurface,
    surfaceContainerLowest = CardSurface,
    surfaceContainerLow = CardSurface,
    surfaceContainer = CreamBackground,
    surfaceContainerHigh = CardSecondary,
    surfaceContainerHighest = CardSecondary,
    surfaceTint = ForestGreen,
    outlineVariant = CardSecondaryAlt
)

private val MushroomDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryCrimson,
    onPrimary = DarkCreamBackground,
    primaryContainer = DarkPrimaryCrimsonLight,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkForestGreen,
    onSecondary = DarkCreamBackground,
    secondaryContainer = DarkCardSecondary,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = PineGreen,
    onTertiary = Color.White,
    tertiaryContainer = DarkGreenStatus,
    onTertiaryContainer = Color.White,
    background = DarkCreamBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCardSecondary,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkTextMuted,
    surfaceDim = DarkCreamBackground,
    surfaceBright = DarkCardSecondary,
    surfaceContainerLowest = DarkCreamBackground,
    surfaceContainerLow = DarkCardSurface,
    surfaceContainer = DarkCardSurface,
    surfaceContainerHigh = DarkCardSecondary,
    surfaceContainerHighest = DarkCardSecondary,
    surfaceTint = DarkForestGreen,
    outlineVariant = DarkCardSecondary
)

@Composable
fun KanakoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MushroomDarkColorScheme
        else -> MushroomLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes,
        typography = Typography,
        content = content
    )
}

package com.smyddevstudio.kanako.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.KanakoPreferences

internal object ReadingAppearanceTokens {
    val padding = 2.dp
    val gap = 2.dp
    val cornerRadius = 4.dp
    val clearShadowBlur = 2.dp
    const val lineHeightMultiplier = 1.2f
}

internal data class ResolvedReadingAppearance(val text: Color, val plate: Color) {
    fun shadow(blurRadius: Float): Shadow =
        if (plate.alpha == 0f) Shadow(Color.White, blurRadius = blurRadius) else Shadow.None
}

internal fun resolveReadingAppearance(
    preferred: Color,
    plate: Color,
    highContrastText: Color? = null
): ResolvedReadingAppearance = ResolvedReadingAppearance(
    text = highContrastText ?: if (plate.alpha == 0f) preferred else readableReadingColor(preferred, plate),
    plate = plate
)

@Composable
internal fun readingAppearance(preferences: KanakoPreferences): ResolvedReadingAppearance {
    val scheme = MaterialTheme.colorScheme
    val plate = when (preferences.annotationPlateStyle) {
        AnnotationPlateStyle.CLEAR -> Color.Transparent
        AnnotationPlateStyle.WASHI_CAPSULE -> CreamBackgroundDark
        AnnotationPlateStyle.HIGH_CONTRAST -> scheme.inverseSurface
        AnnotationPlateStyle.SUBTLE -> scheme.surfaceVariant
    }
    return resolveReadingAppearance(
        Color(preferences.furiganaColor.colorHex),
        plate,
        highContrastText = scheme.inverseOnSurface.takeIf {
            preferences.annotationPlateStyle == AnnotationPlateStyle.HIGH_CONTRAST
        }
    )
}

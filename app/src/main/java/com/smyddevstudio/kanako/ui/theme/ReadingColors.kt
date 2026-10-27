package com.smyddevstudio.kanako.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

internal fun readableReadingColor(preferred: Color, background: Color): Color {
    fun contrast(foreground: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }
    if (contrast(preferred) >= 4.5f) return preferred
    val target = if (contrast(Color.White) > contrast(Color.Black)) Color.White else Color.Black
    return (1..20).asSequence()
        .map { lerp(preferred, target, it / 20f) }
        .first { contrast(it) >= 4.5f }
}

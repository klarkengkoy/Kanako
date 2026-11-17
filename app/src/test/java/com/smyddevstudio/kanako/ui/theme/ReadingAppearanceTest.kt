package com.smyddevstudio.kanako.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.smyddevstudio.kanako.model.FuriganaColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ReadingAppearanceTest {
    @Test fun everySwatchStaysReadableOnTheDarkSubtlePlate() {
        val plate = Color(0xFF304938)
        FuriganaColor.entries.forEach { swatch ->
            val appearance = resolveReadingAppearance(Color(swatch.colorHex), plate)
            val first = appearance.text.luminance()
            val second = plate.luminance()
            assertTrue((max(first, second) + 0.05f) / (min(first, second) + 0.05f) >= 4.5f)
        }
    }

    @Test fun highContrastUsesItsDedicatedForeground() {
        val appearance = resolveReadingAppearance(Color.Red, Color.Black, Color.White)
        assertEquals(Color.White, appearance.text)
        assertEquals(Color.Black, appearance.plate)
    }

    @Test fun clearPreservesTheChosenColorAndAddsTheSameLightHalo() {
        val preferred = Color(FuriganaColor.INK_BLACK.colorHex)
        val appearance = resolveReadingAppearance(preferred, Color.Transparent)
        assertEquals(preferred, appearance.text)
        assertEquals(Color.White, appearance.shadow(2f).color)
        assertEquals(2f, appearance.shadow(2f).blurRadius, 0f)
    }
}

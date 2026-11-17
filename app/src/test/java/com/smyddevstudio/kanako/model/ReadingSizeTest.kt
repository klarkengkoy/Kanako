package com.smyddevstudio.kanako.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSizeTest {
    @Test fun sizeChoicesStayOrderedAcrossSourceSizesAndDisplayDensities() {
        for (density in listOf(1f, 3f)) {
            for (source in listOf(8f, 30f, 100f, 600f)) {
                val fonts = FuriganaSize.entries.map { size ->
                    val minimum = size.sizeSp * density
                    preferredReadingFontPixels(minimum, source * density, size.readingScale).also {
                        assertTrue(it >= minimum && it <= minimum * 2f)
                    }
                }
                assertTrue(fonts.zipWithNext().all { (small, large) -> small < large })
            }
        }
    }

    @Test fun largeSourceTextCannotMakeAllChoicesTheSameSize() {
        val fonts = FuriganaSize.entries.map {
            preferredReadingFontPixels(it.sizeSp.toFloat(), 100f, it.readingScale)
        }
        assertEquals(listOf(20f, 24f, 28f), fonts)
    }
}

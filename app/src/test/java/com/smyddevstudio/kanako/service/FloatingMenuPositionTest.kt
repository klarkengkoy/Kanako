package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class FloatingMenuPositionTest {
    private val area = ReadingBounds(8, 40, 992, 1960)

    @Test fun menuChoosesTheSameSideAndStaysIndependentOfMascotSize() {
        assertEquals(FloatingPoint(8, 664),
            floatingMenuPosition(area, ReadingBounds(8, 600, 64, 656), 200, 280, 8))
        assertEquals(FloatingPoint(792, 664),
            floatingMenuPosition(area, ReadingBounds(936, 600, 992, 656), 200, 280, 8))
    }

    @Test fun bottomCornerOpensAboveAndStaysInsideSafeBounds() {
        val anchor = ReadingBounds(936, 1904, 992, 1960)
        assertEquals(FloatingPoint(792, 1616), floatingMenuPosition(area, anchor, 200, 280, 8))
        assertEquals(ReadingBounds(936, 1904, 992, 1960), anchor)
    }

    @Test fun crampedWindowsClampTheMenuWithoutMovingItsAnchor() {
        val small = ReadingBounds(20, 40, 220, 300)
        val anchor = ReadingBounds(164, 200, 220, 256)
        assertEquals(FloatingPoint(20, 40), floatingMenuPosition(small, anchor, 200, 260, 8))
        assertEquals(164, anchor.left)
        assertEquals(200, anchor.top)
    }
}

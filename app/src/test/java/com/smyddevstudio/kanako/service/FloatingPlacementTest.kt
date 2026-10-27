package com.smyddevstudio.kanako.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPlacementTest {
    private val area = ReadingBounds(8, 40, 992, 1960)

    @Test fun firstPlacementStartsAtTheRightEdgeAndSavedPositionsWin() {
        assertEquals(936, floatingAnchor(area, 56).x)
        assertEquals(FloatingPoint(120, 700), floatingAnchor(area, 56, FloatingPoint(120, 700)))
    }

    @Test fun expansionAndCollapseKeepTheSameMascotAnchorOnBothSides() {
        for (anchor in listOf(FloatingPoint(936, 600), FloatingPoint(8, 600))) {
            for ((width, height) in listOf(56 to 56, 180 to 350, 120 to 220, 56 to 56)) {
                val frame = floatingFrame(area, anchor, 56, width, height)
                assertEquals(anchor.x, frame.origin.x + frame.control.x)
                assertEquals(anchor.y, frame.origin.y + frame.control.y)
            }
        }
    }

    @Test fun bottomEdgeMenuOpensAboveWithoutMovingTheMascot() {
        val anchor = FloatingPoint(936, 1904)
        val frame = floatingFrame(area, anchor, 56, 180, 350)
        assertTrue(frame.menuAbove)
        assertFalse(frame.onLeft)
        assertEquals(anchor.y, frame.origin.y + frame.control.y)
        assertTrue(frame.origin.y >= area.top)
    }

    @Test fun changedScreenBoundsClampSavedCoordinates() {
        val small = ReadingBounds(20, 40, 400, 300)
        assertEquals(FloatingPoint(344, 244), floatingAnchor(small, 56, FloatingPoint(900, 1800)))
    }

    @Test fun controlSizeChangeKeepsSavedAnchorWhenItStillFits() {
        assertEquals(FloatingPoint(200, 400), floatingAnchor(area, 68, FloatingPoint(200, 400)))
    }
}

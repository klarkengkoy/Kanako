package com.smyddevstudio.kanako.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTrackingTest {
    private val viewport = ReadingBounds(0, 100, 400, 800)

    @Test
    fun scrollingMovesReadingsButLeavesTheFixedHeader() {
        val movement = ScrollMovement(1, viewport, 0, 80)
        val line = followScroll(ReadingBounds(20, 300, 300, 340), viewport, movement)!!
        assertEquals(ReadingBounds(20, 220, 300, 260), line.bounds)
        assertEquals(viewport, line.clip)
        val header = ReadingBounds(10, 10, 100, 40)
        assertEquals(header, followScroll(header, ReadingBounds(0, 0, 400, 800), movement)!!.bounds)
    }

    @Test
    fun offscreenTextIsRemovedWithoutRemovingVisibleText() {
        val movement = ScrollMovement(1, viewport, 0, 100)
        assertNull(followScroll(ReadingBounds(10, 120, 100, 150), viewport, movement))
        assertEquals(ReadingBounds(10, 400, 100, 440),
            followScroll(ReadingBounds(10, 500, 100, 540), viewport, movement)!!.bounds)
    }

    @Test
    fun horizontalAndReverseScrollUseTheSameCoordinates() {
        val bounds = ReadingBounds(100, 300, 200, 340)
        assertEquals(ReadingBounds(70, 350, 170, 390),
            followScroll(bounds, viewport, ScrollMovement(1, viewport, 30, -50))!!.bounds)
    }

    @Test
    fun partlyVisibleLinesKeepTheirFullGeometryButClipHiddenTokens() {
        val moved = followScroll(ReadingBounds(20, 120, 220, 160), viewport,
            ScrollMovement(1, viewport, 0, 30))!!
        assertEquals(ReadingBounds(20, 90, 220, 130), moved.bounds)
        assertEquals(viewport, moved.clip)
        assertEquals(false, moved.clip.contains(ReadingBounds(20, 90, 40, 130)))
        assertEquals(true, moved.clip.contains(ReadingBounds(20, 200, 40, 240)))
    }

    @Test
    fun absoluteOffsetsNeedABaselineAndDoNotMixContainers() {
        val tracker = ScrollOffsetTracker()
        assertNull(tracker.delta(1, 10, 0, 200, 0, 0))
        assertEquals(0 to 80, tracker.delta(1, 10, 0, 280, 0, 0))
        assertNull(tracker.delta(1, 20, 0, 600, 0, 0))
        assertEquals(0 to -20, tracker.delta(1, 10, 0, 260, 0, 0))
        tracker.clear()
        assertNull(tracker.delta(1, 10, 0, 300, 0, 0))
    }

    @Test
    fun unspecifiedDeltasUseAbsoluteOffsetsInsteadOfMovingOnePixelBackwards() {
        val tracker = ScrollOffsetTracker()
        assertNull(tracker.delta(1, 10, 0, 200, -1, -1))
        assertEquals(0 to 80, tracker.delta(1, 10, 0, 280, -1, -1))
        assertNull(tracker.delta(1, 10, 0, 280, -1, -1))
    }

    @Test
    fun anUnspecifiedHorizontalDeltaDoesNotOffsetAVerticalScroll() {
        assertEquals(0 to 60, ScrollOffsetTracker().delta(1, 10, 0, 0, -1, 60))
    }

    @Test
    fun reportedDeltasWorkWithoutPriorAbsoluteOffsets() {
        assertEquals(5 to 60, ScrollOffsetTracker().delta(1, 10, -1, -1, 5, 60))
    }
}

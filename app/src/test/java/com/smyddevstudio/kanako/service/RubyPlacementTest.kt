package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class RubyPlacementTest {
    private val viewport = ReadingBounds(0, 0, 400, 500)

    @Test fun longReadingKeepsItsMeasuredWidthInsteadOfTheKanjiWidth() {
        val request = RubyRequest(ReadingBounds(180, 200, 200, 240), viewport, 160, 24)
        val result = placeRubyReadings(listOf(request), listOf(request.anchor), 4).single()!!
        assertEquals(160, result.right - result.left)
        assertEquals(24, result.bottom - result.top)
        assertTrue(viewport.contains(result))
        assertNull(result.intersection(request.anchor))
    }

    @Test fun adjacentReadingsUseDifferentRowsInsteadOfClippingOrShrinking() {
        val anchors = listOf(ReadingBounds(150, 200, 170, 240), ReadingBounds(175, 200, 195, 240))
        val requests = anchors.map { RubyRequest(it, viewport, 120, 24) }
        val result = placeRubyReadings(requests, anchors, 4).map { it!! }
        assertNull(result[0].intersection(result[1]))
        assertTrue(result.all { it.right - it.left == 120 })
    }

    @Test fun topAndSideEdgesKeepTheWholeReadingInsideTheViewport() {
        val requests = listOf(
            RubyRequest(ReadingBounds(0, 0, 20, 40), viewport, 140, 24),
            RubyRequest(ReadingBounds(380, 100, 400, 140), viewport, 140, 24)
        )
        val result = placeRubyReadings(requests, requests.map { it.anchor }, 4)
        assertTrue(result.all { it != null && viewport.contains(it) })
    }

    @Test fun verticalReadingsUseNeighbouringColumns() {
        val anchors = listOf(ReadingBounds(200, 150, 240, 170), ReadingBounds(200, 175, 240, 195))
        val result = placeRubyReadings(anchors.map { RubyRequest(it, viewport, 24, 120, true) }, anchors, 4).map { it!! }
        assertNull(result[0].intersection(result[1]))
        assertTrue(result.all { viewport.contains(it) && it.bottom - it.top == 120 })
    }

    @Test fun denseLayoutsStillReturnFullSizedLabelsWhenTheyFitTheViewport() {
        val request = RubyRequest(ReadingBounds(10, 10, 30, 30), ReadingBounds(0, 0, 100, 100), 90, 90)
        assertNotNull(placeRubyReadings(listOf(request, request), listOf(request.anchor), 2)[1])
    }
}

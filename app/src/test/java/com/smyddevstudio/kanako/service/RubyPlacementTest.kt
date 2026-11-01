package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class RubyPlacementTest {
    private val viewport = ReadingBounds(0, 0, 400, 500)

    @Test fun longReadingKeepsItsMeasuredWidthInsteadOfTheKanjiWidth() {
        val request = RubyRequest(ReadingBounds(180, 200, 200, 240), viewport, 160, 24)
        val result = placeRubyReadings(listOf(request), 4).single()!!
        assertEquals(160, result.right - result.left)
        assertEquals(24, result.bottom - result.top)
        assertTrue(viewport.contains(result))
        assertNull(result.intersection(request.anchor))
    }

    @Test fun adjacentReadingsStayCenteredEvenWhenTheyOverlap() {
        val anchors = listOf(ReadingBounds(150, 200, 170, 240), ReadingBounds(175, 200, 195, 240))
        val requests = anchors.map { RubyRequest(it, viewport, 120, 24) }
        val result = placeRubyReadings(requests, 4).map { it!! }
        assertNotNull(result[0].intersection(result[1]))
        result.zip(anchors).forEach { (rect, anchor) ->
            assertEquals((anchor.left + anchor.right) / 2f, (rect.left + rect.right) / 2f, 0.5f)
        }
        assertTrue(result.all { it.right - it.left == 120 })
    }

    @Test fun topAndSideEdgesKeepTheWholeReadingInsideTheViewport() {
        val requests = listOf(
            RubyRequest(ReadingBounds(0, 0, 20, 40), viewport, 140, 24),
            RubyRequest(ReadingBounds(380, 100, 400, 140), viewport, 140, 24)
        )
        val result = placeRubyReadings(requests, 4)
        assertTrue(result.all { it != null && viewport.contains(it) })
    }

    @Test fun verticalReadingsStayCenteredInTheirOriginalColumn() {
        val anchors = listOf(ReadingBounds(200, 150, 240, 170), ReadingBounds(200, 175, 240, 195))
        val result = placeRubyReadings(anchors.map { RubyRequest(it, viewport, 24, 120, true) }, 4).map { it!! }
        assertNotNull(result[0].intersection(result[1]))
        result.zip(anchors).forEach { (rect, anchor) ->
            assertEquals((anchor.top + anchor.bottom) / 2f, (rect.top + rect.bottom) / 2f, 0.5f)
        }
        assertTrue(result.all { viewport.contains(it) && it.bottom - it.top == 120 })
    }

    @Test fun crowdedPageNeverMovesReadingsToDistantEmptyMargins() {
        val anchors = (0..12).map { ReadingBounds(150, 200 + it * 20, 190, 216 + it * 20) }
        for (vertical in listOf(false, true)) {
            val source = if (vertical) anchors.map { ReadingBounds(it.top, it.left, it.bottom, it.right) } else anchors
            val requests = source.map {
                RubyRequest(it, ReadingBounds(0, 0, 1200, 1200),
                    if (vertical) 30 else 160, if (vertical) 160 else 30, vertical)
            }
            val result = placeRubyReadings(requests, 4)
            result.zip(requests).forEach { (placed, request) ->
                assertNotNull(placed)
                val rect = placed!!
                assertEquals(request.width, rect.right - rect.left)
                assertEquals(request.height, rect.bottom - rect.top)
                if (vertical) {
                    assertTrue(rect.left >= request.anchor.left - 4 - request.width)
                    assertTrue(rect.left <= request.anchor.right + 4 + request.width + 4)
                } else {
                    assertTrue(rect.top >= request.anchor.top - 2 * (request.height + 4))
                    assertTrue(rect.top <= request.anchor.bottom + 4)
                }
            }
        }
    }

    @Test fun denseLayoutsStillReturnFullSizedLabelsWhenTheyFitTheViewport() {
        val request = RubyRequest(ReadingBounds(10, 10, 30, 30), ReadingBounds(0, 0, 100, 100), 90, 90)
        assertNotNull(placeRubyReadings(listOf(request, request), 2)[1])
    }
}

package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class RecognizedTextGeometryTest {
    @Test fun unevenCharacterGeometryLocatesTheActualToken() {
        val bounds = ReadingBounds(10, 200, 510, 240)
        val spans = listOf(
            RecognizedTextSpan(0, 1, ReadingBounds(10, 200, 30, 240)),
            RecognizedTextSpan(2, 3, ReadingBounds(100, 201, 140, 239)),
            RecognizedTextSpan(3, 4, ReadingBounds(142, 202, 190, 240))
        )
        assertEquals(ReadingBounds(100, 201, 190, 240),
            readingTokenBounds("a 漢字!", bounds, spans, 2, 4))
    }

    @Test fun elementFallbackSlicesTheRelevantCharacters() {
        val bounds = ReadingBounds(0, 0, 600, 50)
        val span = RecognizedTextSpan(2, 5, ReadingBounds(100, 0, 220, 50))
        assertEquals(ReadingBounds(140, 0, 220, 50),
            readingTokenBounds("ab日本語", bounds, listOf(span), 3, 5))
    }

    @Test fun verticalAndMissingSpansUseTheirOwnAxis() {
        val bounds = ReadingBounds(100, 20, 140, 220)
        assertEquals(ReadingBounds(100, 70, 140, 170),
            readingTokenBounds("日本語学", bounds, emptyList(), 1, 3))
        val span = RecognizedTextSpan(0, 4, ReadingBounds(102, 30, 138, 190))
        assertEquals(ReadingBounds(102, 70, 138, 150),
            readingTokenBounds("日本語学", bounds, listOf(span), 1, 3))
    }

    @Test fun onlyNearbyAlignedRowsContinueAReading() {
        val line = ReadingBounds(30, 200, 900, 250)
        assertTrue(continuesReadingLine("子どもは言", line, ReadingBounds(30, 280, 700, 330)))
        assertFalse(continuesReadingLine("子どもは言", line, ReadingBounds(500, 280, 900, 330)))
        assertFalse(continuesReadingLine("子どもは言", line, ReadingBounds(30, 420, 700, 470)))
        assertFalse(continuesReadingLine("終わりました。", line, ReadingBounds(30, 280, 700, 330)))
        assertFalse(continuesReadingLine("見出し", line, ReadingBounds(30, 280, 700, 390)))
    }

    @Test fun scrollingMovesEveryCharacterByTheSameDeltaAsItsLine() {
        val line = ReadingBounds(10, 200, 500, 250)
        val span = RecognizedTextSpan(2, 3, ReadingBounds(90, 200, 130, 250))
        val movement = ScrollMovement(1, ReadingBounds(0, 100, 600, 900), 0, 75)
        val tracked = followScroll(line, movement.viewport, movement)!!
        val moved = span.copy(bounds = span.bounds.moved(
            tracked.bounds.left - line.left, tracked.bounds.top - line.top))
        assertEquals(ReadingBounds(90, 125, 130, 175),
            readingTokenBounds("ab漢字", tracked.bounds, listOf(moved), 2, 3))
    }
}

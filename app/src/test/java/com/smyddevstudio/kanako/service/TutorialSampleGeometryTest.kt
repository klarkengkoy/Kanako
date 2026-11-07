package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class TutorialSampleGeometryTest {
    private val sample = "日本語を読みます。\n今日は本を三冊読みました。"
    private val spans = sample.indices.filter { !sample[it].isWhitespace() }.map { index ->
        RecognizedTextSpan(index, index + 1, ReadingBounds(index * 12, 30, index * 12 + 12, 50))
    }

    @Test fun recognizedSubstringUsesMeasuredCharactersAndRetainsOcrOffsets() {
        val matched = tutorialSampleSpans(sample, spans, "本 を三冊")!!
        assertEquals(listOf(0, 2, 3, 4), matched.map { it.start })
        val first = sample.indexOf("本を三冊")
        assertEquals(spans.first { it.start == first }.bounds, matched.first().bounds)
        assertEquals(ReadingBounds((first + 2) * 12, 30, (first + 4) * 12, 50),
            readingTokenBounds("本 を三冊", ReadingBounds(0, 0, 500, 50), matched, 3, 5))
    }

    @Test fun missingAmbiguousOrIncompleteMatchesKeepOcrGeometry() {
        assertNull(tutorialSampleSpans(sample, spans, "別の文章"))
        assertNull(tutorialSampleSpans("本と本", emptyList(), "本"))
        assertNull(tutorialSampleSpans(sample, spans.drop(1), "日本語"))
    }

    @Test fun whitespaceCannotCreateAnEmptyGeometry() {
        assertNull(tutorialSampleSpans(sample, spans, "  "))
    }
}

package com.smyddevstudio.kanako.service

import kotlin.math.roundToInt

internal data class RecognizedTextSpan(val start: Int, val end: Int, val bounds: ReadingBounds)

internal fun tutorialSampleSpans(
    sampleText: String, sampleSpans: List<RecognizedTextSpan>, text: String
): List<RecognizedTextSpan>? {
    val sampleIndices = sampleText.indices.filter { !sampleText[it].isWhitespace() }
    val lineIndices = text.indices.filter { !text[it].isWhitespace() }
    val source = sampleIndices.map { sampleText[it] }.joinToString("")
    val recognized = lineIndices.map { text[it] }.joinToString("")
    if (recognized.isEmpty()) return null
    val start = source.indexOf(recognized)
    if (start < 0 || source.indexOf(recognized, start + 1) >= 0) return null
    return lineIndices.mapIndexed { index, lineIndex ->
        val sourceIndex = sampleIndices[start + index]
        val span = sampleSpans.firstOrNull { it.start == sourceIndex } ?: return null
        span.copy(start = lineIndex, end = lineIndex + 1)
    }
}

internal data class RecognitionSlice(val span: Int?, val start: Int, val end: Int)

internal fun recognitionSlices(start: Int, end: Int, ranges: List<Pair<Int, Int>>): List<RecognitionSlice> {
    val result = mutableListOf<RecognitionSlice>()
    for (offset in start until end) {
        val best = ranges.indices.filter { ranges[it].first <= offset && ranges[it].second > offset }
            .minByOrNull { ranges[it].second - ranges[it].first }
        val previous = result.lastOrNull()
        if (previous != null && previous.span == best) result[result.lastIndex] = previous.copy(end = offset + 1)
        else result.add(RecognitionSlice(best, offset, offset + 1))
    }
    return result
}

internal fun readingTokenBounds(
    text: String, bounds: ReadingBounds, spans: List<RecognizedTextSpan>, start: Int, end: Int
): ReadingBounds {
    val vertical = bounds.bottom - bounds.top > bounds.right - bounds.left
    fun slice(box: ReadingBounds, from: Float, to: Float) = if (vertical)
        ReadingBounds(box.left, (box.top + (box.bottom - box.top) * from).roundToInt(),
            box.right, (box.top + (box.bottom - box.top) * to).roundToInt())
    else ReadingBounds((box.left + (box.right - box.left) * from).roundToInt(), box.top,
        (box.left + (box.right - box.left) * to).roundToInt(), box.bottom)
    val boxes = recognitionSlices(start, end, spans.map { it.start to it.end }).map { piece ->
        val span = piece.span?.let { spans[it] } ?: RecognizedTextSpan(0, text.length, bounds)
        val length = (span.end - span.start).coerceAtLeast(1).toFloat()
        slice(span.bounds, (piece.start - span.start) / length, (piece.end - span.start) / length)
    }
    if (boxes.isEmpty()) return bounds
    return ReadingBounds(boxes.minOf { it.left }, boxes.minOf { it.top },
        boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
}

internal fun continuesReadingLine(text: String, bounds: ReadingBounds, next: ReadingBounds): Boolean {
    val height = bounds.bottom - bounds.top
    val nextHeight = next.bottom - next.top
    val content = text.trimEnd()
    if (content.isEmpty() || content.last() in "。！？!?」』" || height <= 0 ||
        bounds.right - bounds.left < height * 2 || next.right - next.left < nextHeight * 2
    ) return false
    val characterWidth = (bounds.right - bounds.left) / text.length.coerceAtLeast(1)
    return nextHeight in (height * 0.7f).roundToInt()..(height * 1.4f).roundToInt() &&
        next.top >= bounds.bottom - height / 4 && next.top <= bounds.bottom + height &&
        kotlin.math.abs(next.left - bounds.left) <= maxOf(characterWidth * 2, height)
}

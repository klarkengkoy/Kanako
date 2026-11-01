package com.smyddevstudio.kanako.service

import kotlin.math.roundToInt

internal data class RecognizedTextSpan(val start: Int, val end: Int, val bounds: ReadingBounds)

internal fun readingTokenBounds(
    text: String, bounds: ReadingBounds, spans: List<RecognizedTextSpan>, start: Int, end: Int
): ReadingBounds {
    val vertical = bounds.bottom - bounds.top > bounds.right - bounds.left
    fun slice(box: ReadingBounds, from: Float, to: Float) = if (vertical)
        ReadingBounds(box.left, (box.top + (box.bottom - box.top) * from).roundToInt(),
            box.right, (box.top + (box.bottom - box.top) * to).roundToInt())
    else ReadingBounds((box.left + (box.right - box.left) * from).roundToInt(), box.top,
        (box.left + (box.right - box.left) * to).roundToInt(), box.bottom)
    val matching = spans.filter { it.start < end && it.end > start }
    if (matching.isEmpty()) {
        val length = text.length.coerceAtLeast(1).toFloat()
        return slice(bounds, start / length, end / length)
    }
    val boxes = matching.map { span ->
        val length = (span.end - span.start).coerceAtLeast(1).toFloat()
        slice(span.bounds, (maxOf(start, span.start) - span.start) / length,
            (minOf(end, span.end) - span.start) / length)
    }
    return ReadingBounds(boxes.minOf { it.left }, boxes.minOf { it.top },
        boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
}

internal fun continuesReadingLine(text: String, bounds: ReadingBounds, next: ReadingBounds): Boolean {
    val height = bounds.bottom - bounds.top
    val nextHeight = next.bottom - next.top
    if (text.isBlank() || text.last() in "。！？!?」』" || height <= 0 ||
        bounds.right - bounds.left < height * 2 || next.right - next.left < nextHeight * 2
    ) return false
    val characterWidth = (bounds.right - bounds.left) / text.length.coerceAtLeast(1)
    return nextHeight in (height * 0.7f).roundToInt()..(height * 1.4f).roundToInt() &&
        next.top >= bounds.bottom - height / 4 && next.top <= bounds.bottom + height &&
        kotlin.math.abs(next.left - bounds.left) <= maxOf(characterWidth * 2, height)
}

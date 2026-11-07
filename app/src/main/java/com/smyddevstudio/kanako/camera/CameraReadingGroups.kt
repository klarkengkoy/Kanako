package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.readingWords
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

internal fun cameraReadingIdentity(line: CameraReadingLine): List<Pair<String, String?>> {
    val words = readingWords(line.text, line.segments)
    return if (words.isEmpty()) listOf(line.text to null)
    else words.map { line.text.substring(it.readingStart, it.readingEnd) to it.reading }
}

internal fun cameraTrackingBounds(line: CameraReadingLine): CameraRect {
    val word = readingWords(line.text, line.segments).singleOrNull() ?: return line.bounds
    return cameraTokenBounds(line, word.readingStart, word.readingEnd)
}

internal fun cameraReadingGroups(lines: List<CameraReadingLine>): List<CameraReadingLine> = lines.flatMap { line ->
    if (line.segments.isEmpty()) listOf(line) else buildList {
        val words = readingWords(line.text, line.segments).associateBy { it.start }
        var offset = 0
        var consumed = 0
        line.segments.forEach { segment ->
            val start = line.text.indexOf(segment.text, offset)
            if (start < 0) return@forEach
            offset = start + segment.text.length
            if (start < consumed || segment.text.isBlank()) return@forEach
            val word = words[start]
            val end = word?.end ?: offset
            consumed = end
            if (line.segments.size == 1 && start == 0 && end == line.text.length) {
                add(line)
                return@forEach
            }
            val source = line.text.substring(start, end)
            val suffix = line.text.substring(offset, end)
            val grouped = if (word != null) FuriganaSegment(source, segment.reading + suffix) else segment
            val spans = line.spans.filter { it.start < end && it.end > start }.map { span ->
                val from = maxOf(start, span.start)
                val to = minOf(end, span.end)
                val polygon = cameraSpanPolygon(span, from, to, line.isVertical)
                CameraTextSpan(from - start, to - start, polygon.bounds, polygon.points)
            }
            add(CameraReadingLine(source, cameraTokenBounds(line, start, end), listOf(grouped),
                spans, vertical = line.isVertical))
        }
    }
}

package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.kanjiAnnotation
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

internal fun cameraReadingGroups(lines: List<CameraReadingLine>): List<CameraReadingLine> = lines.flatMap { line ->
    if (line.segments.isEmpty()) listOf(line) else buildList {
        var offset = 0
        line.segments.forEach { segment ->
            val tokenStart = line.text.indexOf(segment.text, offset)
            if (tokenStart < 0) return@forEach
            offset = tokenStart + segment.text.length
            if (segment.text.isBlank()) return@forEach
            val annotation = segment.reading?.let { reading ->
                kanjiAnnotation(segment.text, reading.map {
                    if (it in '\u30A1'..'\u30F6') (it.code - 0x60).toChar() else it
                }.joinToString(""))
            }
            val start = tokenStart + (annotation?.start ?: 0)
            val end = tokenStart + (annotation?.end ?: segment.text.length)
            if (line.segments.size == 1 && start == 0 && end == line.text.length) {
                add(line)
                return@forEach
            }
            val grouped = if (annotation != null && (annotation.start > 0 || annotation.end < segment.text.length)) {
                FuriganaSegment(segment.text.substring(annotation.start, annotation.end), annotation.reading)
            } else segment
            val spans = line.spans.filter { it.start < end && it.end > start }.map { span ->
                val from = maxOf(start, span.start)
                val to = minOf(end, span.end)
                val polygon = cameraSpanPolygon(span, from, to, line.isVertical)
                CameraTextSpan(from - start, to - start, polygon.bounds, polygon.points)
            }
            add(CameraReadingLine(grouped.text, cameraTokenBounds(line, start, end), listOf(grouped),
                spans, vertical = line.isVertical))
        }
    }
}

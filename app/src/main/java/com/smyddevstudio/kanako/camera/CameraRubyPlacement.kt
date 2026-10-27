package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.RubyRequest
import kotlin.math.abs

// Camera readings must stay associated with their source even when a dense page has no clear lane.
// Keep the floating reader's existing full-size placement policy separate.
internal fun placeCameraRubyReadings(
    requests: List<RubyRequest>, sourceText: List<ReadingBounds>, gap: Int
): List<ReadingBounds?> {
    val placed = mutableListOf<ReadingBounds>()
    fun overlap(a: ReadingBounds, b: ReadingBounds): Long =
        a.intersection(b)?.let { (it.right - it.left).toLong() * (it.bottom - it.top) } ?: 0L
    return requests.map { request ->
        val clip = request.clip
        if (request.width <= 0 || request.height <= 0 ||
            request.width > clip.right - clip.left || request.height > clip.bottom - clip.top
        ) return@map null
        val anchor = request.anchor
        val x = if (request.vertical) anchor.right + gap else (anchor.left + anchor.right - request.width) / 2
        val y = if (request.vertical) (anchor.top + anchor.bottom - request.height) / 2
            else anchor.top - gap - request.height
        fun bounded(left: Int, top: Int): ReadingBounds {
            val l = left.coerceIn(clip.left, clip.right - request.width)
            val t = top.coerceIn(clip.top, clip.bottom - request.height)
            return ReadingBounds(l, t, l + request.width, t + request.height)
        }
        val preferred = bounded(x, y)
        val candidates = if (request.vertical) listOf(
            preferred,
            bounded(x + request.width + gap, y),
            bounded(anchor.left - gap - request.width, y)
        ) else listOf(
            preferred,
            bounded(x, y - request.height - gap),
            bounded(x, anchor.bottom + gap)
        )
        // A clear preferred position always wins. Only one neighbouring lane or the opposite side
        // is considered; preventing every collision must never send a reading across the page.
        val result = candidates.distinct().minBy { rect ->
            val displacement = abs(rect.left - preferred.left) + abs(rect.top - preferred.top)
            val coversAnchor = if (overlap(rect, anchor) > 0) request.width.toLong() * request.height * 100 else 0L
            coversAnchor + placed.sumOf { overlap(rect, it) } * 4 +
                sourceText.sumOf { overlap(rect, it) } * 2 +
                displacement.toLong() * (if (request.vertical) request.height else request.width)
        }
        placed.add(result)
        result
    }
}

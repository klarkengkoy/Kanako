package com.smyddevstudio.kanako.service

internal data class RubyRequest(
    val anchor: ReadingBounds,
    val clip: ReadingBounds,
    val width: Int,
    val height: Int,
    val vertical: Boolean = false
)

private fun overlapArea(a: ReadingBounds, b: ReadingBounds): Long =
    a.intersection(b)?.let { (it.right - it.left).toLong() * (it.bottom - it.top) } ?: 0L

internal fun placeRubyReadings(
    requests: List<RubyRequest>,
    sourceText: List<ReadingBounds>,
    gap: Int
): List<ReadingBounds?> {
    val placed = mutableListOf<ReadingBounds>()
    return requests.map { request ->
        val clip = request.clip
        if (request.width <= 0 || request.height <= 0 ||
            request.width > clip.right - clip.left || request.height > clip.bottom - clip.top
        ) return@map null
        val anchor = request.anchor
        val preferredX = if (request.vertical) anchor.right + gap
            else (anchor.left + anchor.right - request.width) / 2
        val preferredY = if (request.vertical) (anchor.top + anchor.bottom - request.height) / 2
            else anchor.top - gap - request.height
        val candidates = linkedSetOf<ReadingBounds>()
        fun candidate(x: Int, y: Int) {
            val left = x.coerceIn(clip.left, clip.right - request.width)
            val top = y.coerceIn(clip.top, clip.bottom - request.height)
            candidates.add(ReadingBounds(left, top, left + request.width, top + request.height))
        }
        candidate(preferredX, preferredY)
        if (request.vertical) {
            val step = request.width + gap.coerceAtLeast(1)
            for (lane in 1..((clip.right - clip.left) / step + 1)) {
                candidate(preferredX + lane * step, preferredY)
                candidate(anchor.left - gap - request.width - (lane - 1) * step, preferredY)
            }
        } else {
            val step = request.height + gap.coerceAtLeast(1)
            for (lane in 1..((clip.bottom - clip.top) / step + 1)) {
                candidate(preferredX, preferredY - lane * step)
                candidate(preferredX, anchor.bottom + gap + (lane - 1) * step)
            }
        }
        val result = candidates.minBy { rect ->
            val collisions = placed.sumOf { overlapArea(rect, it) }
            val textOverlap = sourceText.sumOf { overlapArea(rect, it) }
            val distance = kotlin.math.abs(rect.left.toLong() - preferredX) + kotlin.math.abs(rect.top.toLong() - preferredY)
            collisions * 1_000_000L + textOverlap * 10_000L + distance
        }
        placed.add(result)
        result
    }
}

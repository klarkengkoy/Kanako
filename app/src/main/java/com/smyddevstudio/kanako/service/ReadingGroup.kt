package com.smyddevstudio.kanako.service

internal data class ReadingGroup(
    val anchor: ReadingBounds,
    val vertical: Boolean,
    val reading: String,
    val key: String,
    val source: String
)

internal fun selectReadingGroup(
    readings: List<ReadingGroup>, x: Float, y: Float, selected: String?
): String? {
    val hits = readings.filter { x >= it.anchor.left && x < it.anchor.right && y >= it.anchor.top && y < it.anchor.bottom }
    return if (hits.any { it.key == selected }) null else hits.lastOrNull()?.key
}

internal fun placeReadingCallout(
    anchor: ReadingBounds, clip: ReadingBounds, width: Int, height: Int, gap: Int, vertical: Boolean
): ReadingBounds? {
    if (clip.isEmpty || anchor.isEmpty || width <= 0 || height <= 0 ||
        width > clip.right - clip.left || height > clip.bottom - clip.top) return null
    val centerX = (anchor.left + anchor.right) / 2
    val centerY = (anchor.top + anchor.bottom) / 2
    val above = ReadingBounds(centerX - width / 2, anchor.top - gap - height, centerX - width / 2 + width, anchor.top - gap)
    val below = ReadingBounds(centerX - width / 2, anchor.bottom + gap, centerX - width / 2 + width, anchor.bottom + gap + height)
    val right = ReadingBounds(anchor.right + gap, centerY - height / 2, anchor.right + gap + width, centerY - height / 2 + height)
    val left = ReadingBounds(anchor.left - gap - width, centerY - height / 2, anchor.left - gap, centerY - height / 2 + height)
    val candidates = if (vertical) listOf(right, left, above, below) else listOf(above, below, right, left)
    candidates.firstOrNull { clip.contains(it) }?.let { return it }
    return candidates.map { bounds ->
        val x = bounds.left.coerceIn(clip.left, clip.right - width)
        val y = bounds.top.coerceIn(clip.top, clip.bottom - height)
        ReadingBounds(x, y, x + width, y + height)
    }.minBy { bounds ->
        val overlap = anchor.intersection(bounds)
        val area = if (overlap == null) 0L else
            (overlap.right - overlap.left).toLong() * (overlap.bottom - overlap.top)
        area * 1_000_000L + kotlin.math.abs(bounds.left + bounds.right - centerX * 2).toLong() +
            kotlin.math.abs(bounds.top + bounds.bottom - centerY * 2).toLong()
    }
}

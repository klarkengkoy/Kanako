package com.smyddevstudio.kanako.service

internal data class ReadingBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = right <= left || bottom <= top

    fun intersection(other: ReadingBounds): ReadingBounds? =
        ReadingBounds(maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom)).takeUnless { it.isEmpty }

    fun moved(dx: Int, dy: Int) = ReadingBounds(left + dx, top + dy, right + dx, bottom + dy)

    fun contains(other: ReadingBounds): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

internal data class ScrollMovement(
    val windowId: Int,
    val viewport: ReadingBounds,
    val deltaX: Int,
    val deltaY: Int
)

internal data class TrackedReadingBounds(val bounds: ReadingBounds, val clip: ReadingBounds)

internal fun followScroll(
    bounds: ReadingBounds,
    clip: ReadingBounds,
    movement: ScrollMovement
): TrackedReadingBounds? {
    if (bounds.intersection(movement.viewport) == null) return TrackedReadingBounds(bounds, clip)
    val visibleArea = clip.intersection(movement.viewport) ?: return null
    val moved = bounds.moved(-movement.deltaX, -movement.deltaY)
    return if (moved.intersection(visibleArea) != null) TrackedReadingBounds(moved, visibleArea) else null
}

internal data class ScreenUpdate(
    val revision: Long = 0,
    val kind: Kind = Kind.RESET,
    val movement: ScrollMovement? = null,
    val canAutoRead: Boolean = false
) {
    enum class Kind { RESET, SCROLL, CONTENT_PENDING, CONTENT, WINDOW }
}

internal class ScrollOffsetTracker {
    private val offsets = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()

    fun delta(windowId: Int, sourceId: Int, x: Int, y: Int, dx: Int, dy: Int): Pair<Int, Int>? {
        val key = windowId to sourceId
        val previous = offsets[key]
        if (x >= 0 && y >= 0) offsets[key] = x to y
        if (previous != null && x >= 0 && y >= 0) {
            val absoluteDelta = x - previous.first to y - previous.second
            if (absoluteDelta.first != 0 || absoluteDelta.second != 0) return absoluteDelta
        }
        val reportedX = dx.takeUnless { it == -1 } ?: 0
        val reportedY = dy.takeUnless { it == -1 } ?: 0
        return (reportedX to reportedY).takeUnless { it.first == 0 && it.second == 0 }
    }

    fun clear() = offsets.clear()
}

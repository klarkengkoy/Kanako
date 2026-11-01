package com.smyddevstudio.kanako.service

internal fun floatingMenuPosition(
    area: ReadingBounds, anchor: ReadingBounds, width: Int, height: Int, gap: Int
): FloatingPoint {
    val onLeft = anchor.left + anchor.right < area.left + area.right
    val preferredX = if (onLeft) anchor.left else anchor.right - width
    val below = anchor.bottom + gap
    val above = anchor.top - gap - height
    val preferredY = when {
        below + height <= area.bottom -> below
        above >= area.top -> above
        anchor.top - area.top > area.bottom - anchor.bottom -> above
        else -> below
    }
    return FloatingPoint(
        preferredX.coerceIn(area.left, maxOf(area.left, area.right - width)),
        preferredY.coerceIn(area.top, maxOf(area.top, area.bottom - height))
    )
}

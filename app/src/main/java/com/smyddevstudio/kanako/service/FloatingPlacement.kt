package com.smyddevstudio.kanako.service

internal data class FloatingPoint(val x: Int, val y: Int)
internal data class FloatingFrame(
    val origin: FloatingPoint,
    val control: FloatingPoint,
    val anchor: FloatingPoint,
    val menuAbove: Boolean,
    val onLeft: Boolean
)

internal fun floatingAnchor(
    area: ReadingBounds,
    controlSize: Int,
    saved: FloatingPoint? = null
): FloatingPoint {
    val right = (area.right - controlSize).coerceAtLeast(area.left)
    val bottom = (area.bottom - controlSize).coerceAtLeast(area.top)
    return FloatingPoint(
        (saved?.x ?: right).coerceIn(area.left, right),
        (saved?.y ?: (area.top + (bottom - area.top) / 3)).coerceIn(area.top, bottom)
    )
}

internal fun floatingFrame(
    area: ReadingBounds,
    anchor: FloatingPoint,
    controlSize: Int,
    width: Int,
    height: Int
): FloatingFrame {
    val bounded = floatingAnchor(area, controlSize, anchor)
    val onLeft = bounded.x + controlSize / 2 < (area.left + area.right) / 2
    val above = bounded.y + height > area.bottom &&
        bounded.y - area.top > area.bottom - bounded.y - controlSize
    val x = (if (onLeft) bounded.x else bounded.x + controlSize - width)
        .coerceIn(area.left, (area.right - width).coerceAtLeast(area.left))
    val y = (if (above) bounded.y + controlSize - height else bounded.y)
        .coerceIn(area.top, (area.bottom - height).coerceAtLeast(area.top))
    return FloatingFrame(
        FloatingPoint(x, y), FloatingPoint(bounded.x - x, bounded.y - y),
        bounded, above, onLeft
    )
}

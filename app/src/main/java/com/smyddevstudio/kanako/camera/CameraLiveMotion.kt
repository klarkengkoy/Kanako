package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingTarget
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class CameraLiveFrame(
    val layout: CameraAnnotationLayout,
    val groups: List<CameraPhotoReading>
) {
    fun forTarget(target: CameraLiveFrame): CameraLiveFrame {
        val current = groups.associateBy { it.key }
        val reusable = target.groups.filter { next ->
            current[next.key]?.let {
                it.source == next.source && it.reading == next.reading && it.vertical == next.vertical
            } == true
        }.mapTo(mutableSetOf()) { it.key }
        val labels = layout.labels.associateBy { it.target.key }
        return CameraLiveFrame(
            CameraAnnotationLayout(target.layout.labels.map { next ->
                labels[next.target.key]?.takeIf {
                    next.target.key in reusable && it.reading == next.reading
                } ?: next
            }),
            target.groups.map { next -> current[next.key]?.takeIf { next.key in reusable } ?: next }
        )
    }

    val targets: List<ReadingTarget>
        get() {
            val labels = layout.labels.associateBy { it.target.key }
            return groups.map { ReadingTarget(it.key, it.highlight, labels[it.key]?.bounds ?: it.anchor) }
        }
}

internal class CameraLiveMotion {
    private data class Item(val group: CameraPhotoReading, val label: CameraRuby?)
    private data class Move(val from: Item, val to: Item, val started: Long, val duration: Long)
    private var moves = emptyList<Move>()

    fun retarget(target: CameraLiveFrame, now: Long, animate: Boolean) {
        val current = items(now).associateBy { it.group.key }
        val labels = target.layout.labels.associateBy { it.target.key }
        moves = target.groups.map { group ->
            val next = Item(group, labels[group.key])
            val previous = current[group.key]
            val slide = animate && previous != null && canSlide(previous.group, group)
            Move(if (slide) requireNotNull(previous) else next, next, now, if (slide) 90L else 0L)
        }
    }

    fun isMoving(now: Long) = moves.any { now - it.started < it.duration }

    fun frameAt(now: Long): CameraLiveFrame {
        val items = items(now)
        return CameraLiveFrame(CameraAnnotationLayout(items.mapNotNull { it.label }), items.map { it.group })
    }

    private fun items(now: Long): List<Item> = moves.map { move ->
        val fraction = if (move.duration == 0L) 1f else
            ((now - move.started).toFloat() / move.duration).coerceIn(0f, 1f)
        if (fraction >= 1f) move.to else {
            fun value(a: Float, b: Float) = a + (b - a) * fraction
            fun bounds(a: ReadingBounds, b: ReadingBounds) = ReadingBounds(
                value(a.left.toFloat(), b.left.toFloat()).roundToInt(),
                value(a.top.toFloat(), b.top.toFloat()).roundToInt(),
                value(a.right.toFloat(), b.right.toFloat()).roundToInt(),
                value(a.bottom.toFloat(), b.bottom.toFloat()).roundToInt())
            val before = move.from.group
            val after = move.to.group
            val anchor = bounds(before.anchor, after.anchor)
            val highlight = bounds(before.highlight, after.highlight)
            val outlines = if (before.outlines.size == after.outlines.size) {
                after.outlines.mapIndexed { index, polygon ->
                    CameraPolygon(polygon.points.mapIndexed { pointIndex, point ->
                        val previous = before.outlines[index].points[pointIndex]
                        CameraPoint(value(previous.x, point.x), value(previous.y, point.y))
                    })
                }
            } else after.outlines
            val label = move.to.label?.let { next ->
                val box = move.from.label?.let { bounds(it.bounds, next.bounds) } ?: next.bounds
                next.copy(bounds = box, target = ReadingTarget(after.key, highlight, box))
            }
            Item(after.copy(group = after.group.copy(anchor = anchor, highlight = highlight), outlines = outlines), label)
        }
    }

    private fun canSlide(a: CameraPhotoReading, b: CameraPhotoReading): Boolean {
        if (a.source != b.source || a.reading != b.reading || a.vertical != b.vertical) return false
        val width = a.anchor.right - a.anchor.left
        val height = a.anchor.bottom - a.anchor.top
        val limit = max(24f, min(width, height) * 2f)
        return abs(a.anchor.left - b.anchor.left) <= limit && abs(a.anchor.right - b.anchor.right) <= limit &&
            abs(a.anchor.top - b.anchor.top) <= limit && abs(a.anchor.bottom - b.anchor.bottom) <= limit
    }
}

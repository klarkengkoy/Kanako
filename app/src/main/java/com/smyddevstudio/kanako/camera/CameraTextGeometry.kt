package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.recognitionSlices

internal data class CameraPoint(val x: Float, val y: Float)

internal data class CameraPolygon(val points: List<CameraPoint>) {
    init { require(points.size == 4) }

    val bounds: CameraRect
        get() = CameraRect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

private fun CameraRect.polygon() = CameraPolygon(listOf(
    CameraPoint(left, top), CameraPoint(right, top), CameraPoint(right, bottom), CameraPoint(left, bottom)
))

internal fun cameraSpanPolygon(span: CameraTextSpan, start: Int, end: Int, vertical: Boolean): CameraPolygon {
    val supplied = span.corners
    val bounds = span.bounds
    val valid = supplied.size == 4 && supplied.all {
        it.x.isFinite() && it.y.isFinite() && it.x >= bounds.left - 2f && it.x <= bounds.right + 2f &&
            it.y >= bounds.top - 2f && it.y <= bounds.bottom + 2f
    } && supplied.indices.map { index ->
        val a = supplied[index]
        val b = supplied[(index + 1) % 4]
        val c = supplied[(index + 2) % 4]
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
    }.let { crosses -> crosses.all { it > 0f } || crosses.all { it < 0f } }
    val points = if (valid) supplied else bounds.polygon().points
    val length = (span.end - span.start).coerceAtLeast(1).toFloat()
    val from = ((start - span.start) / length).coerceIn(0f, 1f)
    val to = ((end - span.start) / length).coerceIn(from, 1f)
    fun interpolate(a: CameraPoint, b: CameraPoint, fraction: Float) =
        CameraPoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
    return if (vertical) CameraPolygon(listOf(
        interpolate(points[0], points[3], from), interpolate(points[1], points[2], from),
        interpolate(points[1], points[2], to), interpolate(points[0], points[3], to)
    )) else CameraPolygon(listOf(
        interpolate(points[0], points[1], from), interpolate(points[0], points[1], to),
        interpolate(points[3], points[2], to), interpolate(points[3], points[2], from)
    ))
}

internal fun cameraTokenPolygons(line: CameraReadingLine, start: Int, end: Int): List<CameraPolygon> {
    val vertical = line.isVertical
    return recognitionSlices(start, end, line.spans.map { it.start to it.end }).map { piece ->
        val span = piece.span?.let { line.spans[it] } ?: CameraTextSpan(0, line.text.length, line.bounds)
        cameraSpanPolygon(span, piece.start, piece.end, vertical)
    }
}

internal fun cameraWordOutline(line: CameraReadingLine, start: Int, end: Int): CameraPolygon {
    val polygons = cameraTokenPolygons(line, start, end)
    if (polygons.size == 1) return polygons.single()
    var dx = 0f
    var dy = 0f
    polygons.forEach { polygon ->
        val edge = if (line.isVertical) polygon.points[3] else polygon.points[1]
        dx += edge.x - polygon.points[0].x
        dy += edge.y - polygon.points[0].y
    }
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 0.001f) return cameraTokenBounds(line, start, end).polygon()
    val along = CameraPoint(dx / length, dy / length)
    val across = if (line.isVertical) CameraPoint(along.y, -along.x) else CameraPoint(-along.y, along.x)
    val points = polygons.flatMap { it.points }
    fun project(point: CameraPoint, axis: CameraPoint) = point.x * axis.x + point.y * axis.y
    val first = points.minOf { project(it, along) }
    val last = points.maxOf { project(it, along) }
    val near = points.minOf { project(it, across) }
    val far = points.maxOf { project(it, across) }
    fun point(u: Float, v: Float) = CameraPoint(along.x * u + across.x * v, along.y * u + across.y * v)
    return CameraPolygon(if (line.isVertical) listOf(point(first, near), point(first, far),
        point(last, far), point(last, near)) else listOf(point(first, near), point(last, near),
        point(last, far), point(first, far)))
}

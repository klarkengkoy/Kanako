package com.smyddevstudio.kanako.camera

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
    val vertical = line.bounds.height > line.bounds.width
    val spans = line.spans.filter { it.start < end && it.end > start }
    return if (spans.isEmpty()) listOf(cameraSpanPolygon(
        CameraTextSpan(0, line.text.length, line.bounds), start, end, vertical
    )) else spans.map { cameraSpanPolygon(it, maxOf(start, it.start), minOf(end, it.end), vertical) }
}

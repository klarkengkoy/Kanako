package com.smyddevstudio.kanako.camera

import kotlin.math.max
import kotlin.math.min

internal data class CameraRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

internal data class CameraImageTransform(val scale: Float, val left: Float, val top: Float)

internal fun fitCameraImage(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): CameraImageTransform? {
    if (minOf(imageWidth, imageHeight, viewWidth, viewHeight) <= 0) return null
    val scale = min(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    return CameraImageTransform(scale, (viewWidth - imageWidth * scale) / 2f, (viewHeight - imageHeight * scale) / 2f)
}

internal data class CameraPhotoGesture(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun update(
        factor: Float, dx: Float, dy: Float, centroidX: Float, centroidY: Float,
        imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int
    ): CameraPhotoGesture {
        val fit = fitCameraImage(imageWidth, imageHeight, viewWidth, viewHeight) ?: return CameraPhotoGesture()
        if (!listOf(factor, dx, dy, centroidX, centroidY).all { it.isFinite() } || factor <= 0) return this
        val next = (zoom * factor).coerceIn(1f, 6f)
        if (next == 1f) return CameraPhotoGesture()
        val ratio = next / zoom
        val limitX = max(0f, (imageWidth * fit.scale * next - viewWidth) / 2f)
        val limitY = max(0f, (imageHeight * fit.scale * next - viewHeight) / 2f)
        val anchorX = centroidX - viewWidth / 2f
        val anchorY = centroidY - viewHeight / 2f
        return CameraPhotoGesture(
            next,
            (anchorX - (anchorX - x) * ratio + dx).coerceIn(-limitX, limitX),
            (anchorY - (anchorY - y) * ratio + dy).coerceIn(-limitY, limitY)
        )
    }
}

// Use the actual OCR character/element span when available. Equal line advances are only a fallback.
internal fun cameraTokenBounds(line: CameraReadingLine, start: Int, end: Int): CameraRect {
    val vertical = line.bounds.height > line.bounds.width
    fun slice(bounds: CameraRect, from: Float, to: Float) = if (vertical)
        CameraRect(bounds.left, bounds.top + bounds.height * from, bounds.right, bounds.top + bounds.height * to)
    else CameraRect(bounds.left + bounds.width * from, bounds.top, bounds.left + bounds.width * to, bounds.bottom)
    val spans = line.spans.filter { it.start < end && it.end > start }
    if (spans.isEmpty()) {
        val length = line.text.length.coerceAtLeast(1).toFloat()
        return slice(line.bounds, start / length, end / length)
    }
    val boxes = spans.map { span ->
        val length = (span.end - span.start).coerceAtLeast(1).toFloat()
        slice(span.bounds, (maxOf(start, span.start) - span.start) / length, (minOf(end, span.end) - span.start) / length)
    }
    return CameraRect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
}

internal enum class CameraReadMode { LIVE, CAPTURING, PHOTO }

internal class CameraRequestGate {
    private var revision = 0L
    private var active = false
    var mode = CameraReadMode.LIVE
        private set

    @Synchronized fun resumeLive(): Long {
        if (mode != CameraReadMode.LIVE) return revision
        active = true
        return ++revision
    }

    @Synchronized fun pauseLive() {
        active = false
        if (mode != CameraReadMode.PHOTO) {
            mode = CameraReadMode.LIVE
            revision++
        }
    }

    @Synchronized fun liveTicket(): Long? = revision.takeIf { active && mode == CameraReadMode.LIVE }

    @Synchronized fun beginCapture(): Long? {
        if (!active || mode != CameraReadMode.LIVE) return null
        mode = CameraReadMode.CAPTURING
        return ++revision
    }

    @Synchronized fun acceptCapture(ticket: Long): Boolean {
        if (ticket != revision || mode != CameraReadMode.CAPTURING || !active) return false
        mode = CameraReadMode.PHOTO
        return true
    }

    @Synchronized fun acceptsPhoto(ticket: Long) = ticket == revision && mode == CameraReadMode.PHOTO
    @Synchronized fun acceptsLive(ticket: Long) = ticket == revision && active && mode == CameraReadMode.LIVE

    @Synchronized fun invalidateLive() {
        if (mode == CameraReadMode.LIVE) revision++
    }

    @Synchronized fun returnToLive() {
        revision++
        mode = CameraReadMode.LIVE
        active = false
    }
}

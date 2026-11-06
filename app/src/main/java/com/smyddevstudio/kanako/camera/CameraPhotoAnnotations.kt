package com.smyddevstudio.kanako.camera

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.service.kanjiAnnotation
import com.smyddevstudio.kanako.service.drawReadingHighlight
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class MeasuredPhotoReading(val layouts: List<StaticLayout>, val padding: Int)

internal fun cameraPhotoReadings(
    lines: List<CameraReadingLine>, width: Int, height: Int, script: ScriptType,
    checkCancellation: () -> Unit = {}
): List<CameraPhotoReading> {
    if (width <= 0 || height <= 0) return emptyList()
    val clip = ReadingBounds(0, 0, width, height)
    val formatter = JapaneseTextTokenizer()
    return buildList {
        lines.forEachIndexed { lineIndex, line ->
            checkCancellation()
            val vertical = line.isVertical
            var offset = 0
            line.segments.forEach { segment ->
                checkCancellation()
                val start = line.text.indexOf(segment.text, offset).takeIf { it >= 0 } ?: offset
                offset = start + segment.text.length
                val raw = segment.reading
                if (raw.isNullOrEmpty() || segment.text.none {
                    it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' || it == '々'
                }) return@forEach
                val annotation = kanjiAnnotation(segment.text, formatter.formatReading(raw, ScriptType.KANA))
                val reading = formatter.formatReading(annotation.reading, script)
                if (reading.isEmpty()) return@forEach
                val outline = cameraWordOutline(line, start + annotation.start, start + annotation.end)
                val token = outline.bounds
                val anchor = clip.intersection(ReadingBounds(token.left.roundToInt(), token.top.roundToInt(),
                    token.right.roundToInt(), token.bottom.roundToInt())) ?: return@forEach
                add(CameraPhotoReading(anchor, vertical, reading, line.trackingKey ?: "$lineIndex:$start:${segment.text}",
                    segment.text.substring(annotation.start, annotation.end)).copy(
                        outlines = listOf(outline)))
            }
        }
    }
}

internal fun layoutCameraPhotoAnnotations(
    lines: List<CameraReadingLine>, width: Int, height: Int, minimumFontPixels: Float, script: ScriptType,
    checkCancellation: () -> Unit = {}
): CameraAnnotationLayout = layoutCameraPhotoReadings(
    cameraPhotoReadings(lines, width, height, script, checkCancellation),
    lines.map { ReadingBounds(it.bounds.left.roundToInt(), it.bounds.top.roundToInt(),
        it.bounds.right.roundToInt(), it.bounds.bottom.roundToInt()) },
    width, height, minimumFontPixels, checkCancellation = checkCancellation
)

internal fun layoutCameraPhotoReadings(
    readings: List<CameraPhotoReading>, sourceText: List<ReadingBounds>,
    width: Int, height: Int, minimumFontPixels: Float,
    selected: String? = null,
    selectionClip: ReadingBounds = ReadingBounds(0, 0, width, height),
    checkCancellation: () -> Unit = {}
): CameraAnnotationLayout {
    if (width <= 0 || height <= 0 || !minimumFontPixels.isFinite() || minimumFontPixels <= 0f) {
        return CameraAnnotationLayout(emptyList())
    }
    val clip = ReadingBounds(0, 0, width, height)
    fun measure(index: Int, font: Float, callout: Boolean = false): PhotoRubyMeasurement<MeasuredPhotoReading>? {
        checkCancellation()
        val item = readings[index]
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = font
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val padding = (font / 10).roundToInt().coerceAtLeast(1)
        val gap = (font / 16).roundToInt().coerceAtLeast(1)
        val available = (if (callout) selectionClip.right - selectionClip.left else width) - padding * 2
        if (available <= 0) return null
        val text = if (item.vertical && !callout) item.reading.toList().joinToString("\n") else item.reading
        val desired = ceil(Layout.getDesiredWidth(text, paint).toDouble())
        if (!desired.isFinite() || (!callout && desired > available)) return null
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, desired.toInt().coerceIn(1, available))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        return PhotoRubyMeasurement(layout.width + padding * 2, layout.height + padding * 2, gap,
            MeasuredPhotoReading(listOf(layout), padding))
    }
    fun ruby(index: Int, bounds: ReadingBounds, size: PhotoRubyMeasurement<MeasuredPhotoReading>, callout: Boolean = false): CameraRuby {
        val item = readings[index]
        return CameraRuby(bounds, if (callout) selectionClip else clip, size.content.layouts,
            size.content.padding, size.gap, item.vertical && !callout, item.reading,
            ReadingTarget(item.key, item.anchor, bounds))
    }
    val selectedIndex = readings.indexOfFirst { it.key == selected }
    if (selectedIndex >= 0) {
        val item = readings[selectedIndex]
        val anchor = item.anchor.intersection(selectionClip) ?: return CameraAnnotationLayout(emptyList())
        val sourceSize = if (item.vertical) item.anchor.right - item.anchor.left else item.anchor.bottom - item.anchor.top
        val natural = maxOf(minimumFontPixels, sourceSize * 0.5f)
        var size = measure(selectedIndex, natural, callout = true)
            ?: measure(selectedIndex, minimumFontPixels, callout = true) ?: return CameraAnnotationLayout(emptyList())
        if (size.height > selectionClip.bottom - selectionClip.top) {
            size = measure(selectedIndex, minimumFontPixels, callout = true) ?: return CameraAnnotationLayout(emptyList())
        }
        val bounds = placeCameraPhotoCallout(anchor, selectionClip, size.width, size.height, size.gap, item.vertical)
            ?: return CameraAnnotationLayout(emptyList())
        return CameraAnnotationLayout(listOf(ruby(selectedIndex, bounds, size, callout = true)))
    }
    val placements = placeCameraPhotoReadings(
        readings.map { PhotoRubyRequest(it.anchor, it.vertical) }, clip, sourceText, minimumFontPixels,
        measure = { index, font -> measure(index, font) }, checkCancellation = checkCancellation
    )
    return CameraAnnotationLayout(placements.mapIndexedNotNull { index, placement ->
        placement?.let { ruby(index, it.bounds, it.measurement) }
    })
}

internal fun drawCameraPhotoHighlights(
    canvas: Canvas, readings: List<CameraPhotoReading>, selected: String?, strokeWidth: Float
) {
    if (!strokeWidth.isFinite() || strokeWidth <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
    readings.forEach { item ->
        if (item.outlines.isEmpty()) {
            drawReadingHighlight(canvas, item.anchor, item.key == selected, strokeWidth)
        } else canvas.withClip(item.anchor.left, item.anchor.top, item.anchor.right, item.anchor.bottom) {
            item.outlines.forEach { polygon ->
                val path = Path().apply {
                    moveTo(polygon.points.first().x, polygon.points.first().y)
                    polygon.points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                canvas.withSave {
                    clipPath(path)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(if (item.key == selected) 48 else 20, 255, 255, 255)
                    drawPath(path, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = strokeWidth * if (item.key == selected) 4f else 3f
                    paint.color = Color.argb(230, 20, 20, 20)
                    drawPath(path, paint)
                    paint.strokeWidth = strokeWidth * if (item.key == selected) 2f else 1f
                    paint.color = Color.WHITE
                    drawPath(path, paint)
                }
            }
        }
    }
}

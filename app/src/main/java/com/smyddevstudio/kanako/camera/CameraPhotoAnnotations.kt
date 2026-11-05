package com.smyddevstudio.kanako.camera

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.service.kanjiAnnotation
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class PhotoReading(
    val anchor: ReadingBounds,
    val vertical: Boolean,
    val reading: String,
    val key: String
)

private data class MeasuredPhotoReading(val layouts: List<StaticLayout>, val padding: Int)

internal fun layoutCameraPhotoAnnotations(
    lines: List<CameraReadingLine>,
    width: Int,
    height: Int,
    minimumFontPixels: Float,
    script: ScriptType,
    checkCancellation: () -> Unit = {}
): CameraAnnotationLayout {
    if (width <= 0 || height <= 0 || !minimumFontPixels.isFinite() || minimumFontPixels <= 0f) {
        return CameraAnnotationLayout(emptyList())
    }
    val clip = ReadingBounds(0, 0, width, height)
    val formatter = JapaneseTextTokenizer()
    val readings = buildList {
        lines.forEachIndexed { lineIndex, line ->
            checkCancellation()
            val vertical = line.bounds.height > line.bounds.width
            var offset = 0
            line.segments.forEach { segment ->
                val start = line.text.indexOf(segment.text, offset).takeIf { it >= 0 } ?: offset
                offset = start + segment.text.length
                val raw = segment.reading
                if (raw.isNullOrEmpty()) return@forEach
                val annotation = kanjiAnnotation(segment.text, formatter.formatReading(raw, ScriptType.KANA))
                val reading = formatter.formatReading(annotation.reading, script)
                if (reading.isEmpty()) return@forEach
                val token = cameraTokenBounds(line, start + annotation.start, start + annotation.end)
                add(PhotoReading(
                    ReadingBounds(token.left.roundToInt(), token.top.roundToInt(), token.right.roundToInt(), token.bottom.roundToInt()),
                    vertical, reading, "$lineIndex:$start:${segment.text}"
                ))
            }
        }
    }
    val obstacles = lines.map { line ->
        ReadingBounds(line.bounds.left.roundToInt(), line.bounds.top.roundToInt(),
            line.bounds.right.roundToInt(), line.bounds.bottom.roundToInt())
    }
    val placements = placeCameraPhotoReadings(
        readings.map { PhotoRubyRequest(it.anchor, it.vertical) }, clip, obstacles, minimumFontPixels,
        measure = { index, font ->
            val item = readings[index]
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = font
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val padding = (font / 10).roundToInt().coerceAtLeast(1)
            val gap = (font / 16).roundToInt().coerceAtLeast(1)
            val text = if (item.vertical) item.reading.toList().joinToString("\n") else item.reading
            val desired = ceil(Layout.getDesiredWidth(text, paint).toDouble())
            if (!desired.isFinite() || desired > width - padding * 2) null
            else {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, desired.toInt().coerceAtLeast(1))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
                PhotoRubyMeasurement(layout.width + padding * 2, layout.height + padding * 2, gap,
                    MeasuredPhotoReading(listOf(layout), padding))
            }
        },
        checkCancellation = checkCancellation
    )
    return CameraAnnotationLayout(placements.mapIndexedNotNull { index, placement ->
        placement?.let {
            val reading = readings[index]
            CameraRuby(it.bounds, clip, it.measurement.content.layouts, it.measurement.content.padding,
                it.measurement.gap, reading.vertical, reading.reading,
                ReadingTarget(reading.key, reading.anchor, it.bounds))
        }
    })
}

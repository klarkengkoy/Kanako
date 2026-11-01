package com.smyddevstudio.kanako.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.RubyRequest
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.service.kanjiAnnotation
import com.smyddevstudio.kanako.service.readingDrawOrder
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class CameraAnnotationColors(val text: Int, val plate: Int)

internal data class CameraRuby(
    val bounds: ReadingBounds,
    val clip: ReadingBounds,
    val layouts: List<StaticLayout>,
    val padding: Int,
    val gap: Int,
    val vertical: Boolean,
    val reading: String,
    val target: ReadingTarget
)

internal data class CameraAnnotationLayout(val labels: List<CameraRuby>)

internal fun layoutCameraAnnotations(
    lines: List<CameraReadingLine>, width: Int, height: Int, fontPixels: Float, script: ScriptType
): CameraAnnotationLayout {
    if (width <= 0 || height <= 0 || !fontPixels.isFinite() || fontPixels <= 0) return CameraAnnotationLayout(emptyList())
    val clip = ReadingBounds(0, 0, width, height)
    val padding = (fontPixels / 10).roundToInt().coerceAtLeast(1)
    val gap = (fontPixels / 16).roundToInt().coerceAtLeast(1)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontPixels
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    val formatter = JapaneseTextTokenizer()
    data class Pending(val request: RubyRequest, val layouts: List<StaticLayout>, val reading: String, val key: String)
    val pending = buildList {
        lines.forEachIndexed { lineIndex, line ->
            val vertical = line.bounds.height > line.bounds.width
            var offset = 0
            line.segments.forEach { segment ->
                val start = line.text.indexOf(segment.text, offset).takeIf { it >= 0 } ?: offset
                offset = start + segment.text.length
                val raw = segment.reading
                if (raw.isNullOrEmpty()) return@forEach
                val annotation = kanjiAnnotation(segment.text, formatter.formatReading(raw, ScriptType.KANA))
                val reading = formatter.formatReading(annotation.reading, script)
                val token = cameraTokenBounds(line, start + annotation.start, start + annotation.end)
                val anchor = ReadingBounds(token.left.roundToInt(), token.top.roundToInt(), token.right.roundToInt(), token.bottom.roundToInt())
                if (clip.intersection(anchor) == null) return@forEach
                val maxWidth = width - padding * 2
                if (maxWidth <= 0) return@forEach
                val chunks = if (vertical) {
                    val rows = ((height - padding * 2) / paint.fontSpacing).toInt().coerceAtLeast(1)
                    reading.chunked(rows).map { it.toList().joinToString("\n") }
                } else listOf(reading)
                val layouts = chunks.map { chunk ->
                    val desiredWidth = ceil(Layout.getDesiredWidth(chunk, paint).toDouble()).toInt().coerceIn(1, maxWidth)
                    StaticLayout.Builder.obtain(chunk, 0, chunk.length, paint, desiredWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setIncludePad(false)
                        .build()
                }
                add(Pending(
                    RubyRequest(anchor, clip, layouts.sumOf { it.width } + (layouts.size - 1) * gap + padding * 2,
                        layouts.maxOf { it.height } + padding * 2, vertical), layouts, reading, "$lineIndex:$start:${segment.text}"
                ))
            }
        }
    }
    val positions = placeCameraRubyReadings(pending.map { it.request }, gap)
    return CameraAnnotationLayout(pending.mapIndexedNotNull { index, item ->
        positions[index]?.let { CameraRuby(it, clip, item.layouts, padding, gap, item.request.vertical, item.reading,
            ReadingTarget(item.key, item.request.anchor, it)) }
    })
}

internal fun drawCameraAnnotations(canvas: Canvas, layout: CameraAnnotationLayout, colors: CameraAnnotationColors, selected: String? = null) {
    val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.plate }
    readingDrawOrder(layout.labels.map { it.target }, selected).forEach { index ->
        val ruby = layout.labels[index]
        canvas.withClip(ruby.clip.left, ruby.clip.top, ruby.clip.right, ruby.clip.bottom) {
        canvas.drawRoundRect(
            ruby.bounds.left.toFloat(), ruby.bounds.top.toFloat(), ruby.bounds.right.toFloat(), ruby.bounds.bottom.toFloat(),
            ruby.padding * 2f, ruby.padding * 2f, platePaint
        )
        var x = if (ruby.vertical) ruby.bounds.right - ruby.padding else ruby.bounds.left + ruby.padding
        ruby.layouts.forEach { text ->
            if (ruby.vertical) x -= text.width
            text.paint.color = colors.text
            if (Color.alpha(colors.plate) == 0) text.paint.setShadowLayer(2f, 0f, 0f, Color.WHITE) else text.paint.clearShadowLayer()
            canvas.withTranslation(x.toFloat(), (ruby.bounds.top + ruby.padding).toFloat()) {
                text.draw(this)
            }
            if (ruby.vertical) x -= ruby.gap else x += text.width + ruby.gap
        }
        }
    }
}

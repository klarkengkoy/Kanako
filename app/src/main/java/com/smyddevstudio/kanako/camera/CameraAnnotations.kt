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
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.model.preferredReadingFontPixels
import com.smyddevstudio.kanako.ui.theme.ReadingAppearanceTokens
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.RubyRequest
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.service.readingDrawOrder
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class CameraAnnotationColors(
    val text: Int,
    val plate: Int,
    val cornerRadius: Float? = null,
    val shadowRadius: Float = 2f
)

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
    lines: List<CameraReadingLine>, width: Int, height: Int, fontPixels: Float, script: ScriptType,
    readingTypeface: Typeface? = null,
    sizeScale: Float = 1f,
    paddingPixels: Int? = null,
    gapPixels: Int? = null
): CameraAnnotationLayout {
    if (width <= 0 || height <= 0 || !fontPixels.isFinite() || fontPixels <= 0) return CameraAnnotationLayout(emptyList())
    val clip = ReadingBounds(0, 0, width, height)
    val placementGap = gapPixels?.coerceAtLeast(1) ?: (fontPixels / 16).roundToInt().coerceAtLeast(1)
    data class Pending(
        val request: RubyRequest, val layouts: List<StaticLayout>, val reading: String,
        val key: String, val highlight: ReadingBounds, val padding: Int, val gap: Int
    )
    val pending = buildList {
        cameraPhotoReadings(lines, width, height, script).forEach { item ->
            val anchor = item.anchor
            val vertical = item.vertical
            val reading = item.reading
            val sourceSize = if (vertical) anchor.right - anchor.left else anchor.bottom - anchor.top
            val font = preferredReadingFontPixels(fontPixels, sourceSize.toFloat(), sizeScale)
            val padding = paddingPixels?.coerceAtLeast(1) ?: (font / 10).roundToInt().coerceAtLeast(1)
            val gap = gapPixels?.coerceAtLeast(1) ?: (font / 16).roundToInt().coerceAtLeast(1)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = font
                typeface = readingTypeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
                if (readingTypeface != null) setFontVariationSettings("'wght' 500")
            }
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
                    .setLineSpacing(0f, ReadingAppearanceTokens.lineHeightMultiplier)
                    .build()
            }
            add(Pending(
                RubyRequest(anchor, clip, layouts.sumOf { it.width } + (layouts.size - 1) * gap + padding * 2,
                    layouts.maxOf { it.height } + padding * 2, vertical),
                layouts, reading, item.key, item.highlight, padding, gap
            ))
        }
    }
    val positions = placeCameraRubyReadings(pending.map { it.request }, placementGap)
    return CameraAnnotationLayout(pending.mapIndexedNotNull { index, item ->
        positions[index]?.let {
            CameraRuby(it, clip, item.layouts, item.padding, item.gap, item.request.vertical, item.reading,
                ReadingTarget(item.key, item.highlight, it))
        }
    })
}

internal fun drawCameraAnnotations(canvas: Canvas, layout: CameraAnnotationLayout, colors: CameraAnnotationColors, selected: String? = null) {
    val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.plate }
    readingDrawOrder(layout.labels.map { it.target }, selected).forEach { index ->
        val ruby = layout.labels[index]
        canvas.withClip(ruby.clip.left, ruby.clip.top, ruby.clip.right, ruby.clip.bottom) {
        canvas.drawRoundRect(
            ruby.bounds.left.toFloat(), ruby.bounds.top.toFloat(), ruby.bounds.right.toFloat(), ruby.bounds.bottom.toFloat(),
            colors.cornerRadius ?: ruby.padding * 2f, colors.cornerRadius ?: ruby.padding * 2f, platePaint
        )
        var x = if (ruby.vertical) ruby.bounds.right - ruby.padding else ruby.bounds.left + ruby.padding
        ruby.layouts.forEach { text ->
            if (ruby.vertical) x -= text.width
            text.paint.color = colors.text
            if (Color.alpha(colors.plate) == 0) text.paint.setShadowLayer(colors.shadowRadius, 0f, 0f, Color.WHITE) else text.paint.clearShadowLayer()
            canvas.withTranslation(x.toFloat(), (ruby.bounds.top + ruby.padding).toFloat()) {
                text.draw(this)
            }
            if (ruby.vertical) x -= ruby.gap else x += text.width + ruby.gap
        }
        }
    }
}

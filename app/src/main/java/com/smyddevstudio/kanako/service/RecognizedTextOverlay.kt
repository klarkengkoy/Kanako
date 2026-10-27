package com.smyddevstudio.kanako.service

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.CreamBackgroundDark
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlin.math.roundToInt

private data class MeasuredRuby(val request: RubyRequest, val layouts: List<TextLayoutResult>)

@Composable
internal fun RecognizedTextOverlayContent(
    lines: List<RecognizedTextLine>,
    preferences: KanakoPreferences,
    origin: IntOffset,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tokenizer = remember { JapaneseTextTokenizer() }
    val tokenized = remember(lines.map { it.text }, preferences.scriptType) {
        lines.map { it.text }.distinct().associateWith { tokenizer.tokenize(it, preferences.scriptType) }
    }
    val padding = with(density) { 2.dp.roundToPx() }
    val gap = with(density) { 2.dp.roundToPx() }.coerceAtLeast(1)
    val style = TextStyle(
        fontSize = preferences.furiganaSize.sizeSp.sp,
        lineHeight = (preferences.furiganaSize.sizeSp * 1.2f).sp,
        fontWeight = FontWeight.Medium
    )
    val measured = remember(lines, tokenized, measurer, density, style, padding, gap) {
        buildList {
            lines.forEach { line ->
                val vertical = line.bounds.height() > line.bounds.width()
                val advance = (if (vertical) line.bounds.height() else line.bounds.width()).toFloat() /
                    line.text.length.coerceAtLeast(1)
                var cursor = 0
                tokenized.getValue(line.text).forEach { segment ->
                    val start = cursor
                    cursor += segment.text.length
                    val reading = segment.reading
                    val token = if (vertical) ReadingBounds(
                        line.bounds.left, (line.bounds.top + start * advance).roundToInt(),
                        line.bounds.right, (line.bounds.top + cursor * advance).roundToInt()
                    ) else ReadingBounds(
                        (line.bounds.left + start * advance).roundToInt(), line.bounds.top,
                        (line.bounds.left + cursor * advance).roundToInt(), line.bounds.bottom
                    )
                    val widthAvailable = line.clip.right - line.clip.left - padding * 2
                    val heightAvailable = line.clip.bottom - line.clip.top - padding * 2
                    if (!reading.isNullOrEmpty() && line.clip.contains(token) && widthAvailable > 0 && heightAvailable > 0) {
                        val chunks = if (vertical) {
                            val rowHeight = reading.toSet().maxOf {
                                measurer.measure(it.toString(), style).size.height
                            }.coerceAtLeast(1)
                            val rows = (heightAvailable / rowHeight).coerceAtLeast(1)
                            reading.chunked(rows).map { it.toList().joinToString("\n") }
                        } else listOf(reading)
                        val layouts = chunks.map { text ->
                            measurer.measure(text, style, constraints = Constraints(maxWidth = widthAvailable))
                        }
                        val width = layouts.sumOf { it.size.width } + (layouts.size - 1) * gap + padding * 2
                        val height = layouts.maxOf { it.size.height } + padding * 2
                        add(MeasuredRuby(RubyRequest(token, line.clip, width, height, vertical), layouts))
                    }
                }
            }
        }
    }
    val placement = remember(measured, lines, gap) {
        placeRubyReadings(
            measured.map { it.request },
            lines.map { ReadingBounds(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
            gap
        )
    }
    val plateColor = when (preferences.annotationPlateStyle) {
        AnnotationPlateStyle.CLEAR -> Color.Transparent
        AnnotationPlateStyle.WASHI_CAPSULE -> CreamBackgroundDark
        AnnotationPlateStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.inverseSurface
        AnnotationPlateStyle.SUBTLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rubyColor = if (preferences.annotationPlateStyle == AnnotationPlateStyle.HIGH_CONTRAST)
        MaterialTheme.colorScheme.inverseOnSurface else Color(preferences.furiganaColor.colorHex)
    val accessibleReadings = measured.mapIndexedNotNull { index, ruby ->
        if (placement[index] == null) null else AnnotatedString(
            ruby.layouts.joinToString("") { it.layoutInput.text.text.replace("\n", "") }
        )
    }
    Canvas(modifier.fillMaxSize().semantics {
        text = AnnotatedString(accessibleReadings.joinToString(", ") { it.text })
    }) {
        measured.forEachIndexed { index, ruby ->
            val rect = placement[index] ?: return@forEachIndexed
            val viewport = ruby.request.clip
            clipRect(
                (viewport.left - origin.x).toFloat(), (viewport.top - origin.y).toFloat(),
                (viewport.right - origin.x).toFloat(), (viewport.bottom - origin.y).toFloat()
            ) {
                val left = (rect.left - origin.x).toFloat()
                val top = (rect.top - origin.y).toFloat()
                val anchor = ruby.request.anchor
                if (ruby.request.vertical && rect.left - anchor.right > gap * 2) {
                    drawLine(rubyColor.copy(alpha = 0.5f),
                        Offset((anchor.right - origin.x).toFloat(), (anchor.top + anchor.bottom) / 2f - origin.y),
                        Offset(left, top + (rect.bottom - rect.top) / 2f), 1.dp.toPx())
                } else if (!ruby.request.vertical && anchor.top - rect.bottom > gap * 2) {
                    drawLine(rubyColor.copy(alpha = 0.5f),
                        Offset((anchor.left + anchor.right) / 2f - origin.x, (anchor.top - origin.y).toFloat()),
                        Offset(left + (rect.right - rect.left) / 2f, (rect.bottom - origin.y).toFloat()), 1.dp.toPx())
                }
                drawRoundRect(plateColor, Offset(left, top),
                    Size((rect.right - rect.left).toFloat(), (rect.bottom - rect.top).toFloat()),
                    CornerRadius(4.dp.toPx()))
                var x = if (ruby.request.vertical) rect.right - origin.x - padding else rect.left - origin.x + padding
                ruby.layouts.forEach { layout ->
                    if (ruby.request.vertical) x -= layout.size.width
                    drawText(layout, color = rubyColor, topLeft = Offset(x.toFloat(), top + padding))
                    if (ruby.request.vertical) x -= gap else x += layout.size.width + gap
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 240, showBackground = true)
@Composable
private fun CrowdedReadingPreview() {
    KanakoTheme {
        RecognizedTextOverlayContent(
            listOf(RecognizedTextLine("情報処理技術", Rect(80, 220, 380, 280), 1, ReadingBounds(0, 0, 1080, 720))),
            KanakoPreferences(), IntOffset.Zero
        )
    }
}

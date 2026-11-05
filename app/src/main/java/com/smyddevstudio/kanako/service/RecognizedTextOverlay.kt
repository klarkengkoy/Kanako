package com.smyddevstudio.kanako.service

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withTranslation
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.theme.CreamBackgroundDark
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ScreenReadingGroup(val reading: ReadingGroup, val clip: ReadingBounds)
private data class MeasuredScreenReading(val layout: TextLayoutResult, val padding: Int)

@Composable
internal fun RecognizedTextOverlayContent(
    lines: List<RecognizedTextLine>,
    preferences: KanakoPreferences,
    origin: IntOffset,
    modifier: Modifier = Modifier,
    tutorialContrast: Boolean = false,
    onReadingsVisibleChanged: (Boolean) -> Unit = {},
    onInteractionChanged: (ReadingOverlayInteraction) -> Unit = {},
    onInspectionChanged: (Boolean) -> Unit = {}
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tokenizer = remember { JapaneseTextTokenizer() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var tokenized by remember(lines) {
        mutableStateOf(lines.map { it.segments ?: emptyList<FuriganaSegment>() })
    }
    LaunchedEffect(lines) {
        if (lines.any { it.segments == null }) {
            tokenized = withContext(Dispatchers.Default) {
                lines.map { it.segments ?: tokenizer.tokenize(it.text) }
            }
        }
    }
    val viewport = ReadingBounds(origin.x, origin.y, origin.x + viewportSize.width, origin.y + viewportSize.height)
    val groups = remember(lines, tokenized, preferences.scriptType, viewport) {
        buildList {
            lines.forEachIndexed { index, line ->
                val clip = line.clip.intersection(viewport) ?: return@forEachIndexed
                val vertical = line.bounds.height() > line.bounds.width()
                var cursor = 0
                tokenized[index].forEach { segment ->
                    val start = line.text.indexOf(segment.text, cursor).takeIf { it >= 0 } ?: cursor
                    cursor = start + segment.text.length
                    val raw = segment.reading ?: return@forEach
                    if (segment.text.none { it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' || it == '々' }) return@forEach
                    val annotation = kanjiAnnotation(segment.text, tokenizer.formatReading(raw, ScriptType.KANA))
                    val reading = tokenizer.formatReading(annotation.reading, preferences.scriptType)
                    if (reading.isEmpty()) return@forEach
                    val anchor = readingTokenBounds(line.text,
                        ReadingBounds(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom),
                        line.spans, start + annotation.start, start + annotation.end)
                    if (anchor.intersection(clip) == null) return@forEach
                    add(ScreenReadingGroup(ReadingGroup(anchor, vertical, reading,
                        "${line.windowId}:$index:$start:${segment.text}",
                        segment.text.substring(annotation.start, annotation.end)), clip))
                }
            }
        }
    }
    val selectiveTouch = preferences.autoReadAfterScroll && !tutorialContrast
    var selected by remember(lines.map { it.windowId to it.text }, selectiveTouch) {
        mutableStateOf<String?>(null)
    }
    val focused = groups.firstOrNull { it.reading.key == selected }
    fun select(key: String?) {
        selected = key
        onInspectionChanged(key != null)
    }
    val padding = with(density) { 2.dp.roundToPx() }.coerceAtLeast(1)
    val gap = with(density) { 2.dp.roundToPx() }.coerceAtLeast(1)
    val minimumFont = with(density) { preferences.furiganaSize.sizeSp.sp.toPx() }
    val style = TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    val placements = remember(groups, lines, focused, minimumFont, measurer, density, padding, gap, style, viewport) {
        fun measure(index: Int, font: Float, callout: Boolean = false): GroupedRubyMeasurement<MeasuredScreenReading>? {
            val item = groups[index]
            val available = item.clip.right - item.clip.left - padding * 2
            if (available <= 0) return null
            val reading = item.reading
            val content = if (reading.vertical && !callout) reading.reading.toList().joinToString("\n") else reading.reading
            val textStyle = with(density) { style.copy(fontSize = font.toSp(), lineHeight = (font * 1.2f).toSp()) }
            val natural = measurer.measure(content, textStyle, softWrap = false)
            if (!callout && natural.size.width > available) return null
            val layout = if (callout) measurer.measure(content, textStyle,
                constraints = Constraints(maxWidth = available)) else natural
            return GroupedRubyMeasurement(layout.size.width + padding * 2, layout.size.height + padding * 2,
                gap, MeasuredScreenReading(layout, padding))
        }
        if (focused != null) {
            val index = groups.indexOf(focused)
            val reading = focused.reading
            val sourceSize = if (reading.vertical) reading.anchor.right - reading.anchor.left
                else reading.anchor.bottom - reading.anchor.top
            val natural = measure(index, maxOf(minimumFont, sourceSize * 0.5f), callout = true)
            val size = natural?.takeIf { it.height <= focused.clip.bottom - focused.clip.top }
                ?: measure(index, minimumFont, callout = true)
            val bounds = size?.let { placeReadingCallout(reading.anchor.intersection(focused.clip) ?: reading.anchor,
                focused.clip, it.width, it.height, gap, reading.vertical) }
            groups.indices.map { i ->
                if (i == index && bounds != null) GroupedRubyPlacement(bounds, minimumFont, size) else null
            }
        } else {
            placeGroupedReadings(groups.map { GroupedRubyRequest(it.reading.anchor, it.reading.vertical, it.clip) },
                viewport, lines.map { ReadingBounds(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
                minimumFont, measure = { index, font -> measure(index, font) })
        }
    }
    // Crowded source groups remain useful even when no automatic label fits.
    SideEffect { onReadingsVisibleChanged(groups.isNotEmpty()) }
    val currentGroups by rememberUpdatedState(groups)
    val currentPlacements by rememberUpdatedState(placements)
    val currentOrigin by rememberUpdatedState(origin)
    val selectAt: (Float, Float) -> Unit = { x, y ->
        val readings = currentGroups.map { it.reading.copy(anchor = it.reading.anchor.intersection(it.clip) ?: it.reading.anchor) }
        val hitsSource = readings.any { x >= it.anchor.left && x < it.anchor.right && y >= it.anchor.top && y < it.anchor.bottom }
        select(if (hitsSource) selectReadingGroup(readings, x, y, selected) else {
            val index = currentPlacements.indexOfLast { item -> item?.bounds?.let {
                x >= it.left && x < it.right && y >= it.top && y < it.bottom
            } == true }
            currentGroups.getOrNull(index)?.reading?.key?.takeUnless { it == selected }
        })
    }
    val currentSelectAt by rememberUpdatedState(selectAt)
    val interaction = Modifier.pointerInput(lines.map { it.windowId to it.text }, selectiveTouch) {
        detectTapGestures { point ->
            currentSelectAt(point.x + currentOrigin.x, point.y + currentOrigin.y)
        }
    }
    SideEffect {
        onInspectionChanged(focused != null)
        onInteractionChanged(ReadingOverlayInteraction(
            regions = groups.mapNotNull { it.reading.anchor.intersection(it.clip) } +
                if (focused != null) placements.mapNotNull { it?.bounds } else emptyList(),
            selective = selectiveTouch,
            selected = focused != null,
            onTap = selectAt,
            onDismiss = { select(null) }
        ))
    }
    val plateColor = if (tutorialContrast) MaterialTheme.colorScheme.surface else when (preferences.annotationPlateStyle) {
        AnnotationPlateStyle.CLEAR -> Color.Transparent
        AnnotationPlateStyle.WASHI_CAPSULE -> CreamBackgroundDark
        AnnotationPlateStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.inverseSurface
        AnnotationPlateStyle.SUBTLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rubyColor = if (tutorialContrast) MaterialTheme.colorScheme.onSurface
        else if (preferences.annotationPlateStyle == AnnotationPlateStyle.HIGH_CONTRAST)
        MaterialTheme.colorScheme.inverseOnSurface else Color(preferences.furiganaColor.colorHex)
    val selectedPlate = MaterialTheme.colorScheme.inverseSurface
    val selectedText = MaterialTheme.colorScheme.inverseOnSurface
    val selectLabel = stringResource(R.string.photo_reading_select)
    val clearLabel = stringResource(R.string.photo_reading_clear)
    val selectedDescription = focused?.let {
        stringResource(R.string.photo_reading_selected, it.reading.source, it.reading.reading)
    }.orEmpty()
    Canvas(modifier.fillMaxSize().onSizeChanged { viewportSize = it }.then(interaction).semantics {
        text = AnnotatedString(groups.mapIndexedNotNull { index, item ->
            if (placements[index] != null) item.reading.reading else null
        }.joinToString(", "))
        stateDescription = selectedDescription
        customActions = groups.map { item ->
            val reading = item.reading
            CustomAccessibilityAction("$selectLabel: ${reading.source} — ${reading.reading}") {
                select(reading.key.takeUnless { selected == it }); true
            }
        } + if (focused != null) listOf(CustomAccessibilityAction(clearLabel) { select(null); true }) else emptyList()
    }) {
        groups.forEach { item ->
            val clip = item.clip
            clipRect((clip.left - origin.x).toFloat(), (clip.top - origin.y).toFloat(),
                (clip.right - origin.x).toFloat(), (clip.bottom - origin.y).toFloat()) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.withTranslation(-origin.x.toFloat(), -origin.y.toFloat()) {
                        drawReadingHighlight(this, item.reading.anchor, item == focused, 1.dp.toPx())
                    }
                }
            }
        }
        placements.forEachIndexed { index, placement ->
            if (placement == null) return@forEachIndexed
            val item = groups[index]
            val rect = placement.bounds
            val clip = item.clip
            clipRect((clip.left - origin.x).toFloat(), (clip.top - origin.y).toFloat(),
                (clip.right - origin.x).toFloat(), (clip.bottom - origin.y).toFloat()) {
                val left = (rect.left - origin.x).toFloat()
                val top = (rect.top - origin.y).toFloat()
                drawRoundRect(if (focused != null) selectedPlate else plateColor, Offset(left, top),
                    Size((rect.right - rect.left).toFloat(), (rect.bottom - rect.top).toFloat()), CornerRadius(4.dp.toPx()))
                val measured = placement.measurement.content
                drawText(measured.layout, color = if (focused != null) selectedText else rubyColor,
                    topLeft = Offset(left + measured.padding, top + measured.padding))
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 240, showBackground = true)
@Composable
private fun CrowdedReadingPreview() {
    KanakoTheme {
        RecognizedTextOverlayContent(
            listOf(RecognizedTextLine("情報処理技術", Rect(80, 220, 380, 280), 1, ReadingBounds(0, 0, 1080, 720),
                segments = listOf(FuriganaSegment("情報処理技術", "じょうほうしょりぎじゅつ")))),
            KanakoPreferences(), IntOffset.Zero
        )
    }
}

@Preview(name = "Tutorial light", widthDp = 360, heightDp = 180, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Tutorial dark", widthDp = 360, heightDp = 180, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TutorialReadingContrastPreview() {
    KanakoTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            RecognizedTextOverlayContent(
                listOf(RecognizedTextLine("日本語", Rect(80, 220, 280, 280), 1, ReadingBounds(0, 0, 1080, 720),
                    segments = listOf(FuriganaSegment("日本語", "にほんご")))),
                KanakoPreferences(annotationPlateStyle = AnnotationPlateStyle.CLEAR), IntOffset.Zero,
                tutorialContrast = true
            )
        }
    }
}

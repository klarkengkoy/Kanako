package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import kotlin.math.roundToInt

@Composable
internal fun GuidedSpotlight(
    target: Rect?,
    message: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val outline = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val hole = target?.intersect(Rect(0f, 0f, width, height))
            ?.takeUnless { it.isEmpty }
        val shade = Color.Black.copy(alpha = 0.65f)
        fun blocker(rect: Rect): Modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .background(shade).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        if (hole == null) Box(blocker(Rect(0f, 0f, width, height))) else {
            Box(blocker(Rect(0f, 0f, width, hole.top)))
            Box(blocker(Rect(0f, hole.bottom, width, height)))
            Box(blocker(Rect(0f, hole.top, hole.left, hole.bottom)))
            Box(blocker(Rect(hole.right, hole.top, width, hole.bottom)))
            Canvas(Modifier.fillMaxSize()) {
                drawRect(outline, hole.topLeft, hole.size, style = Stroke(3.dp.toPx()))
            }
        }
        val above = hole != null && hole.top > height - hole.bottom
        val insets = WindowInsets.safeDrawing
        val freeHeight = if (hole == null) height else if (above) hole.top else height - hole.bottom
        val inset = if (above) insets.getTop(density) else insets.getBottom(density)
        val cardHeight = with(density) { (freeHeight - inset).toDp() - 32.dp }
            .coerceIn(64.dp, 240.dp)
        Card(Modifier.align(if (above) Alignment.TopCenter else Alignment.BottomCenter)
            .windowInsetsPadding(insets).padding(16.dp).widthIn(max = 480.dp)
            .heightIn(max = cardHeight)) {
            Column(Modifier.padding(8.dp)) {
                Text(message, modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onSkip) { Text(stringResource(R.string.guided_skip)) }
            }
        }
    }
}

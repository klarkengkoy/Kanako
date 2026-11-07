package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.roundToInt

internal class TutorialFocus(
    step: Int,
    allowed: Set<String>,
    actionLabel: Int? = null
) {
    var step by mutableIntStateOf(step)
    var allowed by mutableStateOf(allowed)
    var actionLabel by mutableStateOf(actionLabel)
    val bounds = mutableStateMapOf<String, Rect>()
    val owners = mutableMapOf<String, Any>()
    val clips = mutableStateMapOf<String, String>()
    var message by mutableStateOf<Int?>(null)
    var heading by mutableStateOf<Int?>(null)
}

internal val LocalTutorialFocus = staticCompositionLocalOf<TutorialFocus?> { null }

@Composable
internal fun tutorialAllows(vararg keys: String): Boolean {
    val focus = LocalTutorialFocus.current ?: return true
    return keys.any { it in focus.allowed }
}

internal fun Modifier.tutorialTarget(key: String, hideWhenDimmed: Boolean = true, parent: String? = null, clipTo: String? = null): Modifier = composed {
    val focus = LocalTutorialFocus.current ?: return@composed this
    val owner = remember(focus, key) { Any() }
    DisposableEffect(focus, key, owner) {
        onDispose {
            if (focus.owners[key] === owner) {
                focus.owners.remove(key)
                focus.bounds.remove(key)
                focus.clips.remove(key)
            }
        }
    }
    fun recordBounds(bounds: Rect) {
        focus.owners[key] = owner
        focus.bounds[key] = bounds
        if (clipTo != null) focus.clips[key] = clipTo else focus.clips.remove(key)
    }
    val tracking = if (focus.step == 3 || focus.step == 4) {
        Modifier.onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) {
            val bounds = it.boundsInWindow
            recordBounds(Rect(bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat()))
        }
    } else {
        // Retain ancestor clipping for Home, camera controls, and the floating sample.
        Modifier.onGloballyPositioned { recordBounds(it.boundsInWindow()) }
    }
    then(tracking)
        .then(if (hideWhenDimmed && key !in focus.allowed && parent !in focus.allowed) Modifier.clearAndSetSemantics {} else Modifier)
}

internal fun subtractTutorialTarget(area: Rect, target: Rect): List<Rect> {
    val cut = area.intersect(target)
    if (cut.isEmpty) return listOf(area)
    return listOf(
        Rect(area.left, area.top, area.right, cut.top),
        Rect(area.left, cut.bottom, area.right, area.bottom),
        Rect(area.left, cut.top, cut.left, cut.bottom),
        Rect(cut.right, cut.top, area.right, cut.bottom)
    ).filterNot { it.isEmpty }
}

@Composable
internal fun TutorialSpotlight(focus: TutorialFocus, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val outline = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    // Preferences disables controls at their source, leaving its list free to handle drag gestures.
    val scrollThrough = focus.step == 3 || focus.step == 4
    BoxWithConstraints(modifier.fillMaxSize().onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) {
        origin = Offset(it.positionInWindow.x.toFloat(), it.positionInWindow.y.toFloat())
    }) {
        fun targets(viewport: Rect): List<Rect> = focus.allowed.mapNotNull { key ->
            val bounds = focus.bounds[key] ?: return@mapNotNull null
            val clip = focus.clips[key]?.let { focus.bounds[it] ?: return@mapNotNull null }
            (if (clip == null) bounds else bounds.intersect(clip))
                .translate(-origin).intersect(viewport).takeUnless { it.isEmpty }
        }
        fun blocked(viewport: Rect, targets: List<Rect>) = targets.fold(listOf(viewport)) { areas, target ->
            areas.flatMap { subtractTutorialTarget(it, target) }
        }
        if (!scrollThrough) {
            val viewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            blocked(viewport, targets(viewport)).forEach { area ->
                Box(Modifier.offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                    .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() })
                    .clearAndSetSemantics {}
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    })
            }
        }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val viewport = Rect(Offset.Zero, size)
            val currentTargets = targets(viewport)
            blocked(viewport, currentTargets).forEach {
                drawRect(Color.Black.copy(alpha = 0.65f), it.topLeft, it.size)
            }
            currentTargets.forEach { drawRect(outline, it.topLeft, it.size, style = Stroke(2.dp.toPx())) }
        }
    }
}

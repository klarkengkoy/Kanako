package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

@Composable
internal fun Modifier.cameraReaderGestures(
    onZoom: (Float) -> Unit,
    onFocus: (Offset?) -> Unit,
    onInteraction: () -> Unit
): Modifier {
    val zoom by rememberUpdatedState(onZoom)
    val focus by rememberUpdatedState(onFocus)
    val interaction by rememberUpdatedState(onInteraction)
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    interaction()
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            }
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, _, factor, _ -> zoom(1 + (factor - 1) * 2) }
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = { focus(it) })
        }
        .semantics {
            onClick {
                interaction()
                focus(null)
                true
            }
        }
}

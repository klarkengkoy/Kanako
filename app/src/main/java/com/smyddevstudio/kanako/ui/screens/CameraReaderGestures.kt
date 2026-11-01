package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

@Composable
internal fun Modifier.cameraReaderGestures(
    onZoom: (Float) -> Unit,
    onFocus: (Offset?) -> Unit,
    onInteraction: () -> Unit,
    onSelect: (Offset) -> Boolean = { false }
): Modifier {
    val zoom by rememberUpdatedState(onZoom)
    val focus by rememberUpdatedState(onFocus)
    val interaction by rememberUpdatedState(onInteraction)
    val select by rememberUpdatedState(onSelect)
    return this
        .pointerInput(Unit) {
            detectTransformGestures { _, _, factor, _ ->
                interaction()
                zoom(1 + (factor - 1) * 2)
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                if (!select(it)) {
                    interaction()
                    focus(it)
                }
            })
        }
        .semantics {
            onClick {
                interaction()
                focus(null)
                true
            }
        }
}

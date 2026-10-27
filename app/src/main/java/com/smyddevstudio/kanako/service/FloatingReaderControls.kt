package com.smyddevstudio.kanako.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Translate
import android.os.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.smyddevstudio.kanako.ui.components.labelRes
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Composable
fun FloatingOverlayContent(
    preferences: KanakoPreferences,
    showFurigana: Boolean,
    onToggleFurigana: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleScriptType: () -> Unit,
    onOpenApp: () -> Unit,
    onStopService: () -> Unit,
    modifier: Modifier = Modifier,
    onReadOrClear: () -> Unit = {},
    mirrorMascot: Boolean = false,
    onControlSizeChanged: (Int) -> Unit = {},
    onLayoutSizeChanged: (IntSize) -> Unit = {},
    controlOffset: () -> IntOffset = { IntOffset.Zero },
    menuAbove: () -> Boolean = { false },
    isReading: Boolean = false,
    statusMessage: Int = R.string.reading_ready
) {
    var isExpanded by remember { mutableStateOf(false) }
    val (fabSize, _, _, _) = when (preferences.fabSize) {
        FabSize.COMPACT -> listOf(48.dp, 28.dp, 8.dp, 10.dp)
        FabSize.MEDIUM -> listOf(56.dp, 36.dp, 10.dp, 12.dp)
        FabSize.LARGE -> listOf(68.dp, 44.dp, 12.dp, 14.dp)
    }

    Layout(
        modifier = modifier.onSizeChanged(onLayoutSizeChanged),
        content = {
            val controlDescription = stringResource(R.string.floating_control_description)
            val statusDescription = stringResource(statusMessage)
            Box(
                modifier = Modifier
                    .size(fabSize)
                    .onSizeChanged { onControlSizeChanged(it.width) }
                    .semantics {
                        contentDescription = controlDescription
                        stateDescription = statusDescription
                    }
                    .combinedClickable(
                        onClick = onReadOrClear,
                        onLongClick = { isExpanded = !isExpanded },
                        onDoubleClick = { isExpanded = !isExpanded }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isExpanded = false },
                            onDragEnd = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val mascotModifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = if (mirrorMascot) -1f else 1f
                }
                if (isReading && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !LocalInspectionMode.current) {
                    LoadingMascot(size = fabSize, modifier = mascotModifier)
                } else {
                    Image(
                        painter = painterResource(R.drawable.kanako_floating),
                        contentDescription = null,
                        modifier = mascotModifier
                    )
                }
                when {
                    isReading -> Unit
                    statusMessage != R.string.reading_ready && statusMessage != R.string.reading_shown ->
                        Icon(
                            Icons.Rounded.ErrorOutline, contentDescription = null,
                            modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    showFurigana -> Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape))
                }
            }

            // Expanded Quick Palette Menu
            Box {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(180.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.controls_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = statusDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Toggle Furigana Display ON/OFF
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = if (showFurigana) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onToggleFurigana
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Translate,
                                        contentDescription = null,
                                        tint = if (showFurigana) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.clear_annotations),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (showFurigana) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Cycle Script Type
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onToggleScriptType
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.controls_script, stringResource(preferences.scriptType.labelRes)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Open App
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onOpenApp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.controls_open),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Stop Overlay Service
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = onStopService
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.controls_stop),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val control = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val menu = measurables[1].measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxHeight = (constraints.maxHeight - control.height - 48.dp.roundToPx()).coerceAtLeast(0)
            )
        )
        val width = maxOf(control.width, menu.width)
        val height = control.height + menu.height
        layout(width, height) {
            val offset = controlOffset()
            control.place(offset.x.coerceIn(0, width - control.width),
                offset.y.coerceIn(0, height - control.height))
            menu.place(0, if (menuAbove()) 0 else control.height)
        }
    }
}

@Preview
@Composable
fun FloatingOverlayContentPreview() {
    KanakoTheme {
        Box(
            modifier = Modifier
                .background(Color.Gray)
                .padding(20.dp)
        ) {
            FloatingOverlayContent(
                preferences = KanakoPreferences(),
                showFurigana = true,
                onToggleFurigana = {},
                onDrag = { _, _ -> },
                onDragEnd = {},
                onToggleScriptType = {},
                onOpenApp = {},
                onStopService = {}
            )
        }
    }
}

@Preview
@Composable
private fun FloatingReaderWorkingPreview() {
    KanakoTheme {
        FloatingOverlayContent(
            preferences = KanakoPreferences(),
            showFurigana = false, onToggleFurigana = {}, onDrag = { _, _ -> },
            onDragEnd = {}, onToggleScriptType = {}, onOpenApp = {}, onStopService = {},
            isReading = true, statusMessage = R.string.reading_working
        )
    }
}

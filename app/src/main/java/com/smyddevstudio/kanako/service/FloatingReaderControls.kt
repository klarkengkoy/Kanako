package com.smyddevstudio.kanako.service

import android.graphics.Rect
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.labelRes
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlinx.coroutines.delay

@Composable
fun FloatingOverlayContent(
    preferences: KanakoPreferences,
    showFurigana: Boolean,
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
    isReading: Boolean = false,
    statusMessage: Int = R.string.reading_ready,
    controlsVisible: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    val accessibilityManager = LocalAccessibilityManager.current
    fun dismiss() {
        isExpanded = false
        showStatus = false
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) dismiss()
    }
    LaunchedEffect(statusMessage, isReading) {
        showStatus = !isReading && statusMessage !in listOf(
            R.string.reading_ready, R.string.reading_shown, R.string.reading_working
        )
        if (showStatus) {
            delay(accessibilityManager?.calculateRecommendedTimeoutMillis(
                6_000L, containsText = true, containsControls = true
            ) ?: 6_000L)
            showStatus = false
        }
    }
    val fabSize = when (preferences.fabSize) {
        FabSize.COMPACT -> 48.dp
        FabSize.MEDIUM -> 56.dp
        FabSize.LARGE -> 68.dp
    }
    val controlDescription = stringResource(R.string.floating_control_description)
    val statusDescription = stringResource(statusMessage)
    val popupPosition = rememberFloatingMenuPosition()
    val menuMaxHeight = maxOf(48.dp, LocalConfiguration.current.screenHeightDp.dp - 96.dp)

    Box(
        modifier = modifier.size(fabSize).onSizeChanged {
            onControlSizeChanged(it.width)
            onLayoutSizeChanged(it)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .systemGestureExclusion()
                .semantics {
                    contentDescription = controlDescription
                    stateDescription = statusDescription
                }
                .combinedClickable(
                    onClick = { dismiss(); onReadOrClear() },
                    onLongClick = { showStatus = false; isExpanded = !isExpanded },
                    onDoubleClick = { showStatus = false; isExpanded = !isExpanded }
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dismiss() },
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
                Image(painterResource(R.drawable.kanako_floating), contentDescription = null,
                    modifier = mascotModifier)
            }
            val dotColor = when {
                isReading || statusMessage !in listOf(R.string.reading_ready, R.string.reading_shown) ->
                    MaterialTheme.colorScheme.error
                showFurigana -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outline
            }
            val dotAlpha = if (isReading) {
                val pulse = rememberInfiniteTransition(label = "Reading status")
                val alpha by pulse.animateFloat(
                    initialValue = 0.35f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "Reading status opacity"
                )
                alpha
            } else 1f
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(12.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)
                .graphicsLayer { alpha = dotAlpha }
                .background(dotColor, CircleShape))
        }
        if (controlsVisible && (isExpanded || showStatus)) {
            Popup(
                popupPositionProvider = popupPosition,
                onDismissRequest = ::dismiss,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    excludeFromSystemGesture = false
                )
            ) {
                Surface(
                    modifier = Modifier.width(200.dp).heightIn(max = menuMaxHeight),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isExpanded) {
                            Text(stringResource(R.string.controls_title), fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(statusDescription, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FloatingMenuAction(
                                stringResource(R.string.controls_script, stringResource(preferences.scriptType.labelRes)),
                                Icons.Rounded.Language, onToggleScriptType
                            )
                            FloatingMenuAction(stringResource(R.string.controls_open),
                                Icons.AutoMirrored.Rounded.OpenInNew, { dismiss(); onOpenApp() })
                            FloatingMenuAction(stringResource(R.string.controls_stop),
                                Icons.Rounded.PowerSettingsNew, { dismiss(); onStopService() },
                                emphasized = true)
                        } else {
                            Text(statusDescription,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { isExpanded = true; showStatus = false },
                                modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.controls_title))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMenuAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(label, Modifier.weight(1f), fontSize = 12.sp,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun rememberFloatingMenuPosition(): PopupPositionProvider {
    val host = LocalView.current
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    return remember(host, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize
            ): IntOffset {
                val screen = IntArray(2)
                val window = IntArray(2)
                host.getLocationOnScreen(screen)
                host.getLocationInWindow(window)
                val origin = IntOffset(screen[0] - window[0], screen[1] - window[1])
                val visible = Rect()
                host.getWindowVisibleDisplayFrame(visible)
                val area = if (visible.isEmpty) ReadingBounds(0, 0, windowSize.width, windowSize.height)
                    else ReadingBounds(visible.left + gap, visible.top + gap,
                        maxOf(visible.left + gap, visible.right - gap),
                        maxOf(visible.top + gap, visible.bottom - gap))
                val anchor = ReadingBounds(anchorBounds.left + origin.x, anchorBounds.top + origin.y,
                    anchorBounds.right + origin.x, anchorBounds.bottom + origin.y)
                val position = floatingMenuPosition(area, anchor,
                    popupContentSize.width, popupContentSize.height, gap)
                return IntOffset(position.x - origin.x, position.y - origin.y)
            }
        }
    }
}

@Preview
@Composable
fun FloatingOverlayContentPreview() {
    KanakoTheme {
        Box(Modifier.background(Color.Gray).padding(20.dp)) {
            FloatingOverlayContent(
                preferences = KanakoPreferences(), showFurigana = true,
                onDrag = { _, _ -> }, onDragEnd = {}, onToggleScriptType = {},
                onOpenApp = {}, onStopService = {}
            )
        }
    }
}

@Preview
@Composable
private fun FloatingReaderWorkingPreview() {
    KanakoTheme {
        FloatingOverlayContent(
            preferences = KanakoPreferences(), showFurigana = false,
            onDrag = { _, _ -> }, onDragEnd = {}, onToggleScriptType = {},
            onOpenApp = {}, onStopService = {},
            isReading = true, statusMessage = R.string.reading_working
        )
    }
}

package com.smyddevstudio.kanako.ui.screens

import android.view.Surface as DisplaySurface
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Composable
internal fun CameraReaderContent(
    state: CameraReaderUiState,
    granted: Boolean,
    onBack: () -> Unit,
    onPreferences: () -> Unit,
    onCapture: () -> Unit,
    onPermission: () -> Unit,
    onRetry: () -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    permissionLabel: Int = R.string.camera_allow,
    displayRotation: Int = DisplaySurface.ROTATION_0,
    viewfinder: @Composable (Modifier) -> Unit = { Box(it.background(MaterialTheme.colorScheme.surfaceVariant)) }
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        val landscape = maxWidth > maxHeight
        val compact = landscape || maxHeight < 400.dp || density.fontScale > 1.3f
        val controlsOnRight = displayRotation != DisplaySurface.ROTATION_270 &&
            displayRotation != DisplaySurface.ROTATION_180
        val insets = WindowInsets.safeDrawing
        val direction = LocalLayoutDirection.current
        val edgePadding = maxOf(48.dp, with(density) {
            maxOf(insets.getLeft(this, direction), insets.getTop(this),
                insets.getRight(this, direction), insets.getBottom(this)).toDp()
        } + 16.dp)
        val controlsGutter = if (granted && landscape) 72.dp + edgePadding else 0.dp
        val controlRotation = rememberCameraControlRotation(displayRotation)
        if (granted) viewfinder(Modifier.fillMaxSize())
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background).align(Alignment.TopCenter))
        Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background).align(Alignment.BottomCenter))
        LookaheadScope {
            val motion = Modifier.animateBounds(this, boundsTransform = { _, _ -> tween(250) })
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)
                .absolutePadding(left = if (controlsOnRight) 0.dp else controlsGutter,
                    right = if (controlsOnRight) controlsGutter else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CameraReaderTopBar(onBack, onPreferences, modifier = if (granted) motion else Modifier, appBar = true)
                if (!granted) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        Surface(modifier = Modifier.widthIn(max = 560.dp),
                            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(R.string.camera_state_permission), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.camera_state_permission_body))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(onClick = onPermission, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(permissionLabel))
                                    }
                                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.camera_not_now))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compact) 0.dp else TopAppBarDefaults.TopAppBarExpandedHeight))
                } else {
                    Spacer(Modifier.weight(1f))
                    Surface(modifier = motion, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.status == CameraReadingStatus.STARTING || state.status == CameraReadingStatus.CAPTURING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(cameraStatusString(state.status)), style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.status == CameraReadingStatus.CAMERA_ERROR || state.status == CameraReadingStatus.READ_ERROR) {
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.camera_retry)) }
                    }
                    Spacer(Modifier.height(if (landscape) 8.dp else 84.dp + edgePadding))
                }
            }
        }
        if (granted) {
            CameraCaptureControls(state, onCapture, onZoom, controlRotation,
                modifier = Modifier.fillMaxSize(), displayRotation = displayRotation,
                edgePadding = edgePadding)
            if (!compact) Box(Modifier.fillMaxSize().safeDrawingPadding(),
                contentAlignment = Alignment.BottomCenter) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.camera_live_hint), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureControls(
    state: CameraReaderUiState,
    onCapture: () -> Unit,
    onZoom: (Float) -> Unit,
    iconRotation: State<Float>,
    modifier: Modifier = Modifier,
    displayRotation: Int = DisplaySurface.ROTATION_0,
    edgePadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    Layout(modifier = modifier, content = {
        for (control in listOf(-1, 0, 1)) {
            if (control == 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    IconButton(onClick = onCapture,
                        enabled = state.ready && state.status != CameraReadingStatus.CAPTURING,
                        modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.CameraAlt, stringResource(R.string.camera_capture),
                            Modifier.size(32.dp).graphicsLayer { rotationZ = iconRotation.value },
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface) {
                    IconButton(onClick = { onZoom(if (control > 0) 1.5f else 1 / 1.5f) },
                        enabled = state.ready, modifier = Modifier.size(48.dp)) {
                        Icon(if (control > 0) Icons.Rounded.ZoomIn else Icons.Rounded.ZoomOut,
                            stringResource(if (control > 0) R.string.camera_zoom_in else R.string.camera_zoom_out),
                            Modifier.graphicsLayer { rotationZ = iconRotation.value })
                    }
                }
            }
        }
    }) { measurables, constraints ->
        val buttons = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val naturalLandscape = (width > height) == (displayRotation % 2 == 0)
        val rotation = (displayRotation + if (naturalLandscape) 1 else 0) % 4
        val edge = edgePadding.toPx() + 36.dp.toPx()
        val spacing = 84.dp.toPx()
        layout(width, height) {
            buttons.forEachIndexed { index, button ->
                val offset = (index - 1) * spacing
                val (x, y) = when (rotation) {
                    1 -> width - edge to height / 2f - offset
                    2 -> width / 2f - offset to edge
                    3 -> edge to height / 2f + offset
                    else -> width / 2f + offset to height - edge
                }
                button.place((x - button.width / 2f).roundToInt(), (y - button.height / 2f).roundToInt())
            }
        }
    }
}

@Composable
internal fun rememberCameraControlRotation(displayRotation: Int): State<Float> {
    val angle = remember { Animatable(0f) }
    var previousRotation by remember { mutableIntStateOf(displayRotation) }
    LaunchedEffect(displayRotation) {
        val delta = ((displayRotation - previousRotation) * 90 + 540) % 360 - 180
        previousRotation = displayRotation
        angle.snapTo(angle.value - delta)
        angle.animateTo(0f, tween(250))
    }
    return angle.asState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraReaderTopBar(
    onBack: () -> Unit,
    onPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    title: Int = R.string.camera_title,
    appBar: Boolean = false
) {
    if (appBar) {
        TopAppBar(
            title = { Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                }
            },
            actions = {
                IconButton(onClick = onPreferences) {
                    Icon(Icons.Rounded.Settings, stringResource(R.string.camera_appearance))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    } else {
        Surface(modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                }
                Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onPreferences) {
                    Icon(Icons.Rounded.Settings, stringResource(R.string.camera_appearance))
                }
            }
        }
    }
}

internal fun cameraStatusString(status: CameraReadingStatus): Int = when (status) {
    CameraReadingStatus.STARTING -> R.string.camera_starting
    CameraReadingStatus.LIVE -> R.string.camera_live
    CameraReadingStatus.NO_TEXT -> R.string.camera_state_empty
    CameraReadingStatus.MODEL_NEEDED -> R.string.reading_model_missing
    CameraReadingStatus.CAMERA_ERROR -> R.string.camera_unavailable
    CameraReadingStatus.CAPTURING -> R.string.camera_capturing
    CameraReadingStatus.PROCESSING -> R.string.camera_state_processing
    CameraReadingStatus.PHOTO -> R.string.camera_photo_ready
    CameraReadingStatus.READ_ERROR -> R.string.camera_state_error
}

@Preview(showBackground = true)
@Preview(name = "Landscape", widthDp = 720, heightDp = 360)
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CameraReaderPreview() {
    KanakoTheme {
        CameraReaderContent(CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true), true, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Reverse landscape", widthDp = 720, heightDp = 360)
@Composable
private fun CameraReaderReverseLandscapePreview() {
    KanakoTheme {
        CameraReaderContent(CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true),
            true, {}, {}, {}, {}, {}, {}, displayRotation = DisplaySurface.ROTATION_270)
    }
}

@Preview(showBackground = true)
@Preview(name = "Compact landscape", widthDp = 640, heightDp = 360)
@Preview(name = "Large text", widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun CameraPermissionPreview() {
    KanakoTheme { CameraReaderContent(CameraReaderUiState(), false, {}, {}, {}, {}, {}, {}) }
}

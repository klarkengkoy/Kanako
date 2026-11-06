package com.smyddevstudio.kanako.ui.screens

import android.graphics.Bitmap
import android.view.Surface as DisplaySurface
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
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
    onGallery: () -> Unit = {},
    captureThumbnail: Bitmap? = null,
    savedThumbnail: @Composable () -> Unit = { Icon(Icons.Rounded.Image, null, tint = Color.White) },
    guideTarget: Int = -1,
    onGuideBounds: (Rect) -> Unit = {},
    permissionLabel: Int = R.string.camera_allow,
    displayRotation: Int = DisplaySurface.ROTATION_0,
    viewfinder: @Composable (Modifier) -> Unit = { Box(it.background(MaterialTheme.colorScheme.surfaceVariant)) }
) {
    val zoomInLabel = stringResource(R.string.camera_zoom_in)
    val zoomOutLabel = stringResource(R.string.camera_zoom_out)
    val statusDescription = stringResource(cameraStatusString(state.status))
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
        val controlsGutter = if (granted && landscape) 88.dp + edgePadding else 0.dp
        val controlRotation = rememberCameraControlRotation(displayRotation)
        if (granted) viewfinder(Modifier.fillMaxSize().semantics {
            stateDescription = statusDescription
            customActions = if (state.ready && (guideTarget < 0 || guideTarget == 2)) listOf(
                CustomAccessibilityAction(zoomInLabel) { onZoom(1.5f); true },
                CustomAccessibilityAction(zoomOutLabel) { onZoom(1 / 1.5f); true }
            ) else emptyList()
        }.then(if (guideTarget == 1 || guideTarget == 2) Modifier.onGloballyPositioned {
            val bounds = it.boundsInRoot()
            onGuideBounds(Rect(bounds.left + bounds.width * 0.1f, bounds.top + bounds.height * 0.4f,
                bounds.right - bounds.width * 0.1f, bounds.bottom - bounds.height * 0.4f))
        } else Modifier))
        if (granted) {
            Spacer(Modifier.fillMaxWidth().height(with(density) { insets.getTop(this).toDp() } + 96.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
            Spacer(Modifier.fillMaxWidth().height(edgePadding + 176.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        }
        LookaheadScope {
            val motion = Modifier.animateBounds(this, boundsTransform = { _, _ -> tween(250) })
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)
                .absolutePadding(left = if (controlsOnRight) 0.dp else controlsGutter,
                    right = if (controlsOnRight) controlsGutter else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (granted) {
                    Row(modifier = motion.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onBack, enabled = guideTarget < 0, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back), tint = Color.White)
                        }
                        IconButton(onClick = onPreferences, enabled = guideTarget < 0, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Settings, stringResource(R.string.camera_appearance), tint = Color.White)
                        }
                    }
                } else {
                    CameraReaderTopBar(onBack, onPreferences, appBar = true)
                }
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
                                    Button(onClick = onPermission, modifier = Modifier.heightIn(min = 48.dp).then(
                                        if (guideTarget == 0) Modifier.onGloballyPositioned { onGuideBounds(it.boundsInRoot()) } else Modifier)) {
                                        Text(stringResource(permissionLabel))
                                    }
                                    TextButton(onClick = onBack, enabled = guideTarget < 0, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text(stringResource(R.string.camera_not_now))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compact) 0.dp else TopAppBarDefaults.TopAppBarExpandedHeight))
                } else {
                    Spacer(Modifier.weight(1f))
                    if (state.status == CameraReadingStatus.CAMERA_ERROR || state.status == CameraReadingStatus.READ_ERROR ||
                        state.status == CameraReadingStatus.MODEL_NEEDED) {
                        Surface(modifier = motion, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), contentColor = MaterialTheme.colorScheme.onSurface) {
                            Text(statusDescription, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.status == CameraReadingStatus.CAMERA_ERROR || state.status == CameraReadingStatus.READ_ERROR) {
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp).then(
                            if (guideTarget == 4) Modifier.onGloballyPositioned { onGuideBounds(it.boundsInRoot()) } else Modifier)) { Text(stringResource(R.string.camera_retry)) }
                    }
                    Spacer(Modifier.height(if (landscape) 8.dp else 104.dp + edgePadding))
                }
            }
        }
        if (granted) {
            CameraCaptureControls(state, onCapture, onGallery, controlRotation,
                modifier = Modifier.fillMaxSize(), displayRotation = displayRotation,
                edgePadding = edgePadding, guideTarget = guideTarget, onGuideBounds = onGuideBounds,
                thumbnail = {
                    if (captureThumbnail != null) Image(captureThumbnail.asImageBitmap(), null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else savedThumbnail()
                })
        }
    }
}

@Composable
private fun CameraCaptureControls(
    state: CameraReaderUiState,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
    iconRotation: State<Float>,
    modifier: Modifier = Modifier,
    displayRotation: Int = DisplaySurface.ROTATION_0,
    edgePadding: androidx.compose.ui.unit.Dp = 16.dp,
    guideTarget: Int = -1,
    onGuideBounds: (Rect) -> Unit = {},
    thumbnail: @Composable () -> Unit = {}
) {
    val galleryLabel = stringResource(R.string.gallery_title)
    Layout(modifier = modifier, content = {
        IconButton(onClick = onGallery, enabled = guideTarget < 0,
            modifier = Modifier.size(52.dp).semantics { contentDescription = galleryLabel }.clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = iconRotation.value },
                contentAlignment = Alignment.Center) {
                thumbnail()
            }
        }
        val enabled = state.ready && state.status != CameraReadingStatus.CAPTURING &&
            (guideTarget < 0 || guideTarget == 3)
        IconButton(onClick = onCapture, enabled = enabled,
            modifier = Modifier.size(88.dp).border(2.dp, Color.White.copy(alpha = if (enabled) 0.9f else 0.4f), CircleShape)
                .then(if (guideTarget == 3) Modifier.onGloballyPositioned { onGuideBounds(it.boundsInRoot()) } else Modifier)) {
            Box(Modifier.padding(7.dp).fillMaxSize().background(
                Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DocumentScanner, stringResource(R.string.camera_capture),
                    Modifier.size(32.dp).graphicsLayer { rotationZ = iconRotation.value }, tint = Color.Black)
            }
        }
    }) { measurables, constraints ->
        val buttons = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val naturalLandscape = (width > height) == (displayRotation % 2 == 0)
        val rotation = (displayRotation + if (naturalLandscape) 1 else 0) % 4
        val edge = edgePadding.toPx() + 44.dp.toPx()
        val spacing = 100.dp.toPx()
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

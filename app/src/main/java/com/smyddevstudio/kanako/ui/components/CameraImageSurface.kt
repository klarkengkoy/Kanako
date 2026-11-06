package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.camera.CameraPhotoReading
import com.smyddevstudio.kanako.camera.cameraPhotoReadings
import com.smyddevstudio.kanako.camera.layoutCameraPhotoReadings
import com.smyddevstudio.kanako.camera.selectCameraPhotoReading
import com.smyddevstudio.kanako.camera.drawCameraPhotoHighlights
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.withClip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.service.ReadingTarget
import com.smyddevstudio.kanako.camera.CameraAnnotationColors
import com.smyddevstudio.kanako.camera.CameraAnnotationLayout
import com.smyddevstudio.kanako.camera.CameraPhoto
import com.smyddevstudio.kanako.camera.CameraPhotoGesture
import com.smyddevstudio.kanako.camera.CameraReadingLine
import com.smyddevstudio.kanako.camera.drawCameraAnnotations
import com.smyddevstudio.kanako.camera.fitCameraImage
import com.smyddevstudio.kanako.camera.layoutCameraAnnotations
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.CreamBackgroundDark
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
private fun annotationColors(preferences: KanakoPreferences): CameraAnnotationColors {
    val plate = when (preferences.annotationPlateStyle) {
        AnnotationPlateStyle.CLEAR -> Color.Transparent
        AnnotationPlateStyle.WASHI_CAPSULE -> CreamBackgroundDark
        AnnotationPlateStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.inverseSurface
        AnnotationPlateStyle.SUBTLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val text = if (preferences.annotationPlateStyle == AnnotationPlateStyle.HIGH_CONTRAST)
        MaterialTheme.colorScheme.inverseOnSurface else Color(preferences.furiganaColor.colorHex)
    return CameraAnnotationColors(text.toArgb(), plate.toArgb())
}

@Composable
internal fun CameraReadingOverlay(
    lines: List<CameraReadingLine>,
    preferences: KanakoPreferences,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onTargets: (List<ReadingTarget>) -> Unit = {},
    onSelectReading: (String) -> Unit = {}
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pixels = with(LocalDensity.current) { preferences.furiganaSize.sizeSp.sp.toPx() }
    val layout = remember(lines, size, pixels, preferences.scriptType) {
        layoutCameraAnnotations(lines, size.width, size.height, pixels, preferences.scriptType)
    }
    val colors = annotationColors(preferences)
    SideEffect { onTargets(layout.labels.map { it.target }) }
    val selectLabel = stringResource(R.string.reading_bring_front)
    Canvas(modifier.onSizeChanged { size = it }.semantics {
        text = AnnotatedString(layout.labels.joinToString(", ") { it.reading })
        customActions = layout.labels.map { ruby ->
            CustomAccessibilityAction("$selectLabel: ${ruby.reading}") { onSelectReading(ruby.target.key); true }
        }
    }) {
        drawIntoCanvas { drawCameraAnnotations(it.nativeCanvas, layout, colors, selected) }
    }
}

@Composable
internal fun CameraPhotoSurface(
    photo: CameraPhoto,
    lines: List<CameraReadingLine>,
    preferences: KanakoPreferences,
    modifier: Modifier = Modifier,
    showReadings: Boolean = true,
    topOverlayHeight: Int = 0
) {
    val lease = remember(photo) { photo.acquire() }
    DisposableEffect(lease) { onDispose { lease?.close() } }
    if (lease == null) return
    val bitmap = lease.bitmap
    var size by remember { mutableStateOf(IntSize.Zero) }
    var gesture by remember(photo, size) { mutableStateOf(CameraPhotoGesture()) }
    var selected by rememberSaveable(photo, lines.map { it.text }) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val fit = fitCameraImage(photo.width, photo.height, size.width, size.height)
    val scale = (fit?.scale ?: 1f) * gesture.zoom
    val left = (size.width - photo.width * scale) / 2f + gesture.x
    val top = (size.height - photo.height * scale) / 2f + gesture.y
    val fontPixels = with(density) { preferences.furiganaSize.sizeSp.sp.toPx() } / scale
    val visibleClip = ReadingBounds(
        kotlin.math.ceil((insets.getLeft(density, direction) - left) / scale).toInt().coerceIn(0, photo.width),
        kotlin.math.ceil((maxOf(topOverlayHeight, insets.getTop(density)) - top) / scale).toInt().coerceIn(0, photo.height),
        kotlin.math.floor((size.width - insets.getRight(density, direction) - left) / scale).toInt().coerceIn(0, photo.width),
        kotlin.math.floor((size.height - insets.getBottom(density) - top) / scale).toInt().coerceIn(0, photo.height)
    )
    val sourceKey = remember(lines, photo, preferences.scriptType) { Any() }
    val sources by produceState<Pair<Any, List<CameraPhotoReading>>?>(null, sourceKey) {
        val computed = withContext(Dispatchers.Default) {
            val operation = currentCoroutineContext()
            cameraPhotoReadings(lines, photo.width, photo.height, preferences.scriptType,
                checkCancellation = { operation.ensureActive() })
        }
        value = sourceKey to computed
    }
    val readings = sources?.takeIf { it.first === sourceKey }?.second ?: emptyList()
    val focused = readings.firstOrNull { it.key == selected }
    val obstacles = remember(lines) {
        lines.map { ReadingBounds(it.bounds.left.roundToInt(), it.bounds.top.roundToInt(),
            it.bounds.right.roundToInt(), it.bounds.bottom.roundToInt()) }
    }
    val layoutSource = remember(sourceKey, focused?.key, if (focused != null) visibleClip else null) { Any() }
    val layoutKey = remember(readings, layoutSource, fontPixels) { Any() }
    val result by produceState<Pair<Any, CameraAnnotationLayout>?>(null, layoutKey) {
        val computed = if (fit == null) CameraAnnotationLayout(emptyList()) else withContext(Dispatchers.Default) {
            val operation = currentCoroutineContext()
            layoutCameraPhotoReadings(readings, obstacles, photo.width, photo.height, fontPixels,
                selected = focused?.key, selectionClip = visibleClip, checkCancellation = { operation.ensureActive() })
        }
        value = layoutSource to computed
    }
    val layout = result?.takeIf { it.first === layoutSource }?.second ?: CameraAnnotationLayout(emptyList())
    val colors = annotationColors(preferences)
    val calloutColors = CameraAnnotationColors(MaterialTheme.colorScheme.inverseOnSurface.toArgb(),
        MaterialTheme.colorScheme.inverseSurface.toArgb())
    val highlightStroke = with(density) { 1.dp.toPx() } / scale
    val currentReadings by rememberUpdatedState(readings)
    val currentShowReadings by rememberUpdatedState(showReadings)
    val panTop by rememberUpdatedState(topOverlayHeight.toFloat())
    val panBottom by rememberUpdatedState(insets.getBottom(density).toFloat())
    val selectLabel = stringResource(R.string.photo_reading_select)
    val clearLabel = stringResource(R.string.photo_reading_clear)
    val zoomState = stringResource(R.string.camera_zoom_state, (gesture.zoom * 100).roundToInt())
    val selectedDescription = focused?.let { stringResource(R.string.photo_reading_selected, it.source, it.reading) }
    val zoomIn = stringResource(R.string.camera_zoom_in)
    val zoomOut = stringResource(R.string.camera_zoom_out)
    val zoomReset = stringResource(R.string.camera_zoom_reset)
    val description = stringResource(R.string.camera_photo_description)
    fun zoom(factor: Float): Boolean {
        gesture = gesture.update(factor, 0f, 0f, size.width / 2f, size.height / 2f,
            photo.width, photo.height, size.width, size.height, topPadding = panTop, bottomPadding = panBottom)
        return true
    }
    Canvas(
        modifier.onSizeChanged { size = it }
            .pointerInput(photo, size) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    gesture = gesture.update(zoom, pan.x, pan.y, centroid.x, centroid.y,
                        photo.width, photo.height, size.width, size.height, topPadding = panTop, bottomPadding = panBottom)
                }
            }
            .pointerInput(photo, size) {
                detectTapGestures(
                    onTap = { point ->
                        if (!currentShowReadings) return@detectTapGestures
                        val currentScale = (fit?.scale ?: 1f) * gesture.zoom
                        val imageLeft = (size.width - photo.width * currentScale) / 2f + gesture.x
                        val imageTop = (size.height - photo.height * currentScale) / 2f + gesture.y
                        selected = selectCameraPhotoReading(currentReadings,
                            (point.x - imageLeft) / currentScale, (point.y - imageTop) / currentScale, selected)
                    },
                    onDoubleTap = { gesture = CameraPhotoGesture() }
                )
            }
            .semantics {
                contentDescription = description
                text = AnnotatedString(if (!showReadings) "" else focused?.reading ?: layout.labels.joinToString(", ") { it.reading })
                stateDescription = if (showReadings && selectedDescription != null) "$zoomState · $selectedDescription" else zoomState
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { zoom(1.5f) },
                    CustomAccessibilityAction(zoomOut) { zoom(1 / 1.5f) },
                    CustomAccessibilityAction(zoomReset) { gesture = CameraPhotoGesture(); true }
                ) + (if (showReadings) readings else emptyList()).map { item ->
                    CustomAccessibilityAction("$selectLabel: ${item.source} · ${item.reading}") {
                        selected = if (selected == item.key) null else item.key
                        true
                    }
                } + if (showReadings && focused != null) listOf(
                    CustomAccessibilityAction(clearLabel) { selected = null; true }
                ) else emptyList()
            }
    ) {
        if (fit == null) return@Canvas
        drawIntoCanvas {
            it.nativeCanvas.withClip(0, 0, size.width, size.height) {
                translate(left, top)
                scale(scale, scale)
                drawBitmap(bitmap, 0f, 0f, null)
                if (showReadings) {
                    drawCameraPhotoHighlights(this, readings, focused?.key, highlightStroke)
                    drawCameraAnnotations(this, layout, if (focused != null) calloutColors else colors)
                }
            }
        }
    }
}

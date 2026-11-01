package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.smyddevstudio.kanako.service.selectReading
import com.smyddevstudio.kanako.camera.CameraAnnotationColors
import com.smyddevstudio.kanako.camera.CameraExportStyle
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
    onExportStyle: (CameraExportStyle?) -> Unit = {}
) {
    val lease = remember(photo) { photo.acquire() }
    DisposableEffect(lease) { onDispose { lease?.close() } }
    if (lease == null) return
    val bitmap = lease.bitmap
    var size by remember { mutableStateOf(IntSize.Zero) }
    var gesture by remember(photo, size) { mutableStateOf(CameraPhotoGesture()) }
    val fit = fitCameraImage(photo.width, photo.height, size.width, size.height)
    val fontPixels = with(LocalDensity.current) { preferences.furiganaSize.sizeSp.sp.toPx() } / (fit?.scale ?: 1f)
    val layout = remember(lines, photo, fontPixels, preferences.scriptType) {
        layoutCameraAnnotations(lines, photo.width, photo.height, fontPixels, preferences.scriptType)
    }
    val colors = annotationColors(preferences)
    var selected by remember(photo, lines.map { it.text }) { mutableStateOf<String?>(null) }
    val currentLayout by rememberUpdatedState(layout)
    val selectLabel = stringResource(R.string.reading_bring_front)
    val zoomState = stringResource(R.string.camera_zoom_state, (gesture.zoom * 100).roundToInt())
    val zoomIn = stringResource(R.string.camera_zoom_in)
    val zoomOut = stringResource(R.string.camera_zoom_out)
    val zoomReset = stringResource(R.string.camera_zoom_reset)
    val description = stringResource(R.string.camera_photo_description)
    val exportStyle = fit?.let { CameraExportStyle(fontPixels, preferences.scriptType, colors, selected) }
    SideEffect { onExportStyle(exportStyle) }
    fun zoom(factor: Float): Boolean {
        gesture = gesture.update(factor, 0f, 0f, size.width / 2f, size.height / 2f,
            photo.width, photo.height, size.width, size.height)
        return true
    }
    Canvas(
        modifier.onSizeChanged { size = it }
            .pointerInput(photo, size) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    gesture = gesture.update(zoom, pan.x, pan.y, centroid.x, centroid.y,
                        photo.width, photo.height, size.width, size.height)
                }
            }
            .pointerInput(photo, size) {
                detectTapGestures(
                    onTap = { point ->
                        val scale = (fit?.scale ?: 1f) * gesture.zoom
                        val left = (size.width - photo.width * scale) / 2f + gesture.x
                        val top = (size.height - photo.height * scale) / 2f + gesture.y
                        selected = selectReading(currentLayout.labels.map { it.target },
                            (point.x - left) / scale, (point.y - top) / scale, selected)
                    },
                    onDoubleTap = { gesture = CameraPhotoGesture() }
                )
            }
            .semantics {
                contentDescription = description
                text = AnnotatedString(layout.labels.joinToString(", ") { it.reading })
                stateDescription = zoomState
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { zoom(1.5f) },
                    CustomAccessibilityAction(zoomOut) { zoom(1 / 1.5f) },
                    CustomAccessibilityAction(zoomReset) { gesture = CameraPhotoGesture(); true }
                ) + layout.labels.map { ruby ->
                    CustomAccessibilityAction("$selectLabel: ${ruby.reading}") { selected = ruby.target.key; true }
                }
            }
    ) {
        val transform = fit ?: return@Canvas
        val scale = transform.scale * gesture.zoom
        val left = (size.width - photo.width * scale) / 2f + gesture.x
        val top = (size.height - photo.height * scale) / 2f + gesture.y
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            canvas.withClip(0, 0, size.width, size.height) {
                translate(left, top)
                scale(scale, scale)
                drawBitmap(bitmap, 0f, 0f, null)
                drawCameraAnnotations(this, layout, colors, selected)
            }
        }
    }
}

package com.smyddevstudio.kanako.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraPhoto
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.service.FloatingOverlayContent
import kotlin.math.roundToInt

@Composable
internal fun CameraResultContent(
    state: CameraReaderUiState,
    onBack: () -> Unit,
    onPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onToggleScriptType: () -> Unit = {},
    showReadings: Boolean = true,
    onShowReadingsChange: (Boolean) -> Unit = {},
    onControlsHeight: (Int) -> Unit = {},
    photograph: @Composable (Modifier) -> Unit = {},
    guide: @Composable () -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val guideMaxHeight = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                if (state.photo != null) photograph(Modifier.fillMaxSize())
                Spacer(Modifier.fillMaxWidth()
                    .height(with(density) { WindowInsets.safeDrawing.getTop(this).toDp() } + 96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
                Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp)
                    .onSizeChanged { onControlsHeight(it.height) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back), tint = Color.White)
                        }
                        IconButton(onClick = onPreferences, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Settings, stringResource(R.string.camera_appearance), tint = Color.White)
                        }
                    }
                }
                if (state.status == CameraReadingStatus.PROCESSING) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                } else if (state.photo == null) {
                    Text(stringResource(if (state.status == CameraReadingStatus.READ_ERROR) R.string.gallery_image_missing
                        else R.string.camera_photo_missing),
                        Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp), color = Color.White)
                } else if (state.status != CameraReadingStatus.PHOTO) {
                    Surface(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
                        shape = CircleShape, color = Color.Black.copy(alpha = 0.8f), contentColor = Color.White) {
                        Text(stringResource(cameraStatusString(state.status)), Modifier.padding(16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (state.photo != null) CameraPhotoKanako(state, showReadings, onShowReadingsChange,
                    preferences, onToggleScriptType, Modifier.fillMaxSize())
            }
            Box(Modifier.heightIn(max = guideMaxHeight)) { guide() }
        }
    }
}

@Composable
private fun CameraPhotoKanako(
    state: CameraReaderUiState,
    showReadings: Boolean,
    onShowReadingsChange: (Boolean) -> Unit,
    preferences: KanakoPreferences,
    onToggleScriptType: () -> Unit,
    modifier: Modifier = Modifier
) {
    var horizontal by rememberSaveable(state.photo) { mutableFloatStateOf(1f) }
    var vertical by rememberSaveable(state.photo) { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(modifier.safeDrawingPadding().padding(16.dp).padding(top = 48.dp)) {
        val density = LocalDensity.current
        val controlSize = with(density) {
            when (preferences.fabSize) {
                FabSize.COMPACT -> 48.dp
                FabSize.MEDIUM -> 56.dp
                FabSize.LARGE -> 68.dp
            }.roundToPx()
        }
        val travelX = (constraints.maxWidth - controlSize).coerceAtLeast(0)
        val travelY = (constraints.maxHeight - controlSize).coerceAtLeast(0)
        val status = when (state.status) {
            CameraReadingStatus.PROCESSING -> R.string.reading_working
            CameraReadingStatus.PHOTO -> if (showReadings) R.string.reading_shown else R.string.reading_ready
            CameraReadingStatus.NO_TEXT -> R.string.reading_no_text
            CameraReadingStatus.MODEL_NEEDED -> R.string.reading_model_missing
            CameraReadingStatus.READ_ERROR -> R.string.reading_ocr_failed
            else -> R.string.reading_ready
        }
        FloatingOverlayContent(
            preferences = preferences,
            showFurigana = showReadings && state.status == CameraReadingStatus.PHOTO,
            onReadOrClear = { onShowReadingsChange(!showReadings) },
            onDrag = { dx, dy ->
                if (travelX > 0) horizontal = (horizontal + dx / travelX).coerceIn(0f, 1f)
                if (travelY > 0) vertical = (vertical + dy / travelY).coerceIn(0f, 1f)
            },
            onDragEnd = {},
            onToggleScriptType = onToggleScriptType,
            onOpenApp = {},
            onStopService = {},
            modifier = Modifier.offset {
                IntOffset((horizontal * travelX).roundToInt(), (vertical * travelY).roundToInt())
            },
            mirrorMascot = horizontal < 0.5f,
            isReading = state.status == CameraReadingStatus.PROCESSING,
            statusMessage = status,
            showServiceActions = false,
            animateWhenReadingsShown = true,
            statusDescription = if (state.status == CameraReadingStatus.PHOTO) stringResource(
                if (showReadings) R.string.camera_comparison_readings else R.string.camera_comparison_original
            ) else null
        )
    }
}

@Preview(name = "Photo ready dark", widthDp = 393, heightDp = 820, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Photo large text", widthDp = 320, heightDp = 480, fontScale = 2f)
@Preview(name = "Photo landscape", widthDp = 720, heightDp = 360)
@Composable
private fun CameraReadyResultPreview() {
    val photo = remember { CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)) }
    DisposableEffect(photo) { onDispose { photo.close() } }
    KanakoTheme {
        CameraResultContent(
            CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo),
            {}, {},
            photograph = {
                Box(it.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.preview_sample_text), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

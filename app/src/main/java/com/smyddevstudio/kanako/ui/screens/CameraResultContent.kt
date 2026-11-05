package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraPhoto
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Composable
internal fun CameraResultContent(
    state: CameraReaderUiState,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onPreferences: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    canSave: Boolean = false,
    onOpen: () -> Unit = {},
    onShare: () -> Unit = {},
    photograph: @Composable (Modifier) -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().padding(horizontal = 16.dp)) {
        val stackActions = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val controlsMaxHeight = maxHeight * 0.5f
        Column(Modifier.fillMaxSize()) {
            CameraReaderTopBar(onBack, onPreferences, title = R.string.camera_result_title)
            if (state.photo == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.camera_photo_missing))
                    Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.camera_result_retake))
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    photograph(Modifier.fillMaxSize())
                    if (state.status == CameraReadingStatus.PROCESSING) CircularProgressIndicator()
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight)
                        .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.camera_photo_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (state.saved == false || state.status != CameraReadingStatus.PHOTO) {
                        Text(
                            stringResource(if (state.saved == false) R.string.camera_save_failed else cameraStatusString(state.status)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.saved == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    if (state.status in listOf(
                            CameraReadingStatus.READ_ERROR, CameraReadingStatus.MODEL_NEEDED, CameraReadingStatus.NO_TEXT
                        )) {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.camera_retry)) }
                    }
                    CameraPhotoActions(
                        onRetake = onRetake,
                        onSave = onSave,
                        onOpen = onOpen,
                        onShare = onShare,
                        enabled = canSave && state.status == CameraReadingStatus.PHOTO && !state.saving,
                        saving = state.saving,
                        saved = state.saved == true,
                        showSavedActions = state.saved == true && state.savedUri != null && !state.saving,
                        stackActions = stackActions
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPhotoActions(
    onRetake: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    enabled: Boolean,
    saving: Boolean,
    saved: Boolean,
    showSavedActions: Boolean,
    stackActions: Boolean,
    modifier: Modifier = Modifier
) {
    val saveLabel = stringResource(when {
        saving -> R.string.camera_saving
        saved -> R.string.camera_saved
        else -> R.string.camera_save
    })
    val saveColors = if (saved) ButtonDefaults.buttonColors(
        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) else ButtonDefaults.buttonColors()
    if (stackActions) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave, enabled = enabled && !saved, colors = saveColors,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(saveLabel, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (showSavedActions) CameraSavedImageActions(onOpen, onShare)
            }
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.camera_result_retake), textAlign = TextAlign.Center)
            }
        }
    } else {
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRetake,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.camera_result_retake), textAlign = TextAlign.Center)
            }
            Button(onClick = onSave, enabled = enabled && !saved, colors = saveColors,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)) {
                Text(saveLabel, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (showSavedActions) CameraSavedImageActions(onOpen, onShare)
        }
    }
}

@Composable
private fun CameraSavedImageActions(
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onOpen, modifier = Modifier.size(48.dp), colors = colors) {
            Icon(Icons.Rounded.Image, contentDescription = stringResource(R.string.camera_open_saved))
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp), colors = colors) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.camera_share))
        }
    }
}

@Preview(name = "Saved photo", widthDp = 393, heightDp = 820)
@Preview(name = "Saved photo dark", widthDp = 393, heightDp = 820, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Saved photo French", widthDp = 393, heightDp = 820, locale = "fr")
@Preview(name = "Saved photo large text", widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun CameraSavedResultPreview() {
    CameraPhotoResultPreview(saved = true)
}

@Preview(name = "Photo ready dark", widthDp = 393, heightDp = 820, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CameraReadyResultPreview() {
    CameraPhotoResultPreview(saved = false)
}

@Composable
private fun CameraPhotoResultPreview(saved: Boolean, modifier: Modifier = Modifier) {
    val photo = remember { CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)) }
    DisposableEffect(photo) { onDispose { photo.close() } }
    KanakoTheme {
        CameraResultContent(
            CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo, saved = if (saved) true else null,
                savedUri = if (saved) Uri.parse("content://media/external_primary/images/media/1") else null),
            {}, {}, {}, {}, {}, modifier = modifier, canSave = true,
            photograph = {
                Box(it.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.preview_sample_text), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraResultPreview() {
    KanakoTheme { CameraResultContent(CameraReaderUiState(status = CameraReadingStatus.PROCESSING), {}, {}, {}, {}, {}) }
}

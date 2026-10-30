package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
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
    photograph: @Composable (Modifier) -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().padding(horizontal = 16.dp)) {
        val stackActions = maxWidth < 480.dp || LocalDensity.current.fontScale > 1.3f
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
                    val message = when (state.saved) {
                        true -> R.string.camera_saved
                        false -> R.string.camera_save_failed
                        null -> cameraStatusString(state.status)
                    }
                    Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
                    if (state.status in listOf(
                            CameraReadingStatus.READ_ERROR, CameraReadingStatus.MODEL_NEEDED, CameraReadingStatus.NO_TEXT
                        )) {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.camera_retry)) }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = if (stackActions) 1 else 2
                    ) {
                        val actionModifier = if (stackActions) Modifier.fillMaxWidth() else Modifier.weight(1f)
                        OutlinedButton(onClick = onRetake, modifier = actionModifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.camera_result_retake))
                        }
                        Button(onClick = onSave,
                            enabled = canSave && state.status == CameraReadingStatus.PHOTO && !state.saving,
                            modifier = actionModifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (state.saving) R.string.camera_saving else R.string.camera_save))
                        }
                    }
                    Text(stringResource(R.string.camera_photo_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraResultPreview() {
    KanakoTheme { CameraResultContent(CameraReaderUiState(status = CameraReadingStatus.PROCESSING), {}, {}, {}, {}, {}) }
}

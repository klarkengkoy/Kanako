package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 16.dp)) {
        CameraReaderTopBar(onBack, onPreferences, title = R.string.camera_result_title)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            photograph(Modifier.fillMaxSize())
            if (state.photo == null) Text(stringResource(R.string.camera_photo_missing), Modifier.padding(24.dp))
            if (state.status == CameraReadingStatus.PROCESSING) CircularProgressIndicator()
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val message = when (state.saved) {
                true -> R.string.camera_saved
                false -> R.string.camera_save_failed
                null -> if (state.photo == null) R.string.camera_photo_missing else cameraStatusString(state.status)
            }
            Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
            if (state.photo != null) {
                Text(stringResource(R.string.camera_photo_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.photo != null && state.status in listOf(
                    CameraReadingStatus.READ_ERROR, CameraReadingStatus.MODEL_NEEDED, CameraReadingStatus.NO_TEXT
                )) {
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.camera_retry)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.camera_result_retake))
                }
                Button(onClick = onSave, enabled = canSave && state.status == CameraReadingStatus.PHOTO && !state.saving,
                    modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.saving) R.string.camera_saving else R.string.camera_save))
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

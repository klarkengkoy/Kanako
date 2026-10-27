package com.smyddevstudio.kanako.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.camera.CameraExportStyle
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.CameraPhotoSurface

@Composable
internal fun CameraResultScreen(
    viewModel: CameraReaderViewModel,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var style by remember(state.photo) { mutableStateOf<CameraExportStyle?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        viewModel.completeExport(context.contentResolver, uri)
    }
    CameraResultContent(
        state = state,
        onBack = { viewModel.returnToLive(); onBack() },
        onRetake = { viewModel.returnToLive(); onRetake() },
        onPreferences = onNavigateToPreferences,
        onRetry = viewModel::retryPhoto,
        onSave = {
            val exportStyle = style
            if (exportStyle != null && viewModel.prepareExport(exportStyle)) {
                try {
                    picker.launch("Kanako-${System.currentTimeMillis()}.png")
                } catch (_: RuntimeException) {
                    viewModel.completeExport(context.contentResolver, null, unavailable = true)
                }
            }
        },
        modifier = modifier,
        canSave = style != null,
        photograph = { surfaceModifier ->
            state.photo?.let { photo ->
                CameraPhotoSurface(photo, state.lines, preferences, surfaceModifier, onExportStyle = { style = it })
            }
        }
    )
}


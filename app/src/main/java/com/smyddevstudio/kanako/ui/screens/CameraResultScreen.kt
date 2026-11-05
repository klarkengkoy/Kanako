package com.smyddevstudio.kanako.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraExportStyle
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import com.smyddevstudio.kanako.camera.cameraExportFileName
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
    val saveToDocument = {
        val exportStyle = style
        if (exportStyle != null && viewModel.prepareExport(exportStyle)) {
            try {
                picker.launch(cameraExportFileName())
            } catch (_: RuntimeException) {
                viewModel.completeExport(context.contentResolver, null, unavailable = true)
            }
        }
    }
    CameraResultContent(
        state = state,
        onBack = { viewModel.returnToLive(); onBack() },
        onRetake = { viewModel.returnToLive(); onRetake() },
        onPreferences = onNavigateToPreferences,
        onRetry = viewModel::retryPhoto,
        onSave = {
            if (Build.VERSION.SDK_INT >= 29) {
                style?.let { viewModel.saveToPictures(context.contentResolver, it) }
            } else {
                saveToDocument()
            }
        },
        modifier = modifier,
        canSave = style != null,
        onOpen = { state.savedUri?.let { openCameraExport(context, it, share = false) } },
        onShare = { state.savedUri?.let { openCameraExport(context, it, share = true) } },
        photograph = { surfaceModifier ->
            state.photo?.let { photo ->
                CameraPhotoSurface(photo, state.lines, preferences, surfaceModifier, onExportStyle = { style = it })
            }
        }
    )
}

private fun openCameraExport(context: Context, uri: Uri, share: Boolean) {
    val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
        if (share) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
        } else {
            setDataAndType(uri, "image/png")
        }
        clipData = ClipData.newRawUri("Kanako", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val errorMessage = if (share) R.string.camera_share_failed else R.string.camera_open_failed
    try {
        context.startActivity(if (share) Intent.createChooser(intent, null) else intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
}

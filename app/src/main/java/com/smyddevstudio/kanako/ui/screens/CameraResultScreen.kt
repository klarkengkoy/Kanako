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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraExportStyle
import com.smyddevstudio.kanako.camera.SavedCameraImages
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
    preferences: KanakoPreferences = KanakoPreferences(),
    onNavigateToGallery: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val savedImages = remember(context) { SavedCameraImages(context) }
    var documentName by rememberSaveable { mutableStateOf(cameraExportFileName()) }
    var style by remember(state.photo) { mutableStateOf<CameraExportStyle?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        viewModel.completeExport(context.contentResolver, uri, onSaved = { savedImages.retainDocument(it, documentName) })
    }
    val saveToDocument = {
        val exportStyle = style
        if (exportStyle != null && viewModel.prepareExport(exportStyle)) {
            try {
                documentName = cameraExportFileName()
                picker.launch(documentName)
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
        onOpen = onNavigateToGallery,
        onShare = { state.savedUri?.let { shareCameraExport(context, it) } },
        photograph = { surfaceModifier ->
            state.photo?.let { photo ->
                CameraPhotoSurface(photo, state.lines, preferences, surfaceModifier, onExportStyle = { style = it; viewModel.updateExportStyle(it) })
            }
        }
    )
}

internal fun shareCameraExport(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Kanako", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val errorMessage = R.string.camera_share_failed
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }
}

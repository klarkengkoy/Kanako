package com.smyddevstudio.kanako.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import com.smyddevstudio.kanako.camera.LiveCameraSession
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.CameraReadingOverlay

@Composable
internal fun CameraReaderScreen(
    viewModel: CameraReaderViewModel,
    onBack: () -> Unit,
    onCaptured: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var session by remember { mutableStateOf<LiveCameraSession?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        granted = hasCameraPermission(context)
    }
    val activity = context.cameraActivity()
    val liveDescription = stringResource(R.string.camera_live_description)
    val permanentlyDenied = requested && !granted && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(preview, lifecycleOwner, granted, retry) {
        val view = preview
        var active: LiveCameraSession? = null
        var streamObserver: Observer<PreviewView.StreamState>? = null
        fun stop() {
            streamObserver?.let { view?.previewStreamState?.removeObserver(it) }
            streamObserver = null
            view?.controller = null
            active?.close()
            active = null
            session = null
            viewModel.pauseLive()
        }
        fun start() {
            if (view == null || !hasCameraPermission(context) || active != null ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (viewModel.state.value.photo != null) viewModel.returnToLive()
            viewModel.resumeLive()
            try {
                val created = LiveCameraSession(
                    context, viewModel::liveTicket, viewModel::invalidateGeometry,
                    viewModel::receiveLive, viewModel::cameraError
                )
                active = created
                session = created
                view.controller = created.controller
                streamObserver = Observer { viewModel.previewReady(it == PreviewView.StreamState.STREAMING) }
                view.previewStreamState.observe(lifecycleOwner, streamObserver!!)
                if (!created.bind(lifecycleOwner)) viewModel.cameraError()
            } catch (_: RuntimeException) {
                stop()
                viewModel.cameraError()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (granted) start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(R.string.camera_state_permission)) },
            text = { Text(stringResource(R.string.camera_state_permission_body)) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = false
                    launcher.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.camera_permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.camera_cancel)) }
            }
        )
    }
    CameraReaderContent(
        state = state,
        granted = granted,
        onBack = { viewModel.returnToLive(); onBack() },
        onPreferences = onNavigateToPreferences,
        onCapture = { session?.let { viewModel.capture(it, onCaptured) } },
        onPermission = {
            when {
                permanentlyDenied -> context.startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()
                ))
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> rationale = true
                else -> launcher.launch(Manifest.permission.CAMERA)
            }
        },
        onRetry = { retry++ },
        onZoom = { factor ->
            viewModel.invalidateGeometry()
            val controller = session?.controller
            val zoom = controller?.cameraInfo?.zoomState?.value
            if (zoom != null) controller.setZoomRatio((zoom.zoomRatio * factor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
        },
        modifier = modifier,
        permissionLabel = if (permanentlyDenied) R.string.camera_settings else R.string.camera_allow,
        viewfinder = { cameraModifier ->
            Box(cameraModifier) {
                AndroidView(
                    factory = {
                        PreviewView(it).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            contentDescription = liveDescription
                            preview = this
                        }
                    },
                    modifier = Modifier.fillMaxSize().pointerInput(viewModel) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                viewModel.invalidateGeometry()
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                        }
                    },
                    update = { it.contentDescription = liveDescription },
                    onRelease = { it.controller = null }
                )
                CameraReadingOverlay(state.lines, preferences, Modifier.fillMaxSize())
            }
        }
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.cameraActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.cameraActivity()
    else -> null
}


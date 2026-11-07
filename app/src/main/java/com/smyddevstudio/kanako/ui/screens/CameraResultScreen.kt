package com.smyddevstudio.kanako.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.service.KanakoOverlaySession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.CameraPhotoSurface

@Composable
internal fun CameraResultScreen(
    viewModel: CameraReaderViewModel,
    onBack: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onToggleScriptType: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> KanakoOverlaySession.setPhotoReaderVisible(true)
                Lifecycle.Event.ON_PAUSE -> KanakoOverlaySession.setPhotoReaderVisible(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        KanakoOverlaySession.setPhotoReaderVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            KanakoOverlaySession.setPhotoReaderVisible(false)
        }
    }
    var showReadings by rememberSaveable(state.photo) { mutableStateOf(true) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val returnToCamera = { viewModel.returnToLive(); onBack() }
    BackHandler(onBack = returnToCamera)
    CameraResultContent(
        state = state,
        onBack = returnToCamera,
        onPreferences = onNavigateToPreferences,
        modifier = modifier,
        preferences = preferences,
        onToggleScriptType = onToggleScriptType,
        showReadings = showReadings,
        onShowReadingsChange = { showReadings = it },
        onControlsHeight = { controlsHeight = it },
        photograph = { surfaceModifier ->
            state.photo?.let { photo ->
                CameraPhotoSurface(photo, state.lines, preferences, surfaceModifier, showReadings = showReadings,
                    topOverlayHeight = controlsHeight)
            }
        }
    )
}

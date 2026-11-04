package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.service.GuidedReaderSession
import com.smyddevstudio.kanako.service.GuidedReaderStep
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Composable
fun TutorialScreen(
    onBack: () -> Unit,
    onComplete: (TutorialPath) -> Unit,
    modifier: Modifier = Modifier
) {
    val step by GuidedReaderSession.step.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        fun updateVisibility() {
            GuidedReaderSession.setSampleVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            GuidedReaderSession.advance(GuidedReaderStep.SHOW, GuidedReaderStep.READ)
        }
        val observer = LifecycleEventObserver { _, _ -> updateVisibility() }
        lifecycle.addObserver(observer)
        updateVisibility()
        onDispose {
            lifecycle.removeObserver(observer)
            GuidedReaderSession.setSampleVisible(false)
        }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.guided_intro_title), style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().height(180.dp)) {
        Text(stringResource(when (step) {
            GuidedReaderStep.READ -> R.string.guided_read
            GuidedReaderStep.CLEAR -> R.string.guided_clear
            GuidedReaderStep.DRAG -> R.string.guided_drag
            GuidedReaderStep.CONTROLS -> R.string.guided_controls
            GuidedReaderStep.SCRIPT -> R.string.guided_script
            GuidedReaderStep.PREFERENCES, GuidedReaderStep.RETURN -> R.string.guided_preferences
            GuidedReaderStep.STOP -> R.string.guided_stop
            GuidedReaderStep.COMPLETE -> R.string.guided_complete
            else -> R.string.guided_show
        }), modifier = Modifier.verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite })
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(stringResource(R.string.guided_sample), Modifier.padding(24.dp), fontSize = 28.sp, lineHeight = 48.sp)
        }
        if (step == GuidedReaderStep.COMPLETE) {
            Button(onClick = { onComplete(TutorialPath.FLOATING) }) { Text(stringResource(R.string.done)) }
        } else {
            TextButton(onClick = onBack) { Text(stringResource(R.string.guided_skip)) }
        }
    }
}

@Composable
fun TutorialInvitation(onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.guided_intro_body))
            Button(onClick = onStart) { Text(stringResource(R.string.guided_start)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.guided_skip)) }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun GuidedReadingPreview() {
    KanakoTheme { TutorialScreen({}, {}) }
}

package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
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
        .padding(horizontal = 24.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(stringResource(R.string.guided_intro_title), style = MaterialTheme.typography.headlineSmall)
            Surface(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.guided_action),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        stringResource(when (step) {
                            GuidedReaderStep.READ -> R.string.guided_read
                            GuidedReaderStep.CLEAR -> R.string.guided_clear
                            GuidedReaderStep.DRAG -> R.string.guided_drag
                            GuidedReaderStep.CONTROLS -> R.string.guided_controls
                            GuidedReaderStep.SCRIPT -> R.string.guided_script
                            GuidedReaderStep.PREFERENCES, GuidedReaderStep.RETURN -> R.string.guided_preferences
                            GuidedReaderStep.STOP -> R.string.guided_stop
                            GuidedReaderStep.COMPLETE -> R.string.guided_complete
                            else -> R.string.guided_show
                        }),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(stringResource(R.string.guided_sample), Modifier.padding(24.dp), fontSize = 28.sp, lineHeight = 48.sp)
            }
        }
        HorizontalDivider()
        if (step == GuidedReaderStep.COMPLETE) {
            Button(
                onClick = { onComplete(TutorialPath.FLOATING) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.done)) }
        } else {
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.guided_skip)) }
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

@Preview(name = "Light", widthDp = 360, heightDp = 800)
@Preview(name = "Dark", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Compact large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun GuidedReadingPreview() {
    KanakoTheme { TutorialScreen({}, {}) }
}

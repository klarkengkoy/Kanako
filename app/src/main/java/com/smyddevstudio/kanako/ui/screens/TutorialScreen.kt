package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.service.FloatingOverlayContent
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.components.FuriganaText
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    onBack: () -> Unit,
    onComplete: (TutorialPath) -> Unit,
    modifier: Modifier = Modifier,
    initialPath: TutorialPath? = null,
    preferences: KanakoPreferences = KanakoPreferences(),
    onOpenReader: (TutorialPath) -> Unit = {},
    initialStep: Int = 0
) {
    var path by rememberSaveable { mutableStateOf(initialPath) }
    var step by rememberSaveable { mutableIntStateOf(initialStep.coerceIn(0, if (initialPath == TutorialPath.CAMERA) 5 else 4)) }
    var readShown by rememberSaveable { mutableStateOf(false) }
    var readOnce by rememberSaveable { mutableStateOf(false) }
    var cleared by rememberSaveable { mutableStateOf(false) }
    var moved by rememberSaveable { mutableStateOf(false) }
    var stopped by rememberSaveable { mutableStateOf(false) }
    var focused by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var showSave by rememberSaveable { mutableStateOf(false) }
    var script by rememberSaveable { mutableStateOf(preferences.scriptType) }
    var scriptChanged by rememberSaveable { mutableStateOf(false) }
    var autoRead by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var x by rememberSaveable { mutableFloatStateOf(0f) }
    var y by rememberSaveable { mutableFloatStateOf(0f) }
    val selected = path
    val scrollState = key(selected, step) { rememberScrollState() }
    val finalStep = if (selected == TutorialPath.CAMERA) 5 else 4
    val ready = when (selected) {
        TutorialPath.FLOATING -> when (step) { 1 -> readOnce && cleared; 2 -> moved && stopped; else -> true }
        TutorialPath.CAMERA -> when (step) { 1 -> focused && zoomed; 2 -> captured; 3 -> scriptChanged; 4 -> saved; else -> true }
        null -> true
    }
    fun choose(option: TutorialPath) {
        path = option
        step = 0
        readShown = false
        readOnce = false
        cleared = false
        moved = false
        stopped = false
        focused = false
        zoomed = false
        captured = false
        saved = false
        showSave = false
        script = preferences.scriptType
        scriptChanged = false
        autoRead = false
        zoom = 1f
        x = 0f
        y = 0f
    }
    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text(stringResource(R.string.camera_save)) },
            text = { Text(stringResource(R.string.tutorial_save_confirm)) },
            confirmButton = {
                TextButton(onClick = { saved = true; showSave = false }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text(stringResource(R.string.snapshot_disclosure_cancel)) }
            }
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tutorial_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step > 0) step-- else if (selected != null) path = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.tutorial_exit))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(scrollState).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected == null || step == 0) {
                    Text(stringResource(R.string.tutorial_sample_notice), style = MaterialTheme.typography.bodyMedium)
                }
                if (selected == null) {
                    TutorialPath.entries.forEach { option ->
                        OutlinedButton(onClick = { choose(option) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Column {
                                Text(stringResource(if (option == TutorialPath.FLOATING) R.string.help_floating else R.string.camera_title))
                                if (if (option == TutorialPath.FLOATING) preferences.completedFloatingTutorial else preferences.completedCameraTutorial) {
                                    Text(stringResource(R.string.tutorial_completed), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    Text(stringResource(R.string.tutorial_step, step + 1, finalStep + 1),
                        modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    val body = if (step == finalStep) R.string.tutorial_finished else when (selected) {
                        TutorialPath.FLOATING -> when (step) {
                            0 -> R.string.how_start
                            1 -> R.string.tutorial_floating_read
                            2 -> R.string.tutorial_floating_controls
                            else -> R.string.how_move
                        }
                        TutorialPath.CAMERA -> when (step) {
                            0 -> R.string.camera_state_permission_body
                            1 -> R.string.tutorial_camera_focus
                            2 -> R.string.tutorial_camera_capture
                            3 -> R.string.tutorial_camera_appearance
                            else -> R.string.tutorial_camera_save
                        }
                    }
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
                    if (selected == TutorialPath.FLOATING && step in 1..2) {
                        val density = LocalDensity.current
                        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp)) {
                            val maxX = with(density) { (maxWidth - 68.dp).coerceAtLeast(0.dp).toPx() }
                            val maxY = with(density) { (maxHeight - 68.dp).toPx() }
                            TutorialSample(readShown, script, modifier = Modifier.align(Alignment.Center))
                            if (!stopped) {
                                FloatingOverlayContent(
                                    preferences = preferences.copy(scriptType = script),
                                    showFurigana = readShown,
                                    onReadOrClear = {
                                        if (readShown) cleared = true else readOnce = true
                                        readShown = !readShown
                                    },
                                    onDrag = { dx, dy ->
                                        x = (x + dx).coerceIn(0f, maxX)
                                        y = (y + dy).coerceIn(0f, maxY)
                                        if (dx != 0f || dy != 0f) moved = true
                                    },
                                    onDragEnd = {},
                                    onToggleScriptType = { script = if (script == ScriptType.KANA) ScriptType.ROMAJI else ScriptType.KANA },
                                    onOpenApp = onBack,
                                    onStopService = { stopped = true },
                                    statusMessage = if (readShown) R.string.reading_shown else R.string.reading_ready,
                                    modifier = Modifier.offset { IntOffset(x.coerceIn(0f, maxX).roundToInt(), y.coerceIn(0f, maxY).roundToInt()) }
                                )
                            }
                        }
                    }
                    if (selected == TutorialPath.FLOATING && step == 3) {
                        Text(stringResource(R.string.auto_read_title))
                        Switch(checked = autoRead, onCheckedChange = { autoRead = it })
                    }
                    if (selected == TutorialPath.CAMERA && step in 1..4) {
                        Surface(onClick = { focused = true }, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(Modifier.fillMaxWidth().height(180.dp).clipToBounds()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, scale, _ ->
                                        zoom = (zoom * scale).coerceIn(1f, 3f)
                                        zoomed = true
                                    }
                                }, contentAlignment = Alignment.Center) {
                                TutorialSample(true, script, Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom })
                            }
                        }
                        if (step == 1) {
                            Text(stringResource(R.string.camera_zoom_state, (zoom * 100).roundToInt()))
                            Slider(value = zoom, onValueChange = { zoom = it; zoomed = true }, valueRange = 1f..3f)
                        }
                        if (step == 2) {
                            Button(onClick = { captured = !captured }) {
                                Text(stringResource(if (captured) R.string.camera_result_retake else R.string.camera_capture))
                            }
                        }
                        if (step == 3) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScriptType.entries.forEach { option ->
                                    FilterChip(selected = script == option,
                                        onClick = { scriptChanged = scriptChanged || script != option; script = option },
                                        label = { Text(stringResource(if (option == ScriptType.KANA) R.string.script_kana else R.string.script_romaji)) },
                                        modifier = Modifier.heightIn(min = 48.dp))
                                }
                            }
                        }
                        if (step == 4) {
                            Button(onClick = { showSave = true }) { Text(stringResource(R.string.camera_save)) }
                        }
                    }
                    if (ready && step in 1 until finalStep) {
                        Text(stringResource(R.string.tutorial_success),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    if (step == finalStep) {
                        OutlinedButton(onClick = { onOpenReader(selected) }) { Text(stringResource(R.string.tutorial_start_tool)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (step == finalStep) onComplete(selected) else step++ }, enabled = ready) {
                            Text(stringResource(if (step == finalStep) R.string.tutorial_complete else R.string.tutorial_next))
                        }
                        if (step < finalStep) {
                            TextButton(onClick = { step++ }) { Text(stringResource(R.string.tutorial_skip)) }
                        } else {
                            TextButton(onClick = { path = null }) { Text(stringResource(R.string.tutorial_replay)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialSample(
    showReadings: Boolean,
    script: ScriptType,
    modifier: Modifier = Modifier
) {
    FuriganaText(
        segments = listOf(FuriganaSegment("日本語", if (script == ScriptType.KANA) "にほんご" else "nihongo")),
        modifier = modifier.padding(16.dp),
        kanjiFontSize = 32.sp,
        furiganaFontSize = 18.sp,
        furiganaColor = MaterialTheme.colorScheme.primary,
        showFurigana = showReadings
    )
}

@Composable
fun TutorialInvitation(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tutorial_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tutorial_invite), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart) { Text(stringResource(R.string.tutorial_practice)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.snapshot_disclosure_cancel)) }
            }
        }
    }
}

@Preview(name = "Floating practice", widthDp = 360, heightDp = 800)
@Preview(name = "Japanese large text", locale = "ja", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun FloatingPracticePreview() {
    KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.FLOATING, initialStep = 1) }
}

@Preview(name = "Camera practice", widthDp = 360, heightDp = 800)
@Preview(name = "Nepali large text", locale = "ne", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun CameraPracticePreview() {
    KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.CAMERA, initialStep = 1) }
}

@Preview(name = "Save practice", widthDp = 360, heightDp = 800)
@Composable
private fun CameraSavePracticePreview() {
    KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.CAMERA, initialStep = 4) }
}

@Preview(widthDp = 360, heightDp = 800)
@Preview(name = "Compact large text", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Nepali", locale = "ne", widthDp = 360, heightDp = 800)
@Composable
private fun TutorialChooserPreview() {
    KanakoTheme { TutorialScreen({}, {}) }
}

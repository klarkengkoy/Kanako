package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import com.smyddevstudio.kanako.service.RecognizedTextSpan
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import com.smyddevstudio.kanako.service.ReadingBounds
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.service.GuidedReaderSession
import com.smyddevstudio.kanako.ui.components.KanakoButton
import com.smyddevstudio.kanako.ui.components.KanakoCard
import com.smyddevstudio.kanako.ui.components.KanakoTextButton
import com.smyddevstudio.kanako.ui.components.tutorialTarget
import com.smyddevstudio.kanako.ui.components.tutorialAllows

@Composable
internal fun TutorialScreen(
    step: Int,
    path: TutorialPath,
    onChooseReader: (TutorialPath) -> Unit,
    onPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    floatingSupported: Boolean = true,
    floatingReaderActive: Boolean = false
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val automatic = step == 6
    val sampleText = stringResource(if (automatic) R.string.guided_auto_sample else R.string.guided_sample)
    val sample = path == TutorialPath.FLOATING && (step in 1..2 || automatic)
    val scroll = rememberScrollState()
    var textLayout by remember(sampleText) { mutableStateOf<TextLayoutResult?>(null) }
    var textOrigin by remember(sampleText) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(sampleText, textLayout, textOrigin) {
        val layout = textLayout ?: return@LaunchedEffect
        val origin = textOrigin ?: return@LaunchedEffect
        GuidedReaderSession.setSampleText(sampleText, sampleText.indices.filter { !sampleText[it].isWhitespace() }.map { index ->
            val box = layout.getBoundingBox(index).translate(origin)
            RecognizedTextSpan(index, index + 1, ReadingBounds(box.left.roundToInt(), box.top.roundToInt(),
                box.right.roundToInt(), box.bottom.roundToInt()))
        })
    }
    LaunchedEffect(automatic, scroll) {
        if (automatic) snapshotFlow { scroll.isScrollInProgress }.collect {
            GuidedReaderSession.setSampleScrolling(it)
        }
    }
    val tutorial by GuidedReaderSession.state.collectAsStateWithLifecycle()
    DisposableEffect(lifecycle, sample, tutorial.active) {
        fun updateVisibility() {
            GuidedReaderSession.setSampleVisible(sample && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        val observer = LifecycleEventObserver { _, _ -> updateVisibility() }
        lifecycle.addObserver(observer)
        updateVisibility()
        onDispose {
            lifecycle.removeObserver(observer)
            GuidedReaderSession.setSampleVisible(false)
        }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .then(if (automatic) Modifier else Modifier.verticalScroll(scroll)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.guided_intro_title), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        if (step == 0) {
            OutlinedButton(onClick = { onChooseReader(TutorialPath.FLOATING) },
                enabled = floatingSupported, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tutorial_try_floating))
            }
            OutlinedButton(onClick = { onChooseReader(TutorialPath.CAMERA) },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tutorial_try_camera))
            }
            if (!floatingSupported) Text(stringResource(R.string.setup_unsupported))
        } else if (step == 3) {
            OutlinedButton(onClick = onPreferences, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tutorial_open_preferences))
            }
        } else {
            if (sample) {
                Surface(Modifier.fillMaxWidth().then(if (automatic) Modifier.weight(1f) else Modifier).tutorialTarget("sample").onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    val screen = IntArray(2)
                    val window = IntArray(2)
                    view.getLocationOnScreen(screen)
                    view.getLocationInWindow(window)
                    GuidedReaderSession.setSampleBounds(ReadingBounds(
                        bounds.left.roundToInt() + screen[0] - window[0],
                        bounds.top.roundToInt() + screen[1] - window[1],
                        bounds.right.roundToInt() + screen[0] - window[0],
                        bounds.bottom.roundToInt() + screen[1] - window[1]))
                }, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(sampleText, Modifier.then(if (automatic) Modifier.verticalScroll(scroll) else Modifier)
                        .padding(24.dp).onGloballyPositioned { coordinates ->
                        val screen = IntArray(2)
                        val window = IntArray(2)
                        view.getLocationOnScreen(screen)
                        view.getLocationInWindow(window)
                        textOrigin = coordinates.localToWindow(Offset.Zero) + Offset(
                            (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
                    }, fontSize = 28.sp, lineHeight = 48.sp, onTextLayout = { textLayout = it })
                }
            }
            if (path == TutorialPath.CAMERA || !floatingReaderActive) {
                OutlinedButton(onClick = { onChooseReader(path) }, enabled = tutorialAllows("sample_start"), modifier = Modifier.heightIn(min = 48.dp).tutorialTarget("sample_start")) {
                    Text(stringResource(if (path == TutorialPath.FLOATING)
                        R.string.tutorial_try_floating else R.string.tutorial_try_camera))
                }
            }
        }
    }
}

@Composable
fun TutorialInvitation(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    KanakoCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.guided_intro_body))
            KanakoButton(onClick = onStart, enabled = enabled) { Text(stringResource(R.string.guided_start), fontWeight = FontWeight.Bold) }
            KanakoTextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.guided_skip), fontWeight = FontWeight.Bold) }
        }
    }
}

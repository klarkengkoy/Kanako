package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.components.LocalTutorialFocus
import com.smyddevstudio.kanako.ui.components.TutorialSpotlight

internal fun tutorialPrompts(step: Int, path: TutorialPath): List<Int> = when (step) {
    1 -> if (path == TutorialPath.FLOATING)
        listOf(R.string.tutorial_read_sample, R.string.tutorial_highlights_body, R.string.tutorial_dismiss)
        else listOf(R.string.tutorial_controls_camera, R.string.tutorial_highlights_body, R.string.tutorial_dismiss)
    2 -> if (path == TutorialPath.FLOATING)
        listOf(R.string.tutorial_menu, R.string.tutorial_script, R.string.tutorial_controls_floating)
        else listOf(R.string.tutorial_menu, R.string.tutorial_script, R.string.tutorial_photo_toggle)
    6 -> listOf(R.string.tutorial_auto_read, R.string.tutorial_auto_scroll)
    4 -> listOf(R.string.tutorial_tile_setup, R.string.tutorial_tile_use)
    else -> emptyList()
}

@Composable
internal fun TutorialLayout(
    step: Int?,
    path: TutorialPath,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    prompt: Int = 0,
    onPrevious: () -> Unit = {},
    canPrevious: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    val focus = LocalTutorialFocus.current
    val surface = MaterialTheme.colorScheme.surface
    SubcomposeLayout(modifier.fillMaxSize().background(surface)) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val footerConstraints = Constraints(minWidth = width, maxWidth = width, maxHeight = height / 2)
        val footer = if (step == null) null else subcompose("footer") {
            TutorialGuide(step, path, onNext, onSkip, prompt = prompt,
                onPrevious = onPrevious, canPrevious = canPrevious)
        }.single().measure(footerConstraints)
        // Only the camera needs a stable preview aspect ratio across text changes.
        // Other screens reserve exactly the current instruction's natural height.
        val stages = if (path == TutorialPath.CAMERA && step in listOf(1, 2)) listOf(1, 2) else emptyList()
        val variants = stages.flatMap { stage ->
            (0..tutorialPrompts(stage, path).lastIndex.coerceAtLeast(0)).map { stage to it }
        }
        val sizes = subcompose("footer-sizing") {
            CompositionLocalProvider(LocalTutorialFocus provides null) {
                variants.forEach { (stage, index) ->
                    TutorialGuide(stage, path, {}, {}, modifier = Modifier.clearAndSetSemantics {}, prompt = index)
                }
            }
        }.map { it.measure(footerConstraints).height }
        val reserved = maxOf(footer?.height ?: 0, sizes.maxOrNull() ?: 0)
        val reader = subcompose("reader") {
            Box(Modifier.fillMaxSize()) {
                content(Modifier.fillMaxSize())
                focus?.let { TutorialSpotlight(it) }
            }
        }.single().measure(Constraints.fixed(width, (height - reserved).coerceAtLeast(0)))
        layout(width, height) {
            reader.placeRelative(0, 0)
            footer?.placeRelative(0, height - footer.height)
        }
    }
}

@Composable
internal fun TutorialGuide(
    step: Int,
    path: TutorialPath,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    prompt: Int = 0,
    onPrevious: () -> Unit = {},
    canPrevious: Boolean = false
) {
    val focus = LocalTutorialFocus.current
    val title = when (step) {
        -1 -> R.string.tutorial_setup
        0 -> R.string.tutorial_choose
        1 -> if (path == TutorialPath.CAMERA && prompt == 0) R.string.camera_capture else R.string.tutorial_highlights
        2 -> R.string.tutorial_controls
        3, 5 -> R.string.tutorial_preferences
        6 -> R.string.auto_read_title
        else -> R.string.tile_title
    }
    val body = when (step) {
        -1 -> when {
            path == TutorialPath.CAMERA -> R.string.tutorial_setup_camera
            focus?.allowed?.contains("start_reader") == true -> R.string.tutorial_setup_ready
            focus?.allowed?.contains("overlay_access") == true -> R.string.tutorial_setup_overlay
            focus?.allowed?.contains("screen_access") == true -> R.string.tutorial_setup_screen
            else -> R.string.tutorial_setup_floating
        }
        0 -> R.string.tutorial_choose_body
        1 -> R.string.tutorial_highlights_body
        2 -> if (path == TutorialPath.FLOATING) R.string.tutorial_controls_floating else R.string.tutorial_controls_camera
        3 -> R.string.tutorial_preferences_body
        5 -> R.string.tutorial_open_preferences
        else -> R.string.quick_settings_guide_body
    }
    val message = focus?.message ?: tutorialPrompts(step, path).getOrNull(prompt) ?: body
    val label = when (message) {
        R.string.tutorial_setup_ready -> R.string.setup_enable
        R.string.tutorial_setup_overlay -> R.string.setup_open_overlay
        R.string.tutorial_setup_screen -> focus?.actionLabel ?: R.string.setup_open_accessibility
        R.string.tutorial_setup_camera -> R.string.camera_open
        else -> null
    }
    val text = if (label == null) stringResource(message) else stringResource(message, stringResource(label))
    val done = step == 4 && prompt == tutorialPrompts(step, path).lastIndex
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val scroll = rememberScrollState()
                LaunchedEffect(step, path, prompt, text) { scroll.scrollTo(0) }
                Box(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.verticalScroll(scroll)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(focus?.heading ?: title), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                    // An overlay cannot change the measured height and trigger another overflow calculation.
                    if (scroll.canScrollForward) Surface(Modifier.align(Alignment.BottomEnd)) {
                        Icon(Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.tutorial_scroll_more),
                            modifier = Modifier.size(24.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.guided_skip), maxLines = 1, softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedIconButton(onClick = onPrevious, enabled = canPrevious, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.tutorial_previous))
                        }
                        FilledIconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                            Icon(if (done) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                                stringResource(if (done) R.string.done else R.string.tutorial_next))
                        }
                    }
                }
            }
        }
    }
}

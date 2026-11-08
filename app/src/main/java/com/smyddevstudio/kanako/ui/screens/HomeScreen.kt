package com.smyddevstudio.kanako.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.components.LocalTutorialFocus
import com.smyddevstudio.kanako.ui.components.tutorialAllows
import com.smyddevstudio.kanako.ui.components.tutorialTarget
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import com.smyddevstudio.kanako.ui.components.KanakoButton as Button
import androidx.compose.material3.ButtonDefaults
import com.smyddevstudio.kanako.ui.components.KanakoCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import com.smyddevstudio.kanako.ui.components.KanakoIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.smyddevstudio.kanako.ui.components.KanakoOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.smyddevstudio.kanako.ui.components.KanakoTextButton as TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.ui.components.CameraLaunchFlash
import com.smyddevstudio.kanako.ui.components.AnimatedMascot
import com.smyddevstudio.kanako.ui.components.rememberAnimationsEnabled
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.LocalKanakoDarkTheme
import com.smyddevstudio.kanako.ui.theme.WarmCreamSurface
import com.smyddevstudio.kanako.ui.theme.DarkWarmCreamSurface

@Composable
fun HomeScreen(
    onNavigateToHowItWorks: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onUpdateOverlayActive: (Boolean) -> Unit = {},
    supported: Boolean = true,
    overlayGranted: Boolean = false,
    accessibilityEnabled: Boolean = false,
    accessibilityConnected: Boolean = false,
    onRequestOverlay: () -> Unit = {},
    onRequestAccessibility: () -> Unit = {},
    showTutorialInvitation: Boolean = false,
    onNavigateToTutorial: () -> Unit = {},
    onDismissTutorialInvitation: () -> Unit = {},
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
    tutorialActive: Boolean = false,
    onSelectTutorialReader: (TutorialPath) -> Unit = {},
    onStartTutorialReader: (TutorialPath) -> Unit = onSelectTutorialReader
) {
    val focus = LocalTutorialFocus.current
    val chooser = focus?.step == 0
    val floatingInteraction = remember { MutableInteractionSource() }
    val cameraInteraction = remember { MutableInteractionSource() }
    val homeScroll = rememberScrollState()
    var showAccess by rememberSaveable { mutableStateOf(false) }
    var departing by remember { mutableStateOf(false) }
    var awaitingReader by remember { mutableStateOf(false) }
    var departureVersion by remember { mutableIntStateOf(0) }
    var greetingVersion by remember { mutableIntStateOf(0) }
    var cameraLaunching by remember { mutableStateOf(false) }
    var cameraPlaybackStarted by remember { mutableStateOf(false) }
    var cameraPlaybackVersion by remember { mutableIntStateOf(0) }
    var flashOrigin by remember { mutableStateOf(Offset.Zero) }
    var homeCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var cameraMascotCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val flashProgress = remember { Animatable(0f) }
    val launchCamera by rememberUpdatedState {
        if (chooser) onStartTutorialReader(TutorialPath.CAMERA) else onNavigateToCamera()
    }
    DisposableEffect(tutorialActive, focus?.step) {
        onDispose { departing = false; awaitingReader = false; cameraLaunching = false }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) cameraLaunching = false
            if (event == Lifecycle.Event.ON_STOP) {
                departing = false
                awaitingReader = false
                cameraLaunching = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(cameraLaunching, cameraPlaybackStarted, animationsEnabled) {
        if (cameraLaunching && (cameraPlaybackStarted || !animationsEnabled)) {
            if (animationsEnabled) {
                delay(KanakoUiProfile.cameraFlashDelayMillis)
                flashProgress.animateTo(
                    1f,
                    tween(KanakoUiProfile.cameraFlashDuration, easing = LinearEasing)
                )
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) launchCamera()
            cameraLaunching = false
        }
        flashProgress.snapTo(0f)
    }
    LaunchedEffect(awaitingReader) {
        if (awaitingReader) {
            delay(5_000L)
            awaitingReader = false
        }
    }
    val mascotSize = when (preferences.fabSize) {
        FabSize.COMPACT -> 48.dp
        FabSize.MEDIUM -> 56.dp
        FabSize.LARGE -> 68.dp
    }
    val readingActionModifier = Modifier.fillMaxWidth()
        .heightIn(min = KanakoUiProfile.homeActionHeight)
    val accessoryColor = if (LocalKanakoDarkTheme.current) DarkWarmCreamSurface else WarmCreamSurface
    val ready = supported && overlayGranted && accessibilityEnabled && accessibilityConnected &&
        preferences.hasAcceptedSnapshotDisclosure
    LaunchedEffect(ready, preferences.isOverlayActive) {
        if (ready) showAccess = false
        if (!ready || preferences.isOverlayActive) {
            departing = false
            awaitingReader = false
        }
    }
    LaunchedEffect(focus?.step, focus?.allowed, ready) {
        if (focus != null) {
            if (focus.step == -1 && "camera_card" !in focus.allowed) showAccess = !ready
            androidx.compose.runtime.withFrameNanos { }
            if ("camera_card" in focus.allowed && "floating_card" !in focus.allowed) {
                homeScroll.scrollTo(homeScroll.maxValue)
            } else homeScroll.scrollTo(0)
        }
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { homeCoordinates = it }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            BoxWithConstraints(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val contentMinHeight = (maxHeight - KanakoUiProfile.homePadding * 2).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                        .verticalScroll(homeScroll)
                        .padding(KanakoUiProfile.homePadding)
                        .heightIn(min = contentMinHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.homeCardGap)) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val density = LocalDensity.current
                            val pixelLimit = (KanakoUiProfile.homeHeroSourcePixels / density.density).dp
                            val widthLimit = (maxWidth - KanakoUiProfile.homeHeroTextMinWidth * density.fontScale - 72.dp)
                                .coerceAtLeast(mascotSize)
                            val heroSize = minOf(mascotSize * KanakoUiProfile.homeHeroMaxScale, pixelLimit, widthLimit)
                                .coerceAtLeast(mascotSize)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val greetingDescription = stringResource(R.string.replay_mascot_greeting)
                                IconButton(
                                    onClick = { greetingVersion++ },
                                    enabled = focus == null,
                                    modifier = Modifier.size(heroSize).semantics {
                                        contentDescription = greetingDescription
                                    }
                                ) {
                                    AnimatedMascot(
                                        animationRes = R.drawable.kanako_welcome_wave,
                                        size = heroSize,
                                        staticRes = R.drawable.kanako_mascot,
                                        playbackKey = greetingVersion,
                                        animationsEnabled = animationsEnabled,
                                        holdLastFrame = true
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            stringResource(R.string.screen_reader_title),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                    }
                                    IconButton(
                                        onClick = onNavigateToPreferences,
                                        enabled = tutorialAllows("preferences"),
                                        modifier = Modifier.size(48.dp)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .tutorialTarget("preferences")
                                    ) {
                                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.preferences_title))
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth()) {
                            Card(modifier = Modifier.tutorialTarget("floating_card", hideWhenDimmed = false)
                                .then(if (chooser) Modifier.clickable(interactionSource = floatingInteraction,
                                    indication = LocalIndication.current, enabled = tutorialAllows("floating_card") && supported) {
                                    onSelectTutorialReader(TutorialPath.FLOATING)
                                } else Modifier),
                                interactionSource = if (chooser) floatingInteraction else null,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Box(Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().animateContentSize(
                                        animationSpec = if (animationsEnabled) tween(KanakoUiProfile.expandDuration) else snap()
                                    ).padding(KanakoUiProfile.homeCardInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                val readerState = if (preferences.isOverlayActive)
                                                    stringResource(R.string.home_reader_active) else null
                                                Text(
                                                    stringResource(R.string.home_reader_off),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.semantics {
                                                        heading()
                                                        if (readerState != null) stateDescription = readerState
                                                    }
                                                )
                                                Text(stringResource(R.string.home_floating_summary),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box(Modifier.size(mascotSize)) {
                                                if (!preferences.isOverlayActive && !awaitingReader) {
                                                    AnimatedMascot(
                                                        animationRes = if (departing) R.drawable.kanako_peek_button else null,
                                                        size = mascotSize,
                                                        durationMillis = 700,
                                                        playbackKey = departureVersion,
                                                        animationsEnabled = animationsEnabled,
                                                        holdLastFrame = true,
                                                        onFinished = {
                                                            if (departing && ready && !preferences.isOverlayActive &&
                                                                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                                            ) {
                                                                departing = false
                                                                awaitingReader = true
                                                                onUpdateOverlayActive(true)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (ready && !showAccess) {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                itemVerticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AccessStatusBadge(stringResource(R.string.setup_ready), granted = true)
                                                TextButton(onClick = { showAccess = true }, enabled = focus == null,
                                                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End).width(IntrinsicSize.Max)) {
                                                    Text(stringResource(R.string.home_manage_access), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else if (!supported) {
                                            Text(stringResource(R.string.setup_unsupported))
                                        } else if (!showAccess) {
                                            Surface(color = accessoryColor, contentColor = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape) {
                                                Row(
                                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Text(stringResource(R.string.home_setup_summary),
                                                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            OutlinedButton(onClick = { showAccess = true }, enabled = tutorialAllows("setup_access"),
                                                modifier = Modifier.tutorialTarget("setup_access")) {
                                                Text(stringResource(R.string.home_setup_action), fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            PermissionRequirement(
                                                title = stringResource(R.string.setup_overlay_title),
                                                description = stringResource(R.string.setup_overlay_description),
                                                granted = overlayGranted,
                                                action = stringResource(R.string.setup_open_overlay),
                                                onAction = onRequestOverlay,
                                                enabled = tutorialAllows("overlay_access"),
                                                actionModifier = Modifier.tutorialTarget("overlay_access")
                                            )
                                            PermissionRequirement(
                                                title = stringResource(R.string.setup_accessibility_title),
                                                description = stringResource(if (accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure && !accessibilityConnected)
                                                    R.string.setup_accessibility_reconnect else R.string.setup_accessibility_description),
                                                status = stringResource(when {
                                                    !accessibilityEnabled -> R.string.setup_required
                                                    !preferences.hasAcceptedSnapshotDisclosure -> R.string.setup_disclosure_needed
                                                    !accessibilityConnected -> R.string.setup_accessibility_disconnected
                                                    else -> R.string.setup_ready
                                                }),
                                                granted = accessibilityEnabled && accessibilityConnected && preferences.hasAcceptedSnapshotDisclosure,
                                                action = stringResource(
                                                    if (accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure)
                                                        R.string.setup_manage_accessibility else R.string.setup_open_accessibility
                                                ),
                                                onAction = onRequestAccessibility,
                                                enabled = tutorialAllows("screen_access"),
                                                actionModifier = Modifier.tutorialTarget("screen_access"),
                                                connecting = accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure && !accessibilityConnected
                                            )
                                            if (ready) {
                                                TextButton(onClick = { showAccess = false }, enabled = focus == null) {
                                                    Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (accessibilityEnabled && !accessibilityConnected) {
                                                Text(stringResource(R.string.setup_connecting), style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                if (chooser) onStartTutorialReader(TutorialPath.FLOATING)
                                                else if (preferences.isOverlayActive) onUpdateOverlayActive(false)
                                                else if (!departing && !awaitingReader && ready) {
                                                    departureVersion++
                                                    departing = true
                                                }
                                            },
                                            enabled = tutorialAllows("floating_card", "start_reader") &&
                                                (if (chooser) supported else preferences.isOverlayActive || (ready && !departing && !awaitingReader)),
                                            modifier = Modifier.align(Alignment.CenterHorizontally).then(readingActionModifier)
                                                .tutorialTarget("start_reader", parent = "floating_card"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(
                                                horizontal = KanakoUiProfile.homeActionHorizontalPadding,
                                                vertical = KanakoUiProfile.homeActionVerticalPadding
                                            )
                                        ) {
                                            AnimatedContent(
                                                targetState = preferences.isOverlayActive,
                                                transitionSpec = {
                                                    val duration = if (animationsEnabled) KanakoUiProfile.contentDuration else 0
                                                    (fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))).using(
                                                        SizeTransform { _, _ ->
                                                            if (animationsEnabled) KanakoUiProfile.releaseSpec() else snap()
                                                        }
                                                    )
                                                },
                                                label = "Reader action"
                                            ) { active ->
                                                Text(
                                                    stringResource(if (active) R.string.setup_stop else R.string.setup_enable),
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        if (preferences.isOverlayActive) {
                                            Text(stringResource(R.string.home_next_step),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth()) {
                            Card(modifier = Modifier.tutorialTarget("camera_card", hideWhenDimmed = false)
                                .then(if (chooser) Modifier.clickable(interactionSource = cameraInteraction,
                                    indication = LocalIndication.current, enabled = tutorialAllows("camera_card")) {
                                    onSelectTutorialReader(TutorialPath.CAMERA)
                                } else Modifier),
                                interactionSource = if (chooser) cameraInteraction else null,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Box(Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(KanakoUiProfile.homeCardInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(stringResource(R.string.camera_title), style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                                Text(stringResource(R.string.home_camera_summary), style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            AnimatedMascot(
                                                animationRes = if (cameraLaunching) R.drawable.kanako_camera_flash else null,
                                                staticRes = R.drawable.kanako_camera_first_frame,
                                                size = mascotSize,
                                                modifier = Modifier.onGloballyPositioned { cameraMascotCoordinates = it },
                                                playbackKey = cameraPlaybackVersion,
                                                animationsEnabled = animationsEnabled,
                                                onPlaybackStarted = { if (cameraLaunching) cameraPlaybackStarted = true },
                                                onFinished = { if (cameraLaunching) cameraPlaybackStarted = true }
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val home = homeCoordinates
                                                val mascot = cameraMascotCoordinates
                                                flashOrigin = if (home != null && mascot != null && home.isAttached && mascot.isAttached) {
                                                    home.localPositionOf(mascot, Offset(mascot.size.width / 2f, mascot.size.height / 2f))
                                                } else Offset.Zero
                                                cameraPlaybackStarted = false
                                                cameraPlaybackVersion++
                                                cameraLaunching = true
                                            },
                                            enabled = tutorialAllows("camera_card") && !cameraLaunching,
                                            modifier = Modifier.align(Alignment.CenterHorizontally).then(readingActionModifier)
                                                .tutorialTarget("camera_open", parent = "camera_card"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(
                                                horizontal = KanakoUiProfile.homeActionHorizontalPadding,
                                                vertical = KanakoUiProfile.homeActionVerticalPadding
                                            )
                                        ) {
                                            Text(
                                                stringResource(R.string.camera_open),
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showTutorialInvitation,
                            enter = fadeIn(tween(if (animationsEnabled) KanakoUiProfile.contentDuration else 0)) +
                                expandVertically(tween(if (animationsEnabled) KanakoUiProfile.expandDuration else 0)),
                            exit = fadeOut(tween(if (animationsEnabled) KanakoUiProfile.contentDuration else 0)) +
                                shrinkVertically(tween(if (animationsEnabled) KanakoUiProfile.expandDuration else 0))
                        ) {
                            TutorialInvitation(
                                onStart = onNavigateToTutorial,
                                onDismiss = onDismissTutorialInvitation,
                                enabled = showTutorialInvitation
                            )
                        }
                    }
                    TextButton(
                        onClick = onNavigateToHowItWorks,
                        enabled = focus == null,
                        modifier = Modifier.padding(top = KanakoUiProfile.homeCardGap)
                            .heightIn(min = 48.dp).background(accessoryColor, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Help, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.how_it_works), modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (cameraLaunching) {
            CameraLaunchFlash(
                origin = flashOrigin,
                progress = { flashProgress.value },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun PermissionRequirement(
    title: String,
    description: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    connecting: Boolean = false,
    enabled: Boolean = true,
    actionModifier: Modifier = Modifier,
    status: String? = null
) {
    val statusText = status ?: stringResource(when {
        connecting -> R.string.setup_allowed_connecting
        granted -> R.string.setup_granted
        else -> R.string.setup_required
    })
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall)
        AccessStatusBadge(statusText, granted = granted, connecting = connecting)
        OutlinedButton(onClick = onAction, enabled = enabled, modifier = actionModifier) { Text(action, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AccessStatusBadge(
    text: String,
    granted: Boolean,
    modifier: Modifier = Modifier,
    connecting: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = if (granted) MaterialTheme.colorScheme.secondaryContainer
            else if (LocalKanakoDarkTheme.current) DarkWarmCreamSurface else WarmCreamSurface,
        contentColor = if (granted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    connecting -> Icons.Rounded.HourglassTop
                    granted -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Japanese", locale = "ja")
@Composable
fun HomeScreenPreview() {
    KanakoTheme { HomeScreen({}, {}, {}) }
}

@Preview(showBackground = true)
@Preview(name = "Japanese", locale = "ja")
@Composable
private fun HomePartialAccessPreview() {
    KanakoTheme { HomeScreen({}, {}, {}, overlayGranted = true) }
}

@Preview(showBackground = true)
@Composable
private fun HomeAccessConnectingPreview() {
    KanakoTheme {
        HomeScreen({}, {}, {}, preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
            overlayGranted = true, accessibilityEnabled = true)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeActiveReaderPreview() {
    KanakoTheme {
        HomeScreen({}, {}, {},
            preferences = KanakoPreferences(isOverlayActive = true, hasAcceptedSnapshotDisclosure = true),
            overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true)
    }
}

@Preview(showBackground = true)
@Preview(name = "Japanese", locale = "ja")
@Preview(name = "Compact large text", widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun HomeScreenDarkPreview() {
    KanakoTheme(darkTheme = true) {
        HomeScreen({}, {}, {}, preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
            overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true)
    }
}

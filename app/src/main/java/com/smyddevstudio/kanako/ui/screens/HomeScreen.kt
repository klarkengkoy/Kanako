package com.smyddevstudio.kanako.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.smyddevstudio.kanako.ui.components.GuidedSpotlight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.ui.components.AnimatedMascot
import com.smyddevstudio.kanako.ui.components.rememberAnimationsEnabled
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHowItWorks: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onNavigateToGallery: () -> Unit = {},
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
    guidedSetup: Boolean = false,
    guidePreferences: Boolean = false,
    onSkipGuidedSetup: () -> Unit = {}
) {
    var showAccess by rememberSaveable { mutableStateOf(guidedSetup) }
    val guideTarget = if (guidePreferences) 3 else if (!overlayGranted) 0 else if (!accessibilityEnabled ||
        !accessibilityConnected || !preferences.hasAcceptedSnapshotDisclosure) 1 else 2
    var targetBounds by remember(guideTarget) { mutableStateOf<Rect?>(null) }
    val bringTarget = remember { BringIntoViewRequester() }
    val targetModifier = if (guidedSetup) Modifier.bringIntoViewRequester(bringTarget)
        .onGloballyPositioned { targetBounds = it.boundsInRoot() } else Modifier
    LaunchedEffect(guidedSetup, guideTarget) {
        if (guidedSetup) {
            showAccess = guideTarget in 0..1
            delay(100)
            bringTarget.bringIntoView()
        }
    }
    var departing by remember { mutableStateOf(false) }
    var awaitingReader by remember { mutableStateOf(false) }
    var departureVersion by remember { mutableIntStateOf(0) }
    var greetingVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(guidedSetup) {
        onDispose { departing = false; awaitingReader = false }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                departing = false
                awaitingReader = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
    val readingActionModifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().heightIn(min = 48.dp)
    val ready = supported && overlayGranted && accessibilityEnabled && accessibilityConnected &&
        preferences.hasAcceptedSnapshotDisclosure
    LaunchedEffect(ready, preferences.isOverlayActive) {
        if (ready) showAccess = false
        if (!ready || preferences.isOverlayActive) {
            departing = false
            awaitingReader = false
        }
    }
    Box(modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { if (!guidedSetup || guidePreferences) onNavigateToPreferences() },
                        enabled = !guidedSetup || guidePreferences,
                        modifier = if (guidePreferences) targetModifier else Modifier) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.preferences_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { if (!guidedSetup) greetingVersion++ },
                        enabled = !guidedSetup,
                        modifier = Modifier.size(mascotSize)
                    ) {
                        AnimatedMascot(
                            animationRes = R.drawable.kanako_welcome_wave,
                            size = mascotSize,
                            staticRes = R.drawable.kanako_mascot,
                            playbackKey = greetingVersion,
                            contentDescription = stringResource(R.string.replay_mascot_greeting),
                            animationsEnabled = animationsEnabled,
                            holdLastFrame = true
                        )
                    }
                    Text(
                        stringResource(R.string.screen_reader_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                }
                Text(
                    stringResource(R.string.screen_reader_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(if (preferences.isOverlayActive) R.string.home_reader_active else R.string.home_reader_off),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(stringResource(R.string.home_floating_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ready && !showAccess) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                AccessStatusBadge(stringResource(R.string.setup_ready), granted = true)
                                TextButton(onClick = { showAccess = true }, enabled = !guidedSetup,
                                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End).width(IntrinsicSize.Max)) {
                                    Text(stringResource(R.string.home_manage_access))
                                }
                            }
                        } else if (!supported) {
                            Text(stringResource(R.string.setup_unsupported))
                        } else if (!showAccess) {
                            Text(stringResource(R.string.home_setup_summary), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { showAccess = true }) {
                                Text(stringResource(R.string.home_setup_action))
                            }
                        } else {
                            PermissionRequirement(
                                title = stringResource(R.string.setup_overlay_title),
                                description = stringResource(R.string.setup_overlay_description),
                                granted = overlayGranted,
                                action = stringResource(R.string.setup_open_overlay),
                                onAction = { if (!guidedSetup || guideTarget == 0) onRequestOverlay() },
                                actionModifier = if (guideTarget == 0) targetModifier else Modifier,
                                enabled = !guidedSetup || guideTarget == 0
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
                                onAction = { if (!guidedSetup || guideTarget == 1) onRequestAccessibility() },
                                actionModifier = if (guideTarget == 1) targetModifier else Modifier,
                                enabled = !guidedSetup || guideTarget == 1,
                                connecting = accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure && !accessibilityConnected
                            )
                            if (ready) {
                                TextButton(onClick = { showAccess = false }, enabled = !guidedSetup) {
                                    Text(stringResource(R.string.done))
                                }
                            }
                            if (accessibilityEnabled && !accessibilityConnected) {
                                Text(stringResource(R.string.setup_connecting), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = {
                                if (guidedSetup && guideTarget != 2) Unit
                                else if (preferences.isOverlayActive) onUpdateOverlayActive(false)
                                else if (!departing && !awaitingReader && ready) {
                                    departureVersion++
                                    departing = true
                                }
                            },
                            enabled = (!guidedSetup || !guidePreferences) && (preferences.isOverlayActive || (ready && !departing && !awaitingReader)),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp).then(readingActionModifier)
                                .then(if (guideTarget == 2) targetModifier else Modifier),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (preferences.isOverlayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (preferences.isOverlayActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            if (!preferences.isOverlayActive) {
                                AnimatedMascot(
                                    animationRes = if (departing || awaitingReader) R.drawable.kanako_peek_button else null,
                                    size = 24.dp,
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
                                Spacer(Modifier.width(8.dp))
                            }
                            AnimatedContent(targetState = preferences.isOverlayActive, label = "Reader action") { active ->
                                Text(stringResource(if (active) R.string.setup_stop else R.string.setup_enable))
                            }
                        }
                        if (preferences.isOverlayActive) {
                            Text(stringResource(R.string.home_next_step),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.camera_title), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.home_camera_summary), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { if (!guidedSetup) onNavigateToCamera() },
                            enabled = !guidedSetup,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp).then(readingActionModifier),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(
                                stringResource(R.string.camera_open),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { if (!guidedSetup) onNavigateToGallery() },
                    enabled = !guidedSetup,
                    modifier = Modifier.align(Alignment.CenterHorizontally).then(readingActionModifier)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.gallery_title), Modifier.padding(start = 8.dp))
                }
                if (showTutorialInvitation) {
                    TutorialInvitation(onStart = onNavigateToTutorial, onDismiss = onDismissTutorialInvitation)
                }
                TextButton(onClick = { if (!guidedSetup) onNavigateToHowItWorks() }, enabled = !guidedSetup, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.how_it_works))
                }
            }
        }
    }
    if (guidedSetup) GuidedSpotlight(targetBounds, stringResource(when (guideTarget) {
        0 -> R.string.guided_overlay
        1 -> R.string.guided_accessibility
        3 -> R.string.guided_preferences
        else -> R.string.guided_show
    }), onSkipGuidedSetup)
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
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall)
        AccessStatusBadge(statusText, granted = granted, connecting = connecting)
        OutlinedButton(onClick = onAction, enabled = enabled, modifier = actionModifier) { Text(action) }
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
        color = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (granted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(text, style = MaterialTheme.typography.labelMedium)
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

@Preview(name = "Guided overlay access", widthDp = 360, heightDp = 800)
@Preview(name = "Guided compact", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun GuidedHomePreview() {
    KanakoTheme { HomeScreen({}, {}, {}, guidedSetup = true) }
}

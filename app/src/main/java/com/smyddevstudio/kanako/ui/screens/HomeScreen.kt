package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.CreamBackgroundDark
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.PrimaryCrimson

@Composable
fun MushroomLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    capColor: Color = PrimaryCrimson,
    stemColor: Color = CreamBackgroundDark
) {
    Canvas(modifier = modifier.size(size)) {
        val width = size.toPx()
        val height = size.toPx()

        // Draw Stem
        val stemPath = Path().apply {
            moveTo(width * 0.38f, height * 0.5f)
            lineTo(width * 0.38f, height * 0.88f)
            cubicTo(width * 0.38f, height * 0.96f, width * 0.62f, height * 0.96f, width * 0.62f, height * 0.88f)
            lineTo(width * 0.62f, height * 0.5f)
            close()
        }
        drawPath(stemPath, color = stemColor)

        // Draw Cap
        val capPath = Path().apply {
            moveTo(width * 0.1f, height * 0.55f)
            cubicTo(width * 0.1f, height * 0.12f, width * 0.9f, height * 0.12f, width * 0.9f, height * 0.55f)
            cubicTo(width * 0.9f, height * 0.60f, width * 0.1f, height * 0.60f, width * 0.1f, height * 0.55f)
            close()
        }
        drawPath(capPath, color = capColor)

        // Draw Spots
        drawCircle(Color.White.copy(alpha = 0.9f), radius = width * 0.08f, center = Offset(width * 0.5f, height * 0.32f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = width * 0.06f, center = Offset(width * 0.3f, height * 0.42f))
        drawCircle(Color.White.copy(alpha = 0.9f), radius = width * 0.06f, center = Offset(width * 0.7f, height * 0.42f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onRequestAccessibility: () -> Unit = {}
) {
    var showAccess by rememberSaveable { mutableStateOf(false) }
    val readingActionModifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().heightIn(min = 48.dp)
    val ready = supported && overlayGranted && accessibilityEnabled && accessibilityConnected &&
        preferences.hasAcceptedSnapshotDisclosure
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = onNavigateToPreferences) {
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
                    Image(
                        painter = painterResource(R.drawable.kanako_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        stringResource(R.string.screen_reader_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    stringResource(R.string.screen_reader_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(if (preferences.isOverlayActive) R.string.home_reader_active else R.string.home_reader_off),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (ready && !showAccess) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                AccessStatusBadge(stringResource(R.string.setup_ready), granted = true)
                                TextButton(onClick = { showAccess = true },
                                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End).width(IntrinsicSize.Max)) {
                                    Text(stringResource(R.string.home_manage_access))
                                }
                            }
                        } else if (!supported) {
                            Text(stringResource(R.string.setup_unsupported))
                        } else {
                            PermissionRequirement(
                                title = stringResource(R.string.setup_overlay_title),
                                description = stringResource(R.string.setup_overlay_description),
                                granted = overlayGranted,
                                action = stringResource(R.string.setup_open_overlay),
                                onAction = onRequestOverlay
                            )
                            PermissionRequirement(
                                title = stringResource(R.string.setup_accessibility_title),
                                description = stringResource(R.string.setup_accessibility_description),
                                granted = accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure,
                                action = stringResource(
                                    if (accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure)
                                        R.string.setup_manage_accessibility else R.string.setup_open_accessibility
                                ),
                                onAction = onRequestAccessibility,
                                connecting = accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure && !accessibilityConnected
                            )
                            if (ready) {
                                TextButton(onClick = { showAccess = false }) {
                                    Text(stringResource(R.string.done))
                                }
                            }
                            if (accessibilityEnabled && !accessibilityConnected) {
                                Text(stringResource(R.string.setup_connecting), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = { onUpdateOverlayActive(!preferences.isOverlayActive) },
                            enabled = preferences.isOverlayActive || ready,
                            modifier = Modifier.align(Alignment.CenterHorizontally).then(readingActionModifier),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (preferences.isOverlayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (preferences.isOverlayActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            // TODO: Animate the floating mascot icon for the Show and Stop reader actions.
                            Image(painterResource(R.drawable.kanako_floating), contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (preferences.isOverlayActive) R.string.setup_stop else R.string.setup_enable))
                        }
                    }
                }
                Button(
                    onClick = onNavigateToCamera,
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
                TextButton(onClick = onNavigateToHowItWorks, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.how_it_works))
                }
            }
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
    connecting: Boolean = false
) {
    val status = stringResource(when {
        connecting -> R.string.setup_allowed_connecting
        granted -> R.string.setup_granted
        else -> R.string.setup_required
    })
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall)
        AccessStatusBadge(status, granted = granted, connecting = connecting)
        OutlinedButton(onClick = onAction) { Text(action) }
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

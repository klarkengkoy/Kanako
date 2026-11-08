package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.LaunchedEffect
import com.smyddevstudio.kanako.ui.components.LocalTutorialFocus
import com.smyddevstudio.kanako.ui.components.tutorialTarget
import com.smyddevstudio.kanako.ui.components.tutorialAllows
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.smyddevstudio.kanako.R
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.ReadingPreviewCard
import com.smyddevstudio.kanako.ui.components.labelRes
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onUpdateFuriganaSize: (FuriganaSize) -> Unit = {},
    onUpdateFuriganaColor: (FuriganaColor) -> Unit = {},
    onUpdateScriptType: (ScriptType) -> Unit = {},
    onUpdateAnnotationPlateStyle: (AnnotationPlateStyle) -> Unit = {},
    onUpdateFabSize: (FabSize) -> Unit = {},
    onUpdateAutoHideOnKeyboard: (Boolean) -> Unit = {},
    onRequestAccessibility: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {}
) {
    val tutorial = LocalTutorialFocus.current
    val listState = rememberLazyListState()
    LaunchedEffect(tutorial?.step) {
        if (tutorial?.step == 4) listState.scrollToItem(9)
        else if (tutorial?.step == 3) listState.scrollToItem(0)
    }
    var showTileGuide by rememberSaveable { mutableStateOf(false) }

    if (showTileGuide) {
        AlertDialog(
            onDismissRequest = { showTileGuide = false },
            title = { Text(stringResource(R.string.quick_settings_guide_title)) },
            text = { Text(stringResource(R.string.quick_settings_guide_body), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { showTileGuide = false }) { Text(stringResource(R.string.done)) }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.preferences_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = tutorial == null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            val windowSize = DpSize(maxWidth, maxHeight)
            val wide = maxWidth >= 840.dp
            val compactPreview = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.3f
            PreferencesLayout(
                wide = wide,
                modifier = Modifier.widthIn(max = if (wide) 1120.dp else 720.dp)
                    .fillMaxSize().padding(horizontal = 16.dp)
            ) {
                ReadingPreviewCard(
                    preferences = preferences,
                    compact = compactPreview,
                    modifier = Modifier.tutorialTarget("appearance_preview")
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.tutorialTarget("preferences_scroll", hideWhenDimmed = false),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "appearance_header") {
                        PreferenceSectionHeader(title = stringResource(R.string.preferences_reading_appearance), icon = Icons.Rounded.FontDownload)
                        Text(stringResource(R.string.preferences_appearance_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }

                    item(key = "script_type") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_script), modifier = Modifier.tutorialTarget("appearance_script", clipTo = "preferences_scroll")) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScriptType.entries.forEach { script ->
                                    PreferenceChoiceChip(
                                        enabled = tutorialAllows("appearance_script"),
                                        modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                        selected = preferences.scriptType == script,
                                        onClick = { onUpdateScriptType(script) },
                                        label = stringResource(script.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "furigana_size") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_size), modifier = Modifier.tutorialTarget("appearance_size", clipTo = "preferences_scroll")) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FuriganaSize.entries.forEach { size ->
                                    PreferenceChoiceChip(
                                        enabled = tutorialAllows("appearance_size"),
                                        modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                        selected = preferences.furiganaSize == size,
                                        onClick = { onUpdateFuriganaSize(size) },
                                        label = stringResource(size.labelRes)
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.preferences_photo_size_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    item(key = "furigana_color") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_color), modifier = Modifier.tutorialTarget("appearance_color", clipTo = "preferences_scroll")) {
                            FlowRow(
                                modifier = Modifier.selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FuriganaColor.entries.forEach { color ->
                                    PreferenceChoiceChip(
                                        enabled = tutorialAllows("appearance_color"),
                                        modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                        selected = preferences.furiganaColor == color,
                                        onClick = { onUpdateFuriganaColor(color) },
                                        label = stringResource(color.labelRes),
                                        swatch = Color(color.colorHex)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "plate_style") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_plate), modifier = Modifier.tutorialTarget("appearance_plate", clipTo = "preferences_scroll")) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnnotationPlateStyle.entries.forEach { style ->
                                    PreferenceChoiceChip(
                                        enabled = tutorialAllows("appearance_plate"),
                                        modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                        selected = preferences.annotationPlateStyle == style,
                                        onClick = { onUpdateAnnotationPlateStyle(style) },
                                        label = stringResource(style.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "floating_header") {
                        PreferenceSectionHeader(title = stringResource(R.string.preferences_floating_control), icon = Icons.Rounded.Layers)
                    }

                    item(key = "fab_size") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_control_size), modifier = Modifier.tutorialTarget("button_size", clipTo = "preferences_scroll")) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FabSize.entries.forEach { size ->
                                    PreferenceChoiceChip(
                                        enabled = tutorialAllows("button_size"),
                                        modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                        selected = preferences.fabSize == size,
                                        onClick = { onUpdateFabSize(size) },
                                        label = stringResource(size.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "keyboard_hide") {
                        Box(Modifier.tutorialTarget("keyboard")) {
                        PreferenceToggleCard(
                            modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                            title = stringResource(R.string.preferences_keyboard),
                            subtitle = stringResource(R.string.preferences_keyboard_check),
                            checked = preferences.autoHideOnKeyboard,
                            onCheckedChange = { onUpdateAutoHideOnKeyboard(it) },
                            enabled = tutorialAllows("keyboard")
                        )
                        }
                    }

                    item(key = "keyboard_hint") {
                        Text(
                            text = stringResource(R.string.preferences_keyboard_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    item(key = "tile_guide") {
                        Card(
                            modifier = Modifier.fillMaxWidth().tutorialTarget("tile_guide", clipTo = "preferences_scroll"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.tile_title),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )

                                    }
                                    Text(
                                        text = stringResource(R.string.tile_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize),
                                    enabled = tutorial == null,
                                    onClick = { showTileGuide = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.guide))
                                }
                            }
                        }
                    }

                    item(key = "system_header") {
                        PreferenceSectionHeader(title = stringResource(R.string.preferences_system), icon = Icons.Rounded.Settings)
                    }

                    item(key = "accessibility") {
                        OutlinedButton(
                            onClick = onRequestAccessibility,
                            enabled = tutorial == null,
                            modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize).fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(stringResource(R.string.home_manage_access)) }
                    }

                    item(key = "privacy") {
                        TextButton(onClick = onNavigateToPrivacy, enabled = tutorial == null,
                            modifier = Modifier.keepFocusedControlVisibleOnResize(windowSize).fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.privacy_access))
                        }
                    }

                    item(key = "bottom_space") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesLayout(
    wide: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = 12.dp.roundToPx()
        val previewWidth = if (wide) 320.dp.roundToPx() else width
        val preview = measurables[0].measure(Constraints(
            minWidth = previewWidth, maxWidth = previewWidth,
            maxHeight = if (wide) height else (height * 0.45f).roundToInt()
        ))
        val spacing = if (wide) gap else minOf(gap, height - preview.height)
        val controls = measurables[1].measure(Constraints.fixed(
            width = if (wide) (width - preview.width - spacing).coerceAtLeast(0) else width,
            height = if (wide) height else (height - preview.height - spacing).coerceAtLeast(0)
        ))
        layout(width, height) {
            preview.placeRelative(0, 0)
            controls.placeRelative(
                x = if (wide) preview.width + spacing else 0,
                y = if (wide) 0 else preview.height + spacing
            )
        }

    }
}

@Composable
private fun Modifier.keepFocusedControlVisibleOnResize(windowSize: DpSize): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val inputMode = LocalInputModeManager.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(windowSize) {
        if (focused && inputMode.inputMode == InputMode.Keyboard) {
            // Wait for text reflow and the new list viewport to be placed.
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    return this.bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

@Composable
private fun PreferenceChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp).semantics {
            role = Role.RadioButton
        },
        border = null,
        leadingIcon = if (swatch != null) {
            { Surface(Modifier.size(16.dp), shape = CircleShape, color = swatch) {} }
        } else null,
        trailingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondary,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondary
        )
    )
}

@Composable
private fun PreferenceSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PreferenceControlCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PreferenceToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth().toggleable(
            value = checked, role = Role.Switch, enabled = enabled, onValueChange = onCheckedChange
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                    checkedTrackColor = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Compact", widthDp = 320, heightDp = 480, fontScale = 1.3f)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Japanese", locale = "ja")
@Composable
fun PreferencesScreenPreview() {
    KanakoTheme {
        PreferencesScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferencesScreenDarkPreview() {
    KanakoTheme(darkTheme = true) { PreferencesScreen(onBack = {}) }
}

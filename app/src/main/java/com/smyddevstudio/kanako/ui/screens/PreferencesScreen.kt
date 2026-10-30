package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
    onUpdateTouchPassthrough: (Boolean) -> Unit = {},
    onUpdateFabSize: (FabSize) -> Unit = {},
    onUpdateAutoHideOnKeyboard: (Boolean) -> Unit = {},
    onUpdateAutoReadAfterScroll: (Boolean) -> Unit = {},
    onRequestAccessibility: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {}
) {
    var showTileGuide by rememberSaveable { mutableStateOf(false) }
    var showAutoReadDisclosure by rememberSaveable { mutableStateOf(false) }

    if (showAutoReadDisclosure) {
        com.smyddevstudio.kanako.ui.components.AutoReadDisclosure(
            onConfirm = { showAutoReadDisclosure = false; onUpdateAutoReadAfterScroll(true) },
            onDismiss = { showAutoReadDisclosure = false }
        )
    }

    if (showTileGuide) {
        AlertDialog(
            onDismissRequest = { showTileGuide = false },
            title = { Text(stringResource(R.string.quick_settings_guide_title)) },
            text = { Text(stringResource(R.string.quick_settings_guide_body)) },
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
                    IconButton(onClick = onBack) {
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
            val previewMaxHeight = maxHeight * 0.45f
            val compactPreview = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.3f
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReadingPreviewCard(
                    preferences = preferences,
                    compact = compactPreview,
                    modifier = Modifier.heightIn(max = previewMaxHeight)
                )
                LazyColumn(
                    Modifier.weight(1f),
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
                        PreferenceControlCard(title = stringResource(R.string.preferences_script)) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScriptType.entries.forEach { script ->
                                    PreferenceChoiceChip(
                                        selected = preferences.scriptType == script,
                                        onClick = { onUpdateScriptType(script) },
                                        label = stringResource(script.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "furigana_size") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_size)) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FuriganaSize.entries.forEach { size ->
                                    PreferenceChoiceChip(
                                        selected = preferences.furiganaSize == size,
                                        onClick = { onUpdateFuriganaSize(size) },
                                        label = stringResource(size.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "furigana_color") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_color)) {
                            FlowRow(
                                modifier = Modifier.selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FuriganaColor.entries.forEach { color ->
                                    PreferenceChoiceChip(
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
                        PreferenceControlCard(title = stringResource(R.string.preferences_plate)) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnnotationPlateStyle.entries.forEach { style ->
                                    PreferenceChoiceChip(
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

                    item(key = "touch_passthrough") {
                        PreferenceToggleCard(
                            title = stringResource(R.string.preferences_touch),
                            subtitle = stringResource(R.string.preferences_touch_check),
                            checked = preferences.touchPassthrough,
                            onCheckedChange = { onUpdateTouchPassthrough(it) }
                        )
                    }

                    item(key = "fab_size") {
                        PreferenceControlCard(title = stringResource(R.string.preferences_control_size)) {
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FabSize.entries.forEach { size ->
                                    PreferenceChoiceChip(
                                        selected = preferences.fabSize == size,
                                        onClick = { onUpdateFabSize(size) },
                                        label = stringResource(size.labelRes)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "auto_read") {
                        PreferenceControlCard(title = stringResource(R.string.reading_mode)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PreferenceChoiceChip(
                                    selected = preferences.autoReadAfterScroll,
                                    onClick = { if (!preferences.autoReadAfterScroll) showAutoReadDisclosure = true },
                                    label = stringResource(R.string.auto_read_title)
                                )
                                PreferenceChoiceChip(
                                    selected = !preferences.autoReadAfterScroll,
                                    onClick = { onUpdateAutoReadAfterScroll(false) },
                                    label = stringResource(R.string.tap_to_read)
                                )
                            }
                            Text(
                                stringResource(R.string.auto_read_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item(key = "keyboard_hide") {
                        PreferenceToggleCard(
                            title = stringResource(R.string.preferences_keyboard),
                            subtitle = stringResource(R.string.preferences_keyboard_check),
                            checked = preferences.autoHideOnKeyboard,
                            onCheckedChange = { onUpdateAutoHideOnKeyboard(it) }
                        )
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
                            modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) { Text(stringResource(R.string.home_manage_access)) }
                    }

                    item(key = "privacy") {
                        TextButton(onClick = onNavigateToPrivacy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
private fun PreferenceChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    swatch: Color? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.heightIn(min = 48.dp).clearAndSetSemantics {
            role = Role.RadioButton
            this.selected = selected
            text = AnnotatedString(label)
            onClick { onClick(); true }
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
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked, role = Role.Switch, onValueChange = onCheckedChange
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

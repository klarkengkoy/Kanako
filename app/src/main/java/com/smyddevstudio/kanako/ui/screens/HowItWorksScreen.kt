package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import com.smyddevstudio.kanako.ui.navigation.HelpTopic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.smyddevstudio.kanako.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHome: () -> Unit = {},
    initialTopic: HelpTopic = HelpTopic.FLOATING_READER
) {
    var topic by rememberSaveable { mutableStateOf(initialTopic) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.how_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(stringResource(R.string.how_intro), style = MaterialTheme.typography.bodyLarge)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HelpTopic.entries.forEach { option ->
                        val label = stringResource(when (option) {
                            HelpTopic.FLOATING_READER -> R.string.help_floating
                            HelpTopic.CAMERA -> R.string.camera_title
                            HelpTopic.APPEARANCE -> R.string.preferences_reading_appearance
                            HelpTopic.PRIVACY -> R.string.preferences_privacy
                            HelpTopic.TROUBLESHOOTING -> R.string.help_troubleshooting
                        })
                        FilterChip(
                            selected = topic == option,
                            onClick = { topic = option },
                            label = { Text(label) },
                            border = null,
                            trailingIcon = if (topic == option) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.heightIn(min = 48.dp).clearAndSetSemantics {
                                role = Role.RadioButton
                                selected = topic == option
                                text = AnnotatedString(label)
                                onClick { topic = option; true }
                            }
                        )
                    }
                }
                when (topic) {
                    HelpTopic.FLOATING_READER -> {
                        ReadingExplanation(stringResource(R.string.how_start_title), stringResource(R.string.how_start))
                        ReadingExplanation(stringResource(R.string.how_tap_title), stringResource(R.string.how_tap))
                        ReadingExplanation(stringResource(R.string.how_move_title), stringResource(R.string.how_move))
                        ReadingExplanation(stringResource(R.string.how_stop_title), stringResource(R.string.how_stop))
                        ReadingExplanation(stringResource(R.string.how_tile_title), stringResource(R.string.how_tile))
                    }
                    HelpTopic.CAMERA ->
                        ReadingExplanation(stringResource(R.string.how_camera_title), stringResource(R.string.how_camera))
                    HelpTopic.APPEARANCE ->
                        ReadingExplanation(stringResource(R.string.how_preferences_title), stringResource(R.string.how_preferences))
                    HelpTopic.PRIVACY -> {
                        ReadingExplanation(stringResource(R.string.how_privacy_title), stringResource(R.string.how_privacy))
                        ReadingExplanation(stringResource(R.string.how_capture_scope_title), stringResource(R.string.how_capture_scope))
                        ReadingExplanation(stringResource(R.string.how_offline_title), stringResource(R.string.how_offline))
                    }
                    HelpTopic.TROUBLESHOOTING -> {
                        ReadingExplanation(stringResource(R.string.how_limits_title), stringResource(R.string.how_limits))
                        ReadingExplanation(stringResource(R.string.how_recovery_title), stringResource(R.string.how_recovery))
                    }
                }
                OutlinedButton(onClick = onNavigateToHome,
                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_return))
                }
            }
        }
    }
}

@Composable
private fun ReadingExplanation(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
fun HowItWorksScreenPreview() {
    KanakoTheme { HowItWorksScreen(onBack = {}) }
}

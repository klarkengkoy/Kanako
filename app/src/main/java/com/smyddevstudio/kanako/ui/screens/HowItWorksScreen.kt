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
import androidx.compose.material3.Button
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
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: KanakoPreferences = KanakoPreferences(),
    onNavigateToHome: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.how_title)) },
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
                Text(stringResource(R.string.how_intro),
                    style = MaterialTheme.typography.headlineMedium)
                ReadingExplanation(stringResource(R.string.how_access_title), stringResource(R.string.how_access))
                ReadingExplanation(stringResource(R.string.how_open_title), stringResource(R.string.how_open))
                ReadingExplanation(stringResource(R.string.how_tap_title), stringResource(R.string.how_tap))
                ReadingExplanation(stringResource(R.string.how_move_title), stringResource(R.string.how_move))
                ReadingExplanation(stringResource(R.string.how_preferences_title), stringResource(R.string.how_preferences))
                ReadingExplanation(stringResource(R.string.how_privacy_title), stringResource(R.string.how_privacy))
                ReadingExplanation(stringResource(R.string.how_limits_title), stringResource(R.string.how_limits))
                ReadingExplanation(stringResource(R.string.how_offline_title), stringResource(R.string.how_offline))
                ReadingExplanation(stringResource(R.string.how_tile_title), stringResource(R.string.how_tile))
                ReadingExplanation(stringResource(R.string.how_camera_title), stringResource(R.string.how_camera))
                Button(onClick = onNavigateToHome) { Text(stringResource(R.string.home_return)) }
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
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
fun HowItWorksScreenPreview() {
    KanakoTheme { HowItWorksScreen(onBack = {}) }
}

package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Composable
internal fun CameraPhotoGuide(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val instructions = when (step) {
        0 -> R.string.guided_photo_inspect
        1 -> R.string.guided_photo_kanako
        else -> R.string.guided_photo_library
    }
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(
        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 240.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tutorial_step, step + 1, 3),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                key(step) {
                    Text(stringResource(instructions), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                            .semantics { liveRegion = LiveRegionMode.Polite })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.guided_skip))
                    }
                    Button(onClick = { if (step < 2) step++ else onComplete() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (step < 2) R.string.tutorial_next else R.string.done))
                    }
                }
            }
        }
    }
}

@Preview(name = "Photo guide", widthDp = 393, heightDp = 260)
@Preview(name = "Photo guide large text", widthDp = 320, heightDp = 300, fontScale = 2f)
@Preview(name = "Photo guide landscape", widthDp = 720, heightDp = 260)
@Composable
private fun CameraPhotoGuidePreview() {
    KanakoTheme { CameraPhotoGuide({}, {}) }
}

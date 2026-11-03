package com.smyddevstudio.kanako.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.screens.HomeScreen
import com.smyddevstudio.kanako.ui.screens.PreferencesScreen
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

@Preview(name = "French Home", locale = "fr", widthDp = 360, heightDp = 800)
@Preview(name = "Italian Home", locale = "it", widthDp = 360, heightDp = 800)
@Preview(name = "Nepali Home", locale = "ne", widthDp = 360, heightDp = 800)
@Preview(name = "Filipino Home", locale = "fil", widthDp = 360, heightDp = 800)
@Composable
private fun LocalizedHomePreview() {
    KanakoTheme {
        HomeScreen(
            onNavigateToHowItWorks = {},
            onNavigateToCamera = {},
            onNavigateToPreferences = {},
            preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
            overlayGranted = true,
            accessibilityEnabled = true,
            accessibilityConnected = true
        )
    }
}

@Preview(name = "French large text", locale = "fr", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Italian large text", locale = "it", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Nepali large text", locale = "ne", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Preview(name = "Filipino large text", locale = "fil", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun LocalizedPreferencesPreview() {
    KanakoTheme {
        PreferencesScreen(onBack = {})
    }
}

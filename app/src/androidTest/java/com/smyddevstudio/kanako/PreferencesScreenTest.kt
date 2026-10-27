package com.smyddevstudio.kanako

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.components.readingSampleSegments
import com.smyddevstudio.kanako.ui.screens.PreferencesScreen
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fixedPreviewReadingsMatchTheTokenizerInBothScripts() {
        val sample = context.getText(R.string.preview_sample_text)
        val tokenizer = JapaneseTextTokenizer()
        ScriptType.entries.forEach { script ->
            assertEquals(
                tokenizer.tokenize(sample.toString(), script),
                readingSampleSegments(sample, script)
            )
        }
        assertEquals(
            listOf(FuriganaSegment("かな")),
            readingSampleSegments("かな", ScriptType.KANA)
        )
    }

    @Test fun previewUpdatesScriptAndStaysPinnedWhileControlsScroll() {
        val preferences = mutableStateOf(KanakoPreferences())
        compose.setContent {
            KanakoTheme {
                PreferencesScreen(
                    onBack = {},
                    preferences = preferences.value,
                    onUpdateScriptType = { preferences.value = preferences.value.copy(scriptType = it) }
                )
            }
        }
        compose.onNodeWithText("にほんご").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(context.getString(R.string.script_romaji)))
        compose.onNodeWithText(context.getString(R.string.script_romaji)).performClick()
        compose.onNodeWithText("nihongo").assertIsDisplayed()
        compose.onNodeWithText("にほんご").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(context.getString(R.string.privacy_title)))
        compose.onNodeWithText(context.getString(R.string.preferences_preview)).assertIsDisplayed()
        compose.onNodeWithText("nihongo").assertIsDisplayed()
    }

    @Test fun guideAndAutoReadConfirmationStillWorkWithLazyControls() {
        var enabled = false
        val preferences = mutableStateOf(KanakoPreferences())
        compose.setContent {
            KanakoTheme {
                PreferencesScreen(
                    onBack = {},
                    preferences = preferences.value,
                    onUpdateAutoReadAfterScroll = {
                        enabled = it
                        preferences.value = preferences.value.copy(autoReadAfterScroll = it)
                    }
                )
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(context.getString(R.string.guide)))
        compose.onNodeWithText(context.getString(R.string.guide)).performClick()
        compose.onNodeWithText(context.getString(R.string.quick_settings_guide_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(context.getString(R.string.auto_read_title)))
        compose.onNodeWithText(context.getString(R.string.tap_to_read)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.auto_read_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_enable)).assertIsDisplayed()
        assertEquals(false, enabled)
        compose.onNodeWithText(context.getString(R.string.snapshot_disclosure_cancel)).performClick()
        assertEquals(false, enabled)
        compose.onNodeWithText(context.getString(R.string.tap_to_read)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.auto_read_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_enable)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_title)).assertIsSelected()
        assertEquals(true, enabled)
        compose.onNodeWithText(context.getString(R.string.tap_to_read)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_title)).assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.tap_to_read)).assertIsSelected()
        assertEquals(false, enabled)
    }

    @Test fun compactPreviewStaysExpandedWithLargeTextWhileControlsScroll() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KanakoTheme {
                    PreferencesScreen(onBack = {}, modifier = Modifier.requiredSize(320.dp, 480.dp))
                }
            }
        }
        compose.onNodeWithText("にほんご").assertIsDisplayed()
        compose.onNodeWithText("日本語").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(context.getString(R.string.reading_mode)))
        compose.onNodeWithText(context.getString(R.string.preferences_preview)).assertIsDisplayed()
        compose.onNodeWithText("にほんご").assertIsDisplayed()
        compose.onNodeWithText("日本語").assertIsDisplayed()
    }
}

package com.smyddevstudio.kanako

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
            .performScrollToNode(hasText(context.getString(R.string.privacy_access)))
        compose.onNodeWithText(context.getString(R.string.preferences_preview)).assertIsDisplayed()
        compose.onNodeWithText("nihongo").assertIsDisplayed()
    }

    @Test fun quickSettingsGuideRemainsAndAutoReadMovesOutOfPreferences() {
        compose.setContent { KanakoTheme { PreferencesScreen(onBack = {}) } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(context.getString(R.string.guide)))
        compose.onNodeWithText(context.getString(R.string.guide)).performClick()
        compose.onNodeWithText(context.getString(R.string.quick_settings_guide_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_title)).assertDoesNotExist()
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
            .performScrollToNode(hasText(context.getString(R.string.preferences_control_size)))
        compose.onNodeWithText(context.getString(R.string.preferences_preview)).assertIsDisplayed()
        compose.onNodeWithText("にほんご").assertIsDisplayed()
        compose.onNodeWithText("日本語").assertIsDisplayed()
    }
    @Test fun settingsUseLabelledSingleChoiceAndWholeRowToggles() {
        val preferences = mutableStateOf(KanakoPreferences(autoHideOnKeyboard = false))
        compose.setContent {
            KanakoTheme {
                PreferencesScreen(onBack = {}, preferences = preferences.value,
                    onUpdateAutoHideOnKeyboard = { preferences.value = preferences.value.copy(autoHideOnKeyboard = it) })
            }
        }
        compose.onNodeWithText(context.getString(R.string.script_kana))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(context.getString(R.string.preferences_keyboard)))
        compose.onNodeWithText(context.getString(R.string.preferences_keyboard)).performClick().assertIsOn()
        compose.runOnIdle { assertEquals(true, preferences.value.autoHideOnKeyboard) }
    }

    @Test fun privacyLinkRemainsWithoutADuplicateGeneralHelpEntry() {
        var privacy = 0
        compose.setContent {
            KanakoTheme {
                PreferencesScreen(onBack = {}, onNavigateToPrivacy = { privacy++ })
            }
        }
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(context.getString(R.string.privacy_access)))
        compose.onNodeWithText(context.getString(R.string.privacy_access)).performClick()
        compose.onNodeWithText(context.getString(R.string.how_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, privacy) }
    }

    @Test fun defaultAppearanceAndReadingControlsMatchTheRepositoryDefaults() {
        compose.setContent { KanakoTheme { PreferencesScreen(onBack = {}) } }
        for (label in listOf(R.string.script_kana, R.string.reading_size_medium,
                R.string.color_crimson, R.string.plate_washi, R.string.size_medium)) {
            compose.onNode(hasScrollToNodeAction())
                .performScrollToNode(hasText(context.getString(label)))
            compose.onNodeWithText(context.getString(label)).assertIsSelected()
        }
        for (label in listOf(R.string.preferences_keyboard)) {
            compose.onNode(hasScrollToNodeAction())
                .performScrollToNode(hasText(context.getString(label)))
            compose.onNodeWithText(context.getString(label)).assertIsOn()
        }
    }
    @Test fun dimmedPreferencesStillAcceptScrollGesturesDuringTheTutorial() {
        compose.setContent {
            KanakoTheme {
                val focus = androidx.compose.runtime.remember {
                    com.smyddevstudio.kanako.ui.components.TutorialFocus(4, setOf("tile_guide"))
                }
                CompositionLocalProvider(com.smyddevstudio.kanako.ui.components.LocalTutorialFocus provides focus) {
                    com.smyddevstudio.kanako.ui.screens.TutorialLayout(4,
                        com.smyddevstudio.kanako.model.TutorialPath.FLOATING, {}, {},
                        Modifier.requiredSize(320.dp, 600.dp)) { modifier ->
                        PreferencesScreen(onBack = {}, modifier = modifier)
                    }
                }
            }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        list.performTouchInput { swipeDown() }
        val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        org.junit.Assert.assertTrue(after < before)
        compose.onNodeWithText(context.getString(R.string.guided_skip)).assertIsDisplayed()
    }

}

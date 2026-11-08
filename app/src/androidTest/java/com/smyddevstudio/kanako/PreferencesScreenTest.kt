package com.smyddevstudio.kanako

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import org.junit.Assert.assertTrue
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

    @Test fun quickSettingsGuideStaysReadableWithLargeTextInEveryLocale() {
        val language = mutableStateOf("en")
        compose.setContent {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(language.value))
            }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localized, LocalResources provides localized.resources,
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                KanakoTheme {
                    PreferencesScreen(onBack = {}, modifier = Modifier.requiredSize(320.dp, 480.dp))
                }
            }
        }
        for (tag in listOf("en", "fr", "it", "ja", "ne", "fil")) {
            compose.runOnIdle { language.value = tag }
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            val localized = context.createConfigurationContext(configuration)
            val guide = localized.getString(R.string.guide)
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(guide))
            for (id in listOf(R.string.tile_title, R.string.tile_summary, R.string.guide)) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(localized.getString(id))
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                val name = localized.resources.getResourceEntryName(id)
                for (line in 0 until layout.lineCount) {
                    assertTrue("$tag / $name clips the start of line $line", layout.getLineLeft(line) >= -1f)
                    assertTrue("$tag / $name clips the end of line $line",
                        layout.getLineRight(line) <= layout.size.width + 1f)
                }
                assertTrue("$tag / $name clips its last line",
                    layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
                assertEquals("$tag / $name hides some text", layout.layoutInput.text.text.trimEnd().length,
                    layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            }
            compose.onNodeWithText(guide).assertIsDisplayed().performClick()
            compose.onNodeWithText(localized.getString(R.string.quick_settings_guide_title)).assertIsDisplayed()
            val body = compose.onNodeWithText(localized.getString(R.string.quick_settings_guide_body))
            val range = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            if (range.maxValue() > 0f) {
                body.performTouchInput { swipeUp() }
                assertTrue("$tag guide instructions can scroll", range.value() > 0f)
            }
            compose.onNodeWithText(localized.getString(R.string.done)).performClick()
        }
    }

    @Test fun widePreviewAndSettingsKeepTheirStateWhenTheWindowNarrows() {
        val window = mutableStateOf(900.dp to 360.dp)
        val preferences = mutableStateOf(KanakoPreferences())
        compose.setContent {
            KanakoTheme {
                PreferencesScreen(
                    onBack = {}, preferences = preferences.value,
                    modifier = Modifier.requiredSize(window.value.first, window.value.second),
                    onUpdateScriptType = { preferences.value = preferences.value.copy(scriptType = it) }
                )
            }
        }
        val preview = compose.onNodeWithText(context.getString(R.string.preferences_preview))
            .fetchSemanticsNode().boundsInRoot
        val appearance = compose.onNodeWithText(context.getString(R.string.preferences_reading_appearance))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Preview belongs beside the settings in a wide window", preview.right < appearance.left)
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText(context.getString(R.string.script_romaji)))
        compose.onNodeWithText(context.getString(R.string.script_romaji)).performClick()
        list.performScrollToNode(hasText(context.getString(R.string.preferences_keyboard)))
        fun firstVisibleItem() = list.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value().toInt()
        val anchor = firstVisibleItem()
        val wideHeight = list.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { window.value = 320.dp to 480.dp }
        assertEquals("The list keeps its scroll anchor while narrowing", anchor, firstVisibleItem())
        compose.onNodeWithText("nihongo").assertIsDisplayed()
        compose.runOnIdle { window.value = 600.dp to 360.dp }
        val stackedHeight = list.fetchSemanticsNode().boundsInRoot.height
        assertTrue("The wider arrangement gives settings more vertical room", wideHeight > stackedHeight)
        compose.runOnIdle { window.value = 900.dp to 360.dp }
        assertEquals("The list keeps its scroll anchor while widening", anchor, firstVisibleItem())
        compose.onNodeWithText("nihongo").assertIsDisplayed()
    }

    @Test fun guideKeepsKeyboardFocusAndStaysVisibleWhenTheWindowNarrows() {
        val window = mutableStateOf(1000.dp to 600.dp)
        var inputMode: InputModeManager? = null
        compose.setContent {
            inputMode = LocalInputModeManager.current
            KanakoTheme {
                PreferencesScreen(onBack = {},
                    modifier = Modifier.requiredSize(window.value.first, window.value.second))
            }
        }
        compose.runOnIdle { assertTrue(requireNotNull(inputMode).requestInputMode(InputMode.Keyboard)) }
        val label = context.getString(R.string.guide)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
        for (size in listOf(840.dp to 600.dp, 839.dp to 600.dp, 320.dp to 480.dp, 1000.dp to 600.dp)) {
            compose.runOnIdle { window.value = size }
            compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
        }
        compose.onNodeWithText(label).performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText(context.getString(R.string.quick_settings_guide_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
    }

    @Test fun focusedSettingsStayVisibleWhenTheWindowResizes() {
        val window = mutableStateOf(1000.dp to 600.dp)
        var inputMode: InputModeManager? = null
        compose.setContent {
            inputMode = LocalInputModeManager.current
            KanakoTheme {
                PreferencesScreen(onBack = {},
                    modifier = Modifier.requiredSize(window.value.first, window.value.second))
            }
        }
        compose.runOnIdle { assertTrue(requireNotNull(inputMode).requestInputMode(InputMode.Keyboard)) }
        for (id in listOf(R.string.script_romaji, R.string.preferences_keyboard, R.string.privacy_access)) {
            val label = context.getString(id)
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            for (size in listOf(840.dp to 600.dp, 839.dp to 600.dp, 320.dp to 480.dp, 1000.dp to 600.dp)) {
                compose.runOnIdle { window.value = size }
                compose.onNodeWithText(label).assertIsFocused().assertIsDisplayed()
            }
        }
    }

    @Test fun wideTutorialKeepsPreviewAndScriptControlsUsable() {
        val preferences = mutableStateOf(KanakoPreferences())
        compose.setContent {
            KanakoTheme {
                val focus = androidx.compose.runtime.remember {
                    com.smyddevstudio.kanako.ui.components.TutorialFocus(
                        3, setOf("appearance_preview", "appearance_script")
                    )
                }
                CompositionLocalProvider(com.smyddevstudio.kanako.ui.components.LocalTutorialFocus provides focus) {
                    com.smyddevstudio.kanako.ui.screens.TutorialLayout(
                        3, com.smyddevstudio.kanako.model.TutorialPath.FLOATING, {}, {},
                        Modifier.requiredSize(900.dp, 600.dp)
                    ) { modifier ->
                        PreferencesScreen(onBack = {}, preferences = preferences.value, modifier = modifier,
                            onUpdateScriptType = { preferences.value = preferences.value.copy(scriptType = it) })
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.script_romaji)).performTouchInput { click() }
        compose.onNodeWithText("nihongo").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.tutorial_next)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.guided_skip)).assertIsDisplayed()
    }

    @Test fun scriptChoicesKeepKeyboardFocusAndSingleChoiceSemantics() {
        val preferences = mutableStateOf(KanakoPreferences())
        var inputMode: InputModeManager? = null
        compose.setContent {
            inputMode = LocalInputModeManager.current
            KanakoTheme {
                PreferencesScreen(onBack = {}, preferences = preferences.value,
                    onUpdateScriptType = { preferences.value = preferences.value.copy(scriptType = it) })
            }
        }
        compose.runOnIdle { assertTrue(requireNotNull(inputMode).requestInputMode(InputMode.Keyboard)) }
        val label = context.getString(R.string.script_romaji)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        compose.onNodeWithText("nihongo").assertIsDisplayed()
    }

    @Test fun guideCanBeOpenedWithTheKeyboard() {
        var inputMode: InputModeManager? = null
        compose.setContent {
            inputMode = LocalInputModeManager.current
            KanakoTheme { PreferencesScreen(onBack = {}) }
        }
        compose.runOnIdle { assertTrue(requireNotNull(inputMode).requestInputMode(InputMode.Keyboard)) }
        val guide = context.getString(R.string.guide)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(guide))
        compose.onNodeWithText(guide)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(guide).assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText(context.getString(R.string.quick_settings_guide_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
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
        assertTrue(after < before)
        compose.onNodeWithText(context.getString(R.string.guided_skip)).assertIsDisplayed()
    }

}

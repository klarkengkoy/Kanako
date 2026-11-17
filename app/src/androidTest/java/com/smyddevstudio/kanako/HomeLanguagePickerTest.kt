package com.smyddevstudio.kanako

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.ui.components.LanguagePickerContent
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLanguagePickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun nativeLanguageChoiceEmitsItsTagAndClosesTheMenu() {
        var selectedTag: String? = "en"
        compose.setContent {
            KanakoTheme {
                LanguagePickerContent("en", "en", { selectedTag = it })
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.language_button, "English"))
            .performClick()
        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText("日本語").assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals("ja", selectedTag) }
        compose.onNodeWithText("日本語").assertDoesNotExist()
    }

    @Test fun deviceDefaultSelectsItsSingleLanguageRow() {
        var selectedTag: String? = null
        compose.setContent {
            KanakoTheme {
                LanguagePickerContent(null, "en", { selectedTag = it })
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.language_button, "English"))
            .performClick()
        compose.onAllNodesWithText("English").assertCountEquals(1)
        compose.onNodeWithText("English").assertIsSelected().performClick()

        compose.runOnIdle { assertEquals("en", selectedTag) }
        compose.onNodeWithText("English").assertDoesNotExist()
    }
}

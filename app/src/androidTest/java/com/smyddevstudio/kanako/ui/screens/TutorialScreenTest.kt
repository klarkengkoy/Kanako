package com.smyddevstudio.kanako.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorialScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun floatingPracticeRequiresReadAndClearBeforeContinuing() {
        compose.setContent { KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.FLOATING) } }
        compose.onNodeWithText(text(R.string.tutorial_next)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsNotEnabled()
        val mascot = compose.onNodeWithContentDescription(text(R.string.floating_control_description))
        mascot.performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsNotEnabled()
        mascot.performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsEnabled()
    }

    @Test
    fun recreationRestoresTheLessonAndReadClearProgress() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.FLOATING) } }
        compose.onNodeWithText(text(R.string.tutorial_next)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.floating_control_description)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription(text(R.string.floating_control_description)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsEnabled()
    }

    @Test
    fun skippingAndExitingNeverRecordsCompletion() {
        var completions = 0
        var exits = 0
        compose.setContent {
            KanakoTheme { TutorialScreen({ exits++ }, { completions++ }, initialPath = TutorialPath.CAMERA) }
        }
        compose.onNodeWithText(text(R.string.tutorial_skip)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.tutorial_exit)).performClick()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, completions)
        }
    }

    @Test
    fun cancellingThePracticeSaveDoesNotCompleteTheExercise() {
        compose.setContent {
            KanakoTheme { TutorialScreen({}, {}, initialPath = TutorialPath.CAMERA, initialStep = 4) }
        }
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.camera_save)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.snapshot_disclosure_cancel)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.camera_save)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.done)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsEnabled()
    }

    @Test
    fun finishingOnePathReportsOnlyThatPathAndLeavesReplayAvailable() {
        var completed: TutorialPath? = null
        compose.setContent {
            KanakoTheme { TutorialScreen({}, { completed = it }, initialPath = TutorialPath.CAMERA) }
        }
        repeat(5) {
            compose.onNodeWithText(text(R.string.tutorial_skip)).performScrollTo().performClick()
        }
        compose.onNodeWithText(text(R.string.tutorial_complete)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TutorialPath.CAMERA, completed) }
        compose.onNodeWithText(text(R.string.tutorial_replay)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.help_floating)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.camera_title)).assertIsDisplayed()
    }
}

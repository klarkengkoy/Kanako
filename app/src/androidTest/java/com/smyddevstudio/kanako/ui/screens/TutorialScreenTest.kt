package com.smyddevstudio.kanako.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.service.GuidedReaderSession
import com.smyddevstudio.kanako.service.GuidedReaderStep
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorialScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Before fun begin() { GuidedReaderSession.begin() }
    @After fun finish() { GuidedReaderSession.end() }

    @Test fun realLessonHasNoSimulatedReaderOrManualAdvanceButton() {
        compose.setContent { KanakoTheme { TutorialScreen({}, {}) } }
        compose.onNodeWithText(text(R.string.guided_read)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guided_sample)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_next)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.done)).assertDoesNotExist()
    }

    @Test fun sampleStaysAtTheSamePositionWhenInstructionsChangeAfterOcr() {
        compose.setContent { KanakoTheme { TutorialScreen({}, {}) } }
        val sample = compose.onNodeWithText(text(R.string.guided_sample))
        val before = sample.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { GuidedReaderSession.advance(GuidedReaderStep.READ, GuidedReaderStep.CONTROLS) }
        compose.onNodeWithText(text(R.string.guided_controls)).assertIsDisplayed()
        assertEquals(before, sample.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun skippingNeverRecordsCompletion() {
        var completions = 0
        var exits = 0
        compose.setContent { KanakoTheme { TutorialScreen({ exits++ }, { completions++ }) } }
        compose.onNodeWithText(text(R.string.guided_skip)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, completions)
        }
    }

    @Test fun completionRequiresAllRealServiceEvents() {
        var completions = 0
        compose.setContent { KanakoTheme { TutorialScreen({}, { completions++ }) } }
        compose.runOnIdle {
            GuidedReaderSession.advance(GuidedReaderStep.STOP, GuidedReaderStep.COMPLETE)
        }
        compose.onNodeWithText(text(R.string.done)).assertDoesNotExist()
        compose.runOnIdle {
            GuidedReaderSession.advance(GuidedReaderStep.READ, GuidedReaderStep.CONTROLS)
            GuidedReaderSession.advance(GuidedReaderStep.CONTROLS, GuidedReaderStep.SCRIPT)
            GuidedReaderSession.advance(GuidedReaderStep.SCRIPT, GuidedReaderStep.CLEAR)
            GuidedReaderSession.advance(GuidedReaderStep.CLEAR, GuidedReaderStep.DRAG)
            GuidedReaderSession.advance(GuidedReaderStep.DRAG, GuidedReaderStep.PREFERENCES)
            GuidedReaderSession.advance(GuidedReaderStep.PREFERENCES, GuidedReaderStep.RETURN)
            GuidedReaderSession.returnFromPreferences()
            GuidedReaderSession.advance(GuidedReaderStep.STOP, GuidedReaderStep.COMPLETE)
        }
        compose.onNodeWithText(text(R.string.done)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }
}

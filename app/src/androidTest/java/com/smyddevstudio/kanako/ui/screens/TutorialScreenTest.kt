package com.smyddevstudio.kanako.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.service.GuidedReaderSession
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

    @Composable
    private fun Lesson(onComplete: () -> Unit = {}, onSkip: () -> Unit = {}, initialStep: Int = 0) {
        var step by rememberSaveable { mutableIntStateOf(initialStep) }
        var prompt by rememberSaveable(step) { mutableIntStateOf(0) }
        KanakoTheme {
            TutorialLayout(step, TutorialPath.FLOATING,
                onNext = {
                    if (prompt < tutorialPrompts(step, TutorialPath.FLOATING).lastIndex) prompt++
                    else when (step) { 2 -> step = 6; 6 -> step = 3; 4 -> onComplete(); else -> step++ }
                }, onSkip = onSkip, prompt = prompt) { modifier ->
                TutorialScreen(step, TutorialPath.FLOATING, {}, {}, modifier)
            }
        }
    }

    @Test fun optionalPracticeCanFinishWithoutAnyServiceOrRecognitionEvents() {
        var completions = 0
        compose.setContent { Lesson(onComplete = { completions++ }) }
        compose.onNodeWithText(text(R.string.tutorial_choose)).assertIsDisplayed()
        repeat(2 + tutorialPrompts(1, TutorialPath.FLOATING).size +
            tutorialPrompts(2, TutorialPath.FLOATING).size + tutorialPrompts(6, TutorialPath.FLOATING).size + tutorialPrompts(4, TutorialPath.FLOATING).lastIndex) { compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick() }
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithContentDescription(text(R.string.done)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun sampleRemainsAvailableWhenTheGuideChangesHeight() {
        compose.setContent { Lesson(initialStep = 1) }
        val sample = compose.onNodeWithText(text(R.string.guided_sample))
        sample.assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertIsDisplayed()
        sample.assertIsDisplayed()
        repeat(2) { compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick() }
        compose.onNodeWithText(text(R.string.tutorial_controls)).assertIsDisplayed()
        sample.assertIsDisplayed()
    }

    @Test fun skippingNeverRecordsCompletion() {
        var completions = 0
        var exits = 0
        compose.setContent { Lesson(onComplete = { completions++ }, onSkip = { exits++ }) }
        compose.onNodeWithText(text(R.string.guided_skip)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, completions)
        }
    }

    @Test fun bothReaderChoicesBelongToTheSameIntroduction() {
        val choices = mutableListOf<TutorialPath>()
        compose.setContent {
            KanakoTheme { TutorialScreen(0, TutorialPath.FLOATING, { choices.add(it) }, {}) }
        }
        compose.onNodeWithText(text(R.string.tutorial_try_floating)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_try_camera)).performClick()
        compose.runOnIdle { assertEquals(listOf(TutorialPath.FLOATING, TutorialPath.CAMERA), choices) }
    }
}

package com.smyddevstudio.kanako.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraPhoto
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorialGuideTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Composable
    private fun Guide(onComplete: () -> Unit = {}, onSkip: () -> Unit = {}) {
        var step by rememberSaveable { mutableIntStateOf(1) }
        var prompt by rememberSaveable(step) { mutableIntStateOf(0) }
        TutorialGuide(step, TutorialPath.CAMERA, {
            if (prompt < tutorialPrompts(step, TutorialPath.CAMERA).lastIndex) prompt++
            else if (step < 4) step++ else onComplete()
        }, onSkip, prompt = prompt, onPrevious = {
            if (prompt > 0) prompt-- else if (step > 1) step--
        }, canPrevious = prompt > 0 || step > 1)
    }

    @Test fun completionRequiresDoneAfterAllSharedInstructions() {
        var completions = 0
        compose.setContent { KanakoTheme { Guide(onComplete = { completions++ }) } }
        compose.onNodeWithText(text(R.string.camera_capture)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.done)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_dismiss)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_controls)).assertIsDisplayed()
        repeat(tutorialPrompts(2, TutorialPath.CAMERA).size) {
            compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        }
        compose.onNodeWithText(text(R.string.tutorial_preferences)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tile_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_tile_use)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.done)).performClick()
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun changingCameraPromptsKeepsTheReaderViewportStable() {
        val position = mutableStateOf(1 to 0)
        compose.setContent {
            KanakoTheme {
                TutorialLayout(position.value.first, TutorialPath.CAMERA, {}, {},
                    modifier = Modifier.requiredSize(320.dp, 600.dp), prompt = position.value.second) { modifier ->
                    Box(modifier.testTag("stable-reader"))
                }
            }
        }
        val before = compose.onNodeWithTag("stable-reader").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(text(R.string.tutorial_controls_camera)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_dismiss)).assertDoesNotExist()
        for (next in listOf(1 to 1, 1 to 2, 2 to 0, 2 to 1)) {
            compose.runOnIdle { position.value = next }
            assertEquals(before, compose.onNodeWithTag("stable-reader").fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText(text(R.string.tutorial_script)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_auto_read)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tutorial_controls_camera)).assertDoesNotExist()
    }

    @Test fun floatingFooterReservesOnlyTheCurrentTextHeight() {
        val position = mutableStateOf(2 to 2)
        compose.setContent {
            KanakoTheme {
                TutorialLayout(position.value.first, TutorialPath.FLOATING, {}, {},
                    modifier = Modifier.requiredSize(320.dp, 600.dp), prompt = position.value.second) { modifier ->
                    Box(modifier.testTag("natural-reader"))
                }
            }
        }
        val short = compose.onNodeWithTag("natural-reader").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { position.value = 6 to 1 }
        val long = compose.onNodeWithTag("natural-reader").fetchSemanticsNode().boundsInRoot
        assertTrue(long.height < short.height)
        compose.runOnIdle { position.value = 2 to 2 }
        assertEquals(short, compose.onNodeWithTag("natural-reader").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun controlsPracticeComesBeforeClearingReadingsForBothReaders() {
        val prompt = mutableIntStateOf(0)
        val path = mutableStateOf(TutorialPath.FLOATING)
        compose.setContent {
            KanakoTheme {
                TutorialGuide(2, path.value, { prompt.intValue++ }, {}, prompt = prompt.intValue)
            }
        }
        for (reader in TutorialPath.entries) {
            compose.runOnIdle { path.value = reader; prompt.intValue = 0 }
            compose.onNodeWithText(text(R.string.tutorial_menu)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
            compose.onNodeWithText(text(R.string.tutorial_script)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
            compose.onNodeWithText(text(if (reader == TutorialPath.FLOATING)
                R.string.tutorial_controls_floating else R.string.tutorial_photo_toggle)).assertIsDisplayed()
        }
    }

    @Test fun previousAndNextIconsRevisitPromptsWithoutCompleting() {
        var completions = 0
        compose.setContent { KanakoTheme { Guide(onComplete = { completions++ }) } }
        compose.onNodeWithContentDescription(text(R.string.tutorial_previous)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tutorial_previous)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_controls_camera)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.guided_skip)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
    }

    @Test fun setupPromptFollowsTheCurrentHighlightAfterPermissionReturn() {
        val target = mutableStateOf("overlay_access")
        compose.setContent {
            KanakoTheme {
                CompositionLocalProvider(com.smyddevstudio.kanako.ui.components.LocalTutorialFocus provides
                    com.smyddevstudio.kanako.ui.components.TutorialFocus(-1, setOf(target.value))) {
                    TutorialGuide(-1, TutorialPath.FLOATING, {}, {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.tutorial_setup_overlay, text(R.string.setup_open_overlay))).assertIsDisplayed()
        compose.runOnIdle { target.value = "screen_access" }
        compose.onNodeWithText(context.getString(R.string.tutorial_setup_screen, text(R.string.setup_open_accessibility))).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tutorial_setup_overlay, text(R.string.setup_open_overlay))).assertDoesNotExist()
        compose.runOnIdle { target.value = "start_reader" }
        compose.onNodeWithText(context.getString(R.string.tutorial_setup_ready, text(R.string.setup_enable))).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tutorial_setup_screen, text(R.string.setup_open_accessibility))).assertDoesNotExist()
    }

    @Test fun skippingDoesNotRecordCompletion() {
        var completions = 0
        var skips = 0
        compose.setContent { KanakoTheme { Guide({ completions++ }, { skips++ }) } }
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.guided_skip)).performClick()
        compose.runOnIdle {
            assertEquals(0, completions)
            assertEquals(1, skips)
        }
    }

    @Test fun sharedStepSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KanakoTheme { Guide() } }
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.tutorial_highlights_body)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.tutorial_previous)).performClick()
        compose.onNodeWithText(text(R.string.tutorial_controls_camera)).assertIsDisplayed()
    }

    @Test fun sharedFooterKeepsPhotoAndNavigationReachableWithLargeText() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        val shown = mutableStateOf(true)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    KanakoTheme {
                        TutorialLayout(1, TutorialPath.CAMERA, {}, {},
                            Modifier.requiredSize(320.dp, 480.dp).testTag("tutorial-layout")) { modifier ->
                            CameraResultContent(
                                CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo), {}, {},
                                modifier = modifier,
                                showReadings = shown.value, onShowReadingsChange = { shown.value = it },
                                photograph = { Box(it.testTag("photo-area")) }
                            )
                        }
                    }
                }
            }
            val photograph = compose.onNodeWithTag("photo-area").fetchSemanticsNode().boundsInRoot
            val layout = compose.onNodeWithTag("tutorial-layout").fetchSemanticsNode().boundsInRoot
            assertTrue(photograph.height > 0f)
            assertTrue(photograph.height < layout.height)
            val next = compose.onNodeWithContentDescription(text(R.string.tutorial_next)).assertIsDisplayed()
            assertTrue(next.fetchSemanticsNode().boundsInRoot.top >= photograph.bottom)
            compose.onNodeWithText(text(R.string.guided_skip)).assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.floating_control_description))
                .assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { !shown.value }
        } finally { photo.close() }
    }
}

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
import androidx.compose.ui.test.assertIsEnabled
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

    @Test fun deniedAccessDisablesNextWithoutBlockingPreviousOrSkip() {
        val ready = mutableStateOf(false)
        var next = 0
        var previous = 0
        var skipped = 0
        compose.setContent {
            KanakoTheme {
                TutorialGuide(-1, TutorialPath.FLOATING, { next++ }, { skipped++ },
                    canNext = ready.value, canPrevious = true, onPrevious = { previous++ })
            }
        }
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.tutorial_previous)).performClick()
        compose.onNodeWithText(text(R.string.guided_skip)).performClick()
        compose.runOnIdle {
            assertEquals(0, next)
            assertEquals(1, previous)
            assertEquals(1, skipped)
            ready.value = true
        }
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, next) }
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

    @Test fun footerActionsStayReachableAtDoubleFontScaleInShortWindows() {
        val window = mutableStateOf(Triple(320.dp, 480.dp, false))
        val position = mutableStateOf(Triple(TutorialPath.FLOATING, 1, 0))
        var nextClicks = 0
        var previousClicks = 0
        var skips = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KanakoTheme(darkTheme = window.value.third) {
                    TutorialLayout(
                        position.value.second, position.value.first, { nextClicks++ }, { skips++ },
                        Modifier.requiredSize(window.value.first, window.value.second).testTag("large-text-tutorial"),
                        prompt = position.value.third, onPrevious = { previousClicks++ }, canPrevious = true
                    ) { Box(it.testTag("large-text-reader")) }
                }
            }
        }
        val minimumTarget = 48 * context.resources.displayMetrics.density
        var checked = 0
        for (configuration in listOf(Triple(320.dp, 480.dp, false), Triple(400.dp, 320.dp, true))) {
            for (path in TutorialPath.entries) {
                val stages = if (path == TutorialPath.FLOATING) listOf(-1, 0, 1, 2, 6, 5, 3, 4)
                    else listOf(-1, 0, 1, 2, 5, 3, 4)
                for (stage in stages) {
                    val prompts = tutorialPrompts(stage, path)
                    for (prompt in 0..prompts.lastIndex.coerceAtLeast(0)) {
                        compose.runOnIdle {
                            window.value = configuration
                            position.value = Triple(path, stage, prompt)
                        }
                        val label = "$configuration / $path / stage $stage / prompt $prompt"
                        val root = compose.onNodeWithTag("large-text-tutorial").fetchSemanticsNode().boundsInRoot
                        val reader = compose.onNodeWithTag("large-text-reader").fetchSemanticsNode().boundsInRoot
                        assertTrue("$label leaves reader space", reader.height > 0f)
                        val skip = compose.onNodeWithText(text(R.string.guided_skip))
                        val previous = compose.onNodeWithContentDescription(text(R.string.tutorial_previous))
                        val next = compose.onNodeWithContentDescription(text(
                            if (stage == 4 && prompt == prompts.lastIndex) R.string.done else R.string.tutorial_next
                        ))
                        val bounds = listOf(skip, previous, next).map { action ->
                            action.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.also {
                                assertTrue("$label has a full touch target",
                                    it.width >= minimumTarget - 1 && it.height >= minimumTarget - 1)
                                assertTrue("$label keeps the action below the reader and inside the window",
                                    it.left >= root.left - 1 && it.right <= root.right + 1 &&
                                        it.top >= reader.bottom - 1 && it.bottom <= root.bottom + 1)
                            }
                        }
                        assertTrue("$label keeps actions separate",
                            bounds[0].right <= bounds[1].left + 1 && bounds[1].right <= bounds[2].left + 1)
                        skip.performClick()
                        previous.performClick()
                        next.performClick()
                        checked++
                        compose.runOnIdle {
                            assertEquals(checked, skips)
                            assertEquals(checked, previousClicks)
                            assertEquals(checked, nextClicks)
                        }
                    }
                }
            }
        }
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

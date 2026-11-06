package com.smyddevstudio.kanako.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
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
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPhotoGuideTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun completionRequiresDoneAfterReviewingAllThreeInstructions() {
        var completions = 0
        var skips = 0
        compose.setContent { KanakoTheme { CameraPhotoGuide({ completions++ }, { skips++ }) } }
        compose.onNodeWithText(text(R.string.guided_photo_inspect)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.done)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.guided_photo_kanako)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.guided_photo_library)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, completions) }
        compose.onNodeWithText(text(R.string.done)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertEquals(0, skips)
        }
    }

    @Test fun skippingPhotoGuidanceDoesNotRecordCompletion() {
        var completions = 0
        var skips = 0
        compose.setContent { KanakoTheme { CameraPhotoGuide({ completions++ }, { skips++ }) } }
        compose.onNodeWithText(text(R.string.tutorial_next)).performClick()
        compose.onNodeWithText(text(R.string.guided_skip)).performClick()
        compose.runOnIdle {
            assertEquals(0, completions)
            assertEquals(1, skips)
        }
    }

    @Test fun photoLessonStepSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KanakoTheme { CameraPhotoGuide({}, {}) } }
        compose.onNodeWithText(text(R.string.tutorial_next)).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.guided_photo_kanako)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.tutorial_step, 2, 3)).assertIsDisplayed()
    }

    @Test fun guideReservesPhotoSpaceAndLeavesKanakoUsableWithLargeText() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        val shown = mutableStateOf(true)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    KanakoTheme {
                        CameraResultContent(
                            CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo), {}, {},
                            modifier = Modifier.requiredSize(320.dp, 480.dp),
                            showReadings = shown.value, onShowReadingsChange = { shown.value = it },
                            photograph = { Box(it.testTag("photo-area")) },
                            guide = { CameraPhotoGuide({}, {}, Modifier.testTag("photo-guide")) }
                        )
                    }
                }
            }
            val photograph = compose.onNodeWithTag("photo-area").fetchSemanticsNode().boundsInRoot
            val guide = compose.onNodeWithTag("photo-guide").fetchSemanticsNode().boundsInRoot
            assertTrue("Photo $photograph must keep at least half the view beside guide $guide",
                photograph.height >= guide.height - 1f)
            assertTrue(photograph.bottom <= guide.top + 1f)
            compose.onNodeWithText(text(R.string.guided_skip)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.tutorial_next)).assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(text(R.string.floating_control_description))
                .assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { !shown.value }
        } finally { photo.close() }
    }
}

package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.screens.CameraResultContent
import com.smyddevstudio.kanako.ui.screens.cameraStatusString
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraResultControlsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingPhotoKeepsBackAvailableWithoutSaveOrRetryButtonsAtLargeText() {
        var backs = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KanakoTheme {
                    CameraResultContent(CameraReaderUiState(), { backs++ }, {},
                        modifier = Modifier.requiredSize(320.dp, 480.dp))
                }
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.camera_photo_missing)).assertCountEquals(1)
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun kanakoTogglesReadingsAndOffersOnlyPhotoControls() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        val showReadings = mutableStateOf(true)
        try {
            compose.setContent {
                KanakoTheme {
                    CameraResultContent(
                        CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo), {}, {},
                        modifier = Modifier.testTag("photo-root"),
                        showReadings = showReadings.value,
                        onShowReadingsChange = { showReadings.value = it },
                        photograph = { Box(it.testTag("photo-area")) }
                    )
                }
            }
            val root = compose.onNodeWithTag("photo-root").fetchSemanticsNode().boundsInRoot
            assertEquals(root, compose.onNodeWithTag("photo-area").fetchSemanticsNode().boundsInRoot)
            compose.onAllNodes(hasClickAction()).assertCountEquals(3)
            val kanako = compose.onNodeWithContentDescription(context.getString(R.string.floating_control_description))
            kanako.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.camera_comparison_readings)))
            kanako.performClick()
            compose.waitUntil(5_000) { !showReadings.value }
            kanako.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.camera_comparison_original)))
            kanako.performClick()
            compose.waitUntil(5_000) { showReadings.value }
            kanako.performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.controls_script,
                context.getString(R.string.script_kana))).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.controls_open)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.controls_stop)).assertDoesNotExist()
        } finally { photo.close() }
    }

    @Test fun photoErrorsKeepBackAvailableWithoutAddingRetryOrSaveControls() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        val status = mutableStateOf(CameraReadingStatus.NO_TEXT)
        var backs = 0
        try {
            compose.setContent {
                KanakoTheme {
                    CameraResultContent(CameraReaderUiState(status = status.value, photo = photo), { backs++ }, {})
                }
            }
            for (state in listOf(CameraReadingStatus.NO_TEXT, CameraReadingStatus.READ_ERROR, CameraReadingStatus.MODEL_NEEDED)) {
                compose.runOnIdle { status.value = state }
                compose.onAllNodesWithText(context.getString(cameraStatusString(state))).onFirst().assertIsDisplayed()
                compose.onNodeWithContentDescription(context.getString(R.string.floating_control_description)).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
            compose.runOnIdle { assertEquals(1, backs) }
        } finally { photo.close() }
    }
}

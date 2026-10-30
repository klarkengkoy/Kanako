package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.screens.CameraResultContent
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraResultControlsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingPhotoHasOneMessageAndOneRecoveryActionAtLargeText() {
        var returns = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KanakoTheme {
                    CameraResultContent(CameraReaderUiState(), {}, { returns++ }, {}, {}, {},
                        modifier = Modifier.requiredSize(320.dp, 480.dp))
                }
            }
        }
        compose.onAllNodesWithText(context.getString(R.string.camera_photo_missing)).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.camera_photo_missing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.camera_save)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.camera_result_retake))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, returns) }
    }

    @Test fun photoActionsRemainReachableAtLargeTextInAShortWindow() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888))
        var saves = 0
        var returns = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    KanakoTheme {
                        CameraResultContent(CameraReaderUiState(status = CameraReadingStatus.PHOTO, photo = photo),
                            {}, { returns++ }, {}, {}, { saves++ },
                            modifier = Modifier.requiredSize(320.dp, 480.dp), canSave = true,
                            photograph = { Box(it.fillMaxSize()) })
                    }
                }
            }
            compose.onNodeWithText(context.getString(R.string.camera_save))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(context.getString(R.string.camera_result_retake))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, saves); assertEquals(1, returns) }
        } finally { photo.close() }
    }
}

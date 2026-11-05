package com.smyddevstudio.kanako.ui.screens

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidedCameraControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun guideEnablesZoomThenCaptureAndDisablesUnrelatedControls() {
        val step = mutableIntStateOf(1)
        var captures = 0
        compose.setContent {
            KanakoTheme {
                CameraReaderContent(
                    state = CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true),
                    granted = true, onBack = {}, onPreferences = {},
                    onCapture = { captures++ }, onPermission = {}, onRetry = {},
                    onZoom = { step.intValue = 3 }, guideTarget = step.intValue,
                    viewfinder = { modifier ->
                        Box(modifier.testTag("viewfinder").cameraReaderGestures(
                            onZoom = { step.intValue = 3 }, onFocus = {}, onInteraction = {}
                        ))
                    }
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.camera_capture)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_zoom_in)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.gallery_title)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_appearance)).assertIsNotEnabled()
        compose.runOnIdle { step.intValue = 2 }
        compose.onNodeWithTag("viewfinder").performTouchInput {
            pinch(center - Offset(30f, 0f), center - Offset(90f, 0f),
                center + Offset(30f, 0f), center + Offset(90f, 0f), 400)
        }
        compose.onNodeWithContentDescription(text(R.string.camera_zoom_in)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.gallery_title)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_capture)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, captures) }
    }
}

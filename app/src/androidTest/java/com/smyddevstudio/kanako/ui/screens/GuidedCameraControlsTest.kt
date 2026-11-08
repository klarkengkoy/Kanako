package com.smyddevstudio.kanako.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import com.smyddevstudio.kanako.ui.components.LocalTutorialFocus
import com.smyddevstudio.kanako.ui.components.TutorialFocus
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.camera.CameraReaderUiState
import com.smyddevstudio.kanako.camera.CameraReadingStatus
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidedCameraControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun sharedGuideAllowsCaptureLibraryAndFooterWhileHidingUnrelatedControls() {
        var captures = 0
        var preferences = 0
        var backs = 0
        val focus = TutorialFocus(1, setOf("camera_capture", "camera_gallery"))
        compose.setContent {
            KanakoTheme {
                CompositionLocalProvider(LocalTutorialFocus provides focus) {
                TutorialLayout(1, TutorialPath.CAMERA, {}, {}) { modifier ->
                    CameraReaderContent(
                        state = CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true),
                        granted = true, onBack = { backs++ }, onPreferences = { preferences++ },
                        onCapture = { captures++ }, onPermission = {}, onRetry = {},
                        onZoom = {}, modifier = modifier,
                        viewfinder = { Box(it) }
                    )
                }
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.gallery_title)).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_appearance)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.tutorial_next)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.guided_skip)).assertIsEnabled()
        compose.onNodeWithContentDescription(text(R.string.camera_capture)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, captures)
            focus.allowed = setOf("camera_back", "camera_preferences")
        }
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(text(R.string.camera_appearance)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, preferences)
        }
    }

    @Test fun permissionStepExposesOnlyItsAllowedCameraAction() {
        var requests = 0
        var backs = 0
        var preferences = 0
        val focus = androidx.compose.runtime.mutableStateOf<TutorialFocus?>(
            TutorialFocus(-1, setOf("camera_permission"))
        )
        compose.setContent {
            KanakoTheme {
                CompositionLocalProvider(LocalTutorialFocus provides focus.value) {
                    CameraReaderContent(
                        CameraReaderUiState(), granted = false,
                        onBack = { backs++ }, onPreferences = { preferences++ },
                        onCapture = {}, onPermission = { requests++ }, onRetry = {}, onZoom = {}
                    )
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.camera_appearance)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.camera_not_now)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.camera_allow)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            focus.value = null
        }
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(text(R.string.camera_appearance)).assertIsEnabled().performClick()
        compose.onNodeWithText(text(R.string.camera_not_now)).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2, backs)
            assertEquals(1, preferences)
        }
    }
}

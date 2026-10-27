package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.CameraPhotoSurface
import com.smyddevstudio.kanako.ui.screens.CameraReaderContent
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraControlsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int) = context.getString(id)

    @Test fun cameraPermissionIsIndependentAndDoesNotStartTheReader() {
        var requests = 0
        var captures = 0
        compose.setContent {
            KanakoTheme {
                CameraReaderContent(CameraReaderUiState(), false, {}, {}, { captures++ }, { requests++ }, {}, {})
            }
        }
        compose.onNodeWithText(string(R.string.camera_allow)).performClick()
        compose.runOnIdle {
            assertEquals(1, requests)
            assertEquals(0, captures)
        }
    }

    @Test fun shutterIsDisabledUntilTheCameraIsStreaming() {
        compose.setContent {
            KanakoTheme { CameraReaderContent(CameraReaderUiState(), true, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription(string(R.string.camera_capture)).assertIsNotEnabled()
    }

    @Test fun readyControlsDispatchCaptureFocusAlternativesAndNavigation() {
        var captures = 0
        var backs = 0
        var preferences = 0
        val zooms = mutableListOf<Float>()
        compose.setContent {
            KanakoTheme {
                CameraReaderContent(
                    CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true), true,
                    { backs++ }, { preferences++ }, { captures++ }, {}, {}, { zooms.add(it) }
                )
            }
        }
        compose.onNodeWithContentDescription(string(R.string.camera_zoom_in)).performClick()
        compose.onNodeWithContentDescription(string(R.string.camera_zoom_out)).performClick()
        compose.onNodeWithContentDescription(string(R.string.camera_capture)).performClick()
        compose.onNodeWithContentDescription(string(R.string.camera_appearance)).performClick()
        compose.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        compose.runOnIdle {
            assertEquals(1, captures)
            assertEquals(1, backs)
            assertEquals(1, preferences)
            assertEquals(listOf(1.5f, 1 / 1.5f), zooms)
        }
    }

    @Test fun capturedPhotoPinchZoomsAndDoubleTapResets() {
        val photo = CameraPhoto.fromOwnedBitmap(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888))
        try {
            compose.setContent {
                KanakoTheme { CameraPhotoSurface(photo, emptyList(), KanakoPreferences(), Modifier.fillMaxSize()) }
            }
            val image = compose.onNodeWithContentDescription(string(R.string.camera_photo_description))
            image.assertIsDisplayed()
            image.performTouchInput {
                pinch(center - Offset(40f, 0f), center + Offset(40f, 0f),
                    center - Offset(120f, 0f), center + Offset(120f, 0f), 400)
            }
            image.assert(SemanticsMatcher("Photo is zoomed") {
                it.config[SemanticsProperties.StateDescription] != context.getString(R.string.camera_zoom_state, 100)
            })
            image.performTouchInput { doubleClick() }
            image.assert(SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription, context.getString(R.string.camera_zoom_state, 100)
            ))
        } finally { photo.close() }
    }
}

package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.view.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import com.smyddevstudio.kanako.ui.screens.rememberCameraControlRotation
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
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

    @Test fun cameraPermissionCanBeDeclinedWithoutRequestingOrCapturing() {
        var backs = 0
        var requests = 0
        var captures = 0
        compose.setContent {
            KanakoTheme {
                CameraReaderContent(CameraReaderUiState(), false, { backs++ }, {},
                    { captures++ }, { requests++ }, {}, {})
            }
        }
        compose.onNodeWithText(string(R.string.camera_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.camera_allow)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.camera_not_now)).performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, requests)
            assertEquals(0, captures)
        }
    }

    @Test fun cameraSettingsRetainsAnExitWithoutOpeningSettings() {
        var backs = 0
        var settings = 0
        compose.setContent {
            KanakoTheme {
                CameraReaderContent(CameraReaderUiState(), false, { backs++ }, {}, {},
                    { settings++ }, {}, {}, permissionLabel = R.string.camera_settings)
            }
        }
        compose.onNodeWithText(string(R.string.camera_settings)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.camera_not_now)).performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, settings)
        }
    }

    @Test fun cameraPermissionActionsRemainReachableWithLargeTextInASmallWindow() {
        var backs = 0
        var settings = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KanakoTheme {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        CameraReaderContent(CameraReaderUiState(), false, { backs++ }, {}, {},
                            { settings++ }, {}, {}, permissionLabel = R.string.camera_settings)
                    }
                }
            }
        }
        compose.onNodeWithText(string(R.string.camera_settings)).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(string(R.string.camera_not_now)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, settings)
            assertEquals(1, backs)
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

    @Test fun landscapeControlsFollowThePhysicalEdgeInBothDirections() {
        val rotation = mutableIntStateOf(Surface.ROTATION_90)
        var captures = 0
        val zooms = mutableListOf<Float>()
        compose.setContent {
            KanakoTheme {
                Box(Modifier.requiredSize(640.dp, 360.dp)) {
                    CameraReaderContent(
                        CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true), true,
                        {}, {}, { captures++ }, {}, {}, { zooms.add(it) },
                        displayRotation = rotation.intValue
                    )
                }
            }
        }
        val capture = compose.onNodeWithContentDescription(string(R.string.camera_capture))
        val zoomIn = compose.onNodeWithContentDescription(string(R.string.camera_zoom_in))
        val zoomOut = compose.onNodeWithContentDescription(string(R.string.camera_zoom_out))
        val back = compose.onNodeWithContentDescription(string(R.string.navigate_back))
        fun assertRail(right: Boolean) {
            capture.assertIsDisplayed()
            zoomIn.assertIsDisplayed()
            zoomOut.assertIsDisplayed()
            val shutter = capture.fetchSemanticsNode().boundsInRoot
            val plus = zoomIn.fetchSemanticsNode().boundsInRoot
            val minus = zoomOut.fetchSemanticsNode().boundsInRoot
            val header = back.fetchSemanticsNode().boundsInRoot
            assertEquals(shutter.center.x, plus.center.x, 1f)
            assertEquals(shutter.center.x, minus.center.x, 1f)
            if (right) {
                assertTrue(shutter.left > header.right)
                assertTrue(plus.bottom < shutter.top)
                assertTrue(shutter.bottom < minus.top)
            } else {
                assertTrue(shutter.right < header.left)
                assertTrue(minus.bottom < shutter.top)
                assertTrue(shutter.bottom < plus.top)
            }
        }
        assertRail(right = true)
        zoomIn.performClick()
        capture.performClick()
        compose.runOnIdle { rotation.intValue = Surface.ROTATION_270 }
        assertRail(right = false)
        zoomOut.performClick()
        capture.performClick()
        compose.runOnIdle {
            assertEquals(2, captures)
            assertEquals(listOf(1.5f, 1 / 1.5f), zooms)
        }
    }

    @Test fun controlsKeepTheirPhysicalCentersSizesAndIdentityThroughRotation() {
        val rotation = mutableIntStateOf(Surface.ROTATION_0)
        compose.setContent {
            val portrait = rotation.intValue == Surface.ROTATION_0
            KanakoTheme {
                Box(Modifier.requiredSize(if (portrait) 360.dp else 640.dp,
                    if (portrait) 640.dp else 360.dp)) {
                    CameraReaderContent(
                        CameraReaderUiState(status = CameraReadingStatus.LIVE, ready = true), true,
                        {}, {}, {}, {}, {}, {}, modifier = Modifier.testTag("camera"),
                        displayRotation = rotation.intValue
                    )
                }
            }
        }
        val labels = listOf(R.string.camera_zoom_out, R.string.camera_capture, R.string.camera_zoom_in)
        val portraitBounds = compose.onNodeWithTag("camera").fetchSemanticsNode().boundsInRoot
        val originals = labels.map {
            val node = compose.onNodeWithContentDescription(string(it)).fetchSemanticsNode()
            node.id to node.boundsInRoot
        }
        compose.mainClock.autoAdvance = false
        for (turn in listOf(Surface.ROTATION_90, Surface.ROTATION_270, Surface.ROTATION_0)) {
            compose.runOnIdle { rotation.intValue = turn }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val root = compose.onNodeWithTag("camera").fetchSemanticsNode().boundsInRoot
            labels.forEachIndexed { index, label ->
                val button = compose.onNodeWithContentDescription(string(label)).fetchSemanticsNode()
                val original = originals[index]
                assertEquals(original.first, button.id)
                assertEquals(original.second.width, button.boundsInRoot.width, 1f)
                assertEquals(original.second.height, button.boundsInRoot.height, 1f)
                val initial = original.second.center - portraitBounds.topLeft
                val expected = when (turn) {
                    Surface.ROTATION_90 -> Offset(initial.y, portraitBounds.width - initial.x)
                    Surface.ROTATION_270 -> Offset(portraitBounds.height - initial.y, initial.x)
                    else -> initial
                }
                val center = button.boundsInRoot.center - root.topLeft
                assertEquals(expected.x, center.x, 1f)
                assertEquals(expected.y, center.y, 1f)
            }
            compose.mainClock.advanceTimeBy(300)
        }
    }

    @Test fun iconsRotateByTheShortestPathAndSettleUpright() {
        val rotation = mutableIntStateOf(Surface.ROTATION_0)
        lateinit var angle: State<Float>
        compose.setContent { angle = rememberCameraControlRotation(rotation.intValue) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { rotation.intValue = Surface.ROTATION_270 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(90f, angle.value, 1f)
        compose.mainClock.advanceTimeBy(125)
        assertTrue(angle.value > 0f && angle.value < 90f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, angle.value, 0.01f)
        compose.runOnIdle { rotation.intValue = Surface.ROTATION_0 }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(-90f, angle.value, 1f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, angle.value, 0.01f)
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
                pinch(start0 = center - Offset(40f, 0f), end0 = center - Offset(120f, 0f),
                    start1 = center + Offset(40f, 0f), end1 = center + Offset(120f, 0f), durationMillis = 400)
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

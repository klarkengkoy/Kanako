package com.smyddevstudio.kanako.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.camera.view.CameraController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.MainActivity
import com.smyddevstudio.kanako.R
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class CameraHardwareTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun findPreview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPreview(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun liveCameraCapturesInMemoryReturnsToLiveAndReleasesHardware() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val rear = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        assumeTrue("Device needs a rear camera for hardware verification", rear.isNotEmpty())
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        }
        val availability = ConcurrentHashMap<String, Boolean>()
        val callback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) { availability[cameraId] = true }
            override fun onCameraUnavailable(cameraId: String) { availability[cameraId] = false }
        }
        manager.registerAvailabilityCallback(callback, Handler(Looper.getMainLooper()))
        try {
            compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo().performClick()
            fun shutterReady() = compose.onAllNodesWithContentDescription(context.getString(R.string.camera_capture))
                .fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
            compose.waitUntil(timeoutMillis = 30_000) { shutterReady() }
            val used = rear.filter { availability[it] == false }
            assertTrue("Rear camera should be acquired", used.isNotEmpty())
            lateinit var viewModel: CameraReaderViewModel
            compose.activityRule.scenario.onActivity { viewModel = ViewModelProvider(it)[CameraReaderViewModel::class.java] }
            compose.waitUntil(timeoutMillis = 30_000) {
                viewModel.state.value.lines.isNotEmpty() ||
                    viewModel.state.value.status in listOf(CameraReadingStatus.NO_TEXT, CameraReadingStatus.MODEL_NEEDED)
            }
            var controller: CameraController? = null
            compose.activityRule.scenario.onActivity { controller = findPreview(it.window.decorView)?.controller }
            assertNotNull(controller)
            var initialZoom = 1f
            compose.runOnIdle { initialZoom = controller!!.cameraInfo!!.zoomState.value!!.zoomRatio }
            compose.onRoot().performTouchInput {
                pinch(center - Offset(40f, 0f), center + Offset(40f, 0f),
                    center - Offset(120f, 0f), center + Offset(120f, 0f), 400)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                var zoomed = false
                compose.activityRule.scenario.onActivity {
                    zoomed = controller!!.cameraInfo!!.zoomState.value!!.zoomRatio > initialZoom
                }
                zoomed
            }
            compose.onRoot().performTouchInput { click(center) }
            compose.waitUntil(timeoutMillis = 10_000) {
                var focused = false
                compose.activityRule.scenario.onActivity { focused = controller!!.tapToFocusInfoState.value?.tapPoint != null }
                focused
            }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(timeoutMillis = 15_000) { used.all { availability[it] == true } }
            assertFalse(viewModel.state.value.ready)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(timeoutMillis = 30_000) { shutterReady() }
            compose.onNodeWithContentDescription(context.getString(R.string.camera_capture)).performClick()
            compose.waitUntil(timeoutMillis = 30_000) { viewModel.state.value.photo != null }
            compose.onNodeWithText(context.getString(R.string.camera_result_title)).assertIsDisplayed()
            compose.waitUntil(timeoutMillis = 30_000) { viewModel.state.value.status != CameraReadingStatus.PROCESSING }
            assertTrue(viewModel.state.value.status in listOf(
                CameraReadingStatus.PHOTO, CameraReadingStatus.NO_TEXT, CameraReadingStatus.MODEL_NEEDED
            ))
            assertTrue(viewModel.state.value.photo!!.width > 0)
            assertTrue(viewModel.state.value.photo!!.height > 0)
            compose.waitUntil(timeoutMillis = 15_000) { used.all { availability[it] == true } }
            val retainedPhoto = viewModel.state.value.photo
            compose.activityRule.scenario.recreate()
            compose.activityRule.scenario.onActivity {
                val retainedModel = ViewModelProvider(it)[CameraReaderViewModel::class.java]
                assertSame(viewModel, retainedModel)
                assertSame(retainedPhoto, retainedModel.state.value.photo)
            }
            compose.onNodeWithText(context.getString(R.string.camera_result_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.camera_result_retake)).performClick()
            compose.waitUntil(timeoutMillis = 30_000) { shutterReady() }
            assertNull(viewModel.state.value.photo)
            compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
            compose.waitUntil(timeoutMillis = 15_000) { used.all { availability[it] == true } }
        } finally {
            manager.unregisterAvailabilityCallback(callback)
        }
    }
}

package com.smyddevstudio.kanako.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.SurfaceView
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicInteger
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.concurrent.TimeUnit
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

    private fun findSurface(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findSurface(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun rotationKeepsTheActivityPreviewSurfaceCameraAndZoomAlive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(CameraManager::class.java)
        assumeTrue(manager.cameraIdList.any {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        })
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        }
        val provider = ProcessCameraProvider.getInstance(context).get(30, TimeUnit.SECONDS)
        val activity = compose.activity
        val originalOrientation = activity.requestedOrientation
        var preview: PreviewView? = null
        val interruptions = AtomicInteger()
        val cameraInterruptions = AtomicInteger()
        var observedInfo: CameraInfo? = null
        val cameraObserver = Observer<CameraState> {
            if (it.type != CameraState.Type.OPEN) cameraInterruptions.incrementAndGet()
        }
        val observer = Observer<PreviewView.StreamState> {
            if (it != PreviewView.StreamState.STREAMING) interruptions.incrementAndGet()
        }
        try {
            compose.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                var streaming = false
                compose.activityRule.scenario.onActivity {
                    preview = findPreview(it.window.decorView)
                    streaming = preview?.previewStreamState?.value == PreviewView.StreamState.STREAMING
                }
                streaming
            }
            val originalPreview = preview!!
            lateinit var cameraInfo: CameraInfo
            lateinit var surfaceView: SurfaceView
            var zoom = 1f
            compose.activityRule.scenario.onActivity {
                cameraInfo = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
                observedInfo = cameraInfo
                cameraInfo.cameraState.observeForever(cameraObserver)
                surfaceView = findSurface(originalPreview)!!
                originalPreview.previewStreamState.observeForever(observer)
                val zoomState = cameraInfo.zoomState.value!!
                zoom = 1.5f.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.camera_zoom_in)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                var zoomReady = false
                compose.activityRule.scenario.onActivity {
                    zoomReady = kotlin.math.abs(cameraInfo.zoomState.value!!.zoomRatio - zoom) < 0.01f
                }
                zoomReady
            }
            val portraitBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val labels = listOf(R.string.camera_zoom_out, R.string.camera_capture, R.string.camera_zoom_in)
            val originalButtons = labels.map {
                val node = compose.onNodeWithContentDescription(context.getString(it)).fetchSemanticsNode()
                node.id to node.boundsInRoot
            }
            val surface = surfaceView.holder.surface
            assertNotNull(surface)
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                compose.activityRule.scenario.onActivity {
                    it.requestedOrientation = orientation
                }
                val expectedRotation = when (orientation) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> android.view.Surface.ROTATION_90
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> android.view.Surface.ROTATION_270
                    else -> android.view.Surface.ROTATION_0
                }
                compose.waitUntil(timeoutMillis = 15_000) {
                    var rotatedAndLive = false
                    compose.activityRule.scenario.onActivity {
                        rotatedAndLive = it.window.decorView.display.rotation == expectedRotation &&
                            originalPreview.previewStreamState.value == PreviewView.StreamState.STREAMING
                    }
                    rotatedAndLive
                }
                compose.activityRule.scenario.onActivity {
                    assertSame(activity, it)
                    assertSame(originalPreview, findPreview(it.window.decorView))
                    assertEquals(CameraState.Type.OPEN, cameraInfo.cameraState.value?.type)
                    assertSame(surfaceView, findSurface(originalPreview))
                    assertSame(surface, surfaceView.holder.surface)
                    assertTrue(surface.isValid)
                    assertEquals("Rotation must not interrupt the preview stream", 0, interruptions.get())
                    assertEquals(zoom, cameraInfo.zoomState.value!!.zoomRatio, 0.01f)
                    assertEquals(PreviewView.StreamState.STREAMING, originalPreview.previewStreamState.value)
                }
                assertEquals("Rotation must not interrupt the preview stream", 0, interruptions.get())
                assertEquals("Rotation must not reopen the camera", 0, cameraInterruptions.get())
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                labels.forEachIndexed { index, label ->
                    val current = compose.onNodeWithContentDescription(context.getString(label)).fetchSemanticsNode()
                    val original = originalButtons[index]
                    val point = original.second.center - portraitBounds.topLeft
                    val expected = when (expectedRotation) {
                        android.view.Surface.ROTATION_90 -> Offset(point.y, portraitBounds.width - point.x)
                        android.view.Surface.ROTATION_270 -> Offset(portraitBounds.height - point.y, point.x)
                        else -> point
                    }
                    val actual = current.boundsInRoot.center - root.topLeft
                    assertEquals(original.first, current.id)
                    assertEquals(original.second.width, current.boundsInRoot.width, 1f)
                    assertEquals(original.second.height, current.boundsInRoot.height, 1f)
                    assertEquals(expected.x, actual.x, 1f)
                    assertEquals(expected.y, actual.y, 1f)
                }
            }
            compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        } finally {
            compose.activityRule.scenario.onActivity {
                preview?.previewStreamState?.removeObserver(observer)
                observedInfo?.cameraState?.removeObserver(cameraObserver)
                it.requestedOrientation = originalOrientation
            }
        }
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
            val provider = ProcessCameraProvider.getInstance(context).get(30, TimeUnit.SECONDS)
            lateinit var cameraInfo: CameraInfo
            compose.activityRule.scenario.onActivity { cameraInfo = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA) }
            var initialZoom = 1f
            compose.runOnIdle { initialZoom = cameraInfo.zoomState.value!!.zoomRatio }
            compose.onRoot().performTouchInput {
                pinch(center - Offset(40f, 0f), center + Offset(40f, 0f),
                    center - Offset(120f, 0f), center + Offset(120f, 0f), 400)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                var zoomed = false
                compose.activityRule.scenario.onActivity {
                    zoomed = cameraInfo.zoomState.value!!.zoomRatio > initialZoom
                }
                zoomed
            }
            compose.onRoot().performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(CameraState.Type.OPEN, cameraInfo.cameraState.value?.type) }
            compose.onNodeWithContentDescription(context.getString(R.string.camera_appearance)).performClick()
            compose.onNodeWithText(context.getString(R.string.preferences_title)).assertIsDisplayed()
            compose.waitUntil(timeoutMillis = 15_000) { used.all { availability[it] == true } }
            compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
            compose.waitUntil(timeoutMillis = 30_000) { shutterReady() }
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

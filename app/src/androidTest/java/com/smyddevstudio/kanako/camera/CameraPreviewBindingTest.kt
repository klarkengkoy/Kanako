package com.smyddevstudio.kanako.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.smyddevstudio.kanako.ui.screens.cameraReaderGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.content.ContextCompat
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.click
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPreviewBindingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bindingSupportsLiveTransformsPinchFocusAndExitCleanup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        }
        val provider = ProcessCameraProvider.getInstance(context).get(30, TimeUnit.SECONDS)
        assumeTrue(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        val info = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        val transforms = AtomicInteger()
        val errors = AtomicInteger()
        val focusRequests = AtomicInteger()
        lateinit var activeInfo: androidx.camera.core.CameraInfo
        val visible = mutableStateOf(true)
        lateinit var binding: CameraPreviewBinding
        lateinit var preview: PreviewView
        compose.setContent {
            val owner = LocalLifecycleOwner.current
            if (visible.value) AndroidView(
                factory = {
                    val analysis = ImageAnalysis.Builder().build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(it)) { frame -> frame.close() }
                    binding = CameraPreviewBinding(it, analysis, ImageCapture.Builder().build(),
                        { matrix -> if (matrix != null) transforms.incrementAndGet() }, { errors.incrementAndGet() })
                    PreviewView(it).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        contentDescription = "Camera binding test preview"
                        preview = this
                        doOnLayout { binding.bind(owner, this) }
                    }
                },
                modifier = Modifier.fillMaxSize().cameraReaderGestures(
                    { factor -> binding.zoomBy(factor) }, { point -> focusRequests.incrementAndGet(); binding.focusAt(point?.x, point?.y) }, {}),
                onRelease = { binding.close() }
            )
        }
        compose.waitUntil(timeoutMillis = 30_000) {
            var ready = false
            compose.runOnUiThread {
                ready = preview.previewStreamState.value == PreviewView.StreamState.STREAMING && transforms.get() > 0
            }
            ready
        }
        var initialZoom = 1f
        compose.runOnIdle {
            activeInfo = binding.cameraInfo!!
            assertEquals(CameraState.Type.OPEN, info.cameraState.value?.type)
            initialZoom = activeInfo.zoomState.value!!.zoomRatio
        }
        compose.onRoot().performTouchInput {
            pinch(center - Offset(40f, 0f), center + Offset(40f, 0f),
                center - Offset(120f, 0f), center + Offset(120f, 0f), 400)
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            var zoomed = false
            compose.runOnUiThread { zoomed = binding.cameraInfo!!.zoomState.value!!.zoomRatio > initialZoom }
            zoomed
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, focusRequests.get()) }
        var focus: com.google.common.util.concurrent.ListenableFuture<androidx.camera.core.FocusMeteringResult>? = null
        compose.runOnIdle { focus = binding.focusAt(preview.width / 2f, preview.height / 2f) }
        assertNotNull(focus)
        focus!!.get(15, TimeUnit.SECONDS)
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(timeoutMillis = 15_000) {
            var released = false
            compose.runOnUiThread { released = activeInfo.cameraState.value?.type == CameraState.Type.CLOSED }
            released
        }
        assertEquals(0, errors.get())
    }

}

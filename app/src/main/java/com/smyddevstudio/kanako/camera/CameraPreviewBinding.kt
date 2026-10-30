package com.smyddevstudio.kanako.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.hardware.SensorManager
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@MainThread
internal class CameraPreviewBinding(
    context: Context,
    private val analysis: ImageAnalysis,
    private val capture: ImageCapture,
    private val onTransform: (Matrix?) -> Unit,
    private val onError: () -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = ContextCompat.getMainExecutor(appContext)
    private val preview = Preview.Builder().build()
    private val closed = AtomicBoolean()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var view: PreviewView? = null
    private var detach: (() -> Unit)? = null
    val cameraInfo get() = camera?.cameraInfo

    fun bind(owner: LifecycleOwner, previewView: PreviewView): Boolean {
        if (closed.get() || view != null || !hasPermission()) return false
        view = previewView
        var boundAspect: Rational? = null
        var lastTransform: FloatArray? = null

        fun updateTransform() {
            val matrix = previewView.sensorToViewTransform
            val values = matrix?.let { FloatArray(9).also(it::getValues) }
            if (values == null && lastTransform == null) return
            if (values?.contentEquals(lastTransform) == true) return
            lastTransform = values
            onTransform(matrix)
        }

        fun bindIfNeeded() {
            val available = provider ?: return
            if (closed.get() || !hasPermission() || !previewView.isAttachedToWindow ||
                !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            val viewport = previewView.viewPort ?: return
            val ratio = viewport.aspectRatio
            // Quarter turns swap both viewport dimensions and rotation: the sensor crop is unchanged.
            val naturalAspect = if (viewport.rotation % 2 == 0) ratio
                else Rational(ratio.denominator, ratio.numerator)
            if (camera != null && naturalAspect == boundAspect) return
            try {
                onTransform(null)
                lastTransform = null
                if (camera != null) available.unbind(preview, capture, analysis)
                camera = available.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA,
                    UseCaseGroup.Builder().setViewPort(viewport)
                        .addUseCase(preview).addUseCase(capture).addUseCase(analysis).build())
                boundAspect = naturalAspect
            } catch (_: RuntimeException) {
                onError()
            }
        }

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bindIfNeeded()
            updateTransform()
        }
        val drawListener = ViewTreeObserver.OnPreDrawListener {
            if (!closed.get()) {
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                if (preview.targetRotation != rotation) preview.targetRotation = rotation
                updateTransform()
            }
            true
        }
        val orientationListener = object : OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN || closed.get()) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                if (analysis.targetRotation != rotation) {
                    onTransform(null)
                    lastTransform = null
                    analysis.targetRotation = rotation
                    capture.targetRotation = rotation
                }
            }
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { bindIfNeeded() }
            override fun onViewDetachedFromWindow(view: View) {
                onTransform(null)
                provider?.unbind(preview, capture, analysis)
                camera = null
            }
        }
        detach = {
            orientationListener.disable()
            previewView.removeOnAttachStateChangeListener(attachListener)
            previewView.removeOnLayoutChangeListener(layoutListener)
            if (previewView.viewTreeObserver.isAlive) previewView.viewTreeObserver.removeOnPreDrawListener(drawListener)
            preview.setSurfaceProvider(null)
        }
        return try {
            previewView.addOnAttachStateChangeListener(attachListener)
            previewView.addOnLayoutChangeListener(layoutListener)
            previewView.viewTreeObserver.addOnPreDrawListener(drawListener)
            preview.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            analysis.targetRotation = preview.targetRotation
            capture.targetRotation = preview.targetRotation
            preview.setSurfaceProvider(previewView.surfaceProvider)
            orientationListener.enable()
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener({
                if (!closed.get()) {
                    try {
                        provider = future.get()
                        bindIfNeeded()
                    } catch (_: Exception) {
                        onError()
                    }
                }
            }, main)
            true
        } catch (_: RuntimeException) {
            close()
            false
        }
    }

    fun zoomBy(factor: Float) {
        val active = camera ?: return
        val zoom = active.cameraInfo.zoomState.value ?: return
        active.cameraControl.setZoomRatio((zoom.zoomRatio * factor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
    }

    fun focusAt(x: Float? = null, y: Float? = null) = view?.let { previewView ->
        val point = previewView.meteringPointFactory.createPoint(x ?: previewView.width / 2f, y ?: previewView.height / 2f)
        camera?.cameraControl?.startFocusAndMetering(
            FocusMeteringAction.Builder(point).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            detach?.invoke()
        } finally {
            detach = null
            view = null
            try {
                provider?.unbind(preview, capture, analysis)
            } finally {
                camera = null
                provider = null
            }
        }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

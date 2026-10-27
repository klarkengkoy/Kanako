package com.smyddevstudio.kanako.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class LiveCameraSession(
    private val context: Context,
    private val ticket: () -> Long?,
    private val onGeometryChanged: () -> Unit,
    private val onReading: (Long, Long, Result<List<CameraReadingLine>>) -> Unit,
    private val onCameraError: () -> Unit
) : AutoCloseable {
    private val executor = Dispatchers.Default.limitedParallelism(1).asExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val closed = AtomicBoolean()
    private val recognizerClosed = AtomicBoolean()
    private val inFlight = AtomicBoolean()
    private val geometry = AtomicLong()
    private val resourceLock = Any()
    private val geometryLock = Any()
    @Volatile private var transform: Matrix? = null
    private var lastStarted = 0L
    private var cachedReadings = emptyMap<String, List<FuriganaSegment>>()

    val controller = LifecycleCameraController(context).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        imageAnalysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        imageCaptureResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
            .build()
        isPinchToZoomEnabled = true
        isTapToFocusEnabled = true
    }

    init {
        controller.setImageAnalysisAnalyzer(executor, object : ImageAnalysis.Analyzer {
            override fun getTargetCoordinateSystem() = ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED

            override fun updateTransform(matrix: Matrix?) {
                synchronized(geometryLock) {
                    transform = matrix?.let { Matrix(it) }
                    geometry.incrementAndGet()
                }
                main.execute { if (!closed.get()) onGeometryChanged() }
            }

            override fun analyze(image: ImageProxy) {
                val currentTicket = ticket()
                val snapshot = synchronized(geometryLock) { transform?.let { Matrix(it) to geometry.get() } }
                val started = SystemClock.elapsedRealtime()
                val accepted = synchronized(resourceLock) {
                    !closed.get() && currentTicket != null && snapshot != null &&
                        // Keep geometry responsive; content stabilization happens independently in CameraLiveReadings.
                        started - lastStarted >= 160 && inFlight.compareAndSet(false, true)
                }
                if (!accepted || currentTicket == null || snapshot == null) {
                    image.close()
                    return
                }
                lastStarted = started
                val (matrix, geometryTicket) = snapshot
                val frameClosed = AtomicBoolean()
                val tracked = object : ImageProxy by image {
                    override fun close() {
                        if (frameClosed.compareAndSet(false, true)) {
                            try {
                                image.close()
                            } finally {
                                inFlight.set(false)
                                finishClose()
                            }
                        }
                    }
                }
                try {
                    val analyzer = MlKitAnalyzer(
                        listOf(recognizer), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor
                    ) { result ->
                        if (closed.get() || geometryTicket != geometry.get() || ticket() != currentTicket) return@MlKitAnalyzer
                        val reading = runCatching {
                            result.getThrowable(recognizer)?.let { throw it }
                            cameraReadingLines(result.getValue(recognizer) ?: error("No recognition result"), cachedReadings)
                                .also { lines -> cachedReadings = lines.associate { it.text to it.segments } }
                        }
                        main.execute {
                            if (!closed.get() && geometryTicket == geometry.get()) {
                                onReading(currentTicket, started, reading)
                            }
                        }
                    }
                    analyzer.updateTransform(matrix)
                    analyzer.analyze(tracked)
                } catch (error: Exception) {
                    tracked.close()
                    main.execute {
                        if (!closed.get() && geometryTicket == geometry.get() && ticket() == currentTicket) {
                            onReading(currentTicket, started, Result.failure(error))
                        }
                    }
                }
            }
        })
    }

    fun bind(owner: LifecycleOwner): Boolean {
        if (closed.get() || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            controller.bindToLifecycle(owner)
            controller.initializationFuture.addListener({
                if (!closed.get()) {
                    try {
                        controller.initializationFuture.get()
                        if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) onCameraError()
                    } catch (_: Exception) {
                        onCameraError()
                    }
                }
            }, main)
            true
        } catch (_: RuntimeException) {
            onCameraError()
            false
        }
    }

    fun capture(onResult: (Result<CameraPhoto>) -> Unit) {
        if (closed.get() || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onResult(Result.failure(SecurityException("Camera access unavailable")))
            return
        }
        try {
            controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (closed.get()) {
                        image.close()
                        return
                    }
                    val result = runCatching { CameraPhoto.fromCapture(image) }
                    main.execute {
                        if (closed.get()) result.getOrNull()?.close() else onResult(result)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    main.execute { if (!closed.get()) onResult(Result.failure(exception)) }
                }
            })
        } catch (error: RuntimeException) {
            onResult(Result.failure(error))
        }
    }

    override fun close() {
        synchronized(resourceLock) {
            if (!closed.compareAndSet(false, true)) return
        }
        geometry.incrementAndGet()
        try {
            controller.clearImageAnalysisAnalyzer()
        } finally {
            try {
                controller.unbind()
            } finally {
                finishClose()
            }
        }
    }

    private fun finishClose() {
        synchronized(resourceLock) {
            if (closed.get() && !inFlight.get() && recognizerClosed.compareAndSet(false, true)) recognizer.close()
        }
    }
}

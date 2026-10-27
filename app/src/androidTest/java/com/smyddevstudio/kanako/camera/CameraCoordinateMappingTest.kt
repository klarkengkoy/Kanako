package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.media.ImageReader
import android.media.ImageWriter
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCoordinateMappingTest {
    // Uses a synthetic in-memory image, never the camera or a device screenshot.
    @Test fun cameraXMapsOcrLinesAndSymbolsExactlyOnceForEveryRotationAndCenterCrop() {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawText("日本語", 160f, 270f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
        })
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        val direct = Executor { it.run() }
        try {
            for (rotation in listOf(0, 90, 180, 270)) {
                // Rotate pixels back so the OCR always sees upright Japanese after CameraX rotation.
                val raw = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(-rotation.toFloat()) }, false)
                try {
                    val original = analyze(raw, rotation, recognizer, ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, null, direct)
                    val rotatedWidth = if (rotation % 180 == 0) raw.width else raw.height
                    val rotatedHeight = if (rotation % 180 == 0) raw.height else raw.width
                    for ((viewWidth, viewHeight) in listOf(360 to 720, 720 to 360)) {
                        val scale = maxOf(viewWidth.toFloat() / rotatedWidth, viewHeight.toFloat() / rotatedHeight)
                        val left = (viewWidth - rotatedWidth * scale) / 2
                        val top = (viewHeight - rotatedHeight * scale) / 2
                        val uprightToView = Matrix().apply { setScale(scale, scale); postTranslate(left, top) }
                        // A nonidentity sensor-to-buffer transform catches accidentally treating
                        // CameraX's sensor-to-view matrix as a buffer-to-view matrix.
                        val sensorToBuffer = Matrix().apply { setScale(0.5f, 0.5f); postTranslate(-20f, -30f) }
                        val bufferToUpright = Matrix().apply {
                            postRotate(rotation.toFloat())
                            val rotated = RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat())
                            mapRect(rotated)
                            postTranslate(-rotated.left, -rotated.top)
                        }
                        val sensorToView = Matrix(sensorToBuffer).apply {
                            postConcat(bufferToUpright)
                            postConcat(uprightToView)
                        }
                        val mapped = analyze(raw, rotation, recognizer, ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                            sensorToView, direct, sensorToBuffer)
                        val sourceLines = cameraReadingLines(original)
                        val viewLines = cameraReadingLines(mapped)
                        assertTrue(sourceLines.hasReadings)
                        assertEquals(sourceLines.map { it.text }, viewLines.map { it.text })
                        sourceLines.zip(viewLines).forEach { (source, view) ->
                            assertMapped(source.bounds, view.bounds, uprightToView)
                            assertTrue("Japanese OCR should supply character/element geometry", source.spans.isNotEmpty())
                            assertEquals(source.spans.map { it.start to it.end }, view.spans.map { it.start to it.end })
                            source.spans.zip(view.spans).forEach { (a, b) -> assertMapped(a.bounds, b.bounds, uprightToView) }
                        }
                    }
                } finally { if (raw !== bitmap) raw.recycle() }
            }
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    private fun assertMapped(source: CameraRect, mapped: CameraRect, transform: Matrix) {
        val expected = RectF(source.left, source.top, source.right, source.bottom)
        transform.mapRect(expected)
        assertEquals(expected.left, mapped.left, 3f)
        assertEquals(expected.top, mapped.top, 3f)
        assertEquals(expected.right, mapped.right, 3f)
        assertEquals(expected.bottom, mapped.bottom, 3f)
    }

    private fun analyze(
        bitmap: Bitmap, rotation: Int, recognizer: com.google.mlkit.vision.text.TextRecognizer,
        coordinateSystem: Int, transform: Matrix?, executor: Executor, sensorToBuffer: Matrix = Matrix()
    ): Text {
        val reader = ImageReader.newInstance(bitmap.width, bitmap.height, ImageFormat.YUV_420_888, 2)
        val writer = ImageWriter.newInstance(reader.surface, 2)
        val available = CompletableFuture<Image>()
        reader.setOnImageAvailableListener({ source ->
            source.acquireNextImage()?.let { available.complete(it) }
        }, Handler(Looper.getMainLooper()))
        try {
            val input = writer.dequeueInputImage()
            input.planes.forEachIndexed { index, plane ->
                val rows = if (index == 0) bitmap.height else bitmap.height / 2
                val columns = if (index == 0) bitmap.width else bitmap.width / 2
                for (y in 0 until rows) for (x in 0 until columns) {
                    val value = if (index == 0) 16 + Color.red(bitmap.getPixel(x, y)) * 219 / 255 else 128
                    plane.buffer.put(y * plane.rowStride + x * plane.pixelStride, value.toByte())
                }
            }
            writer.queueInputImage(input)
            val image = available.get(5, TimeUnit.SECONDS)
            val proxy = SyntheticImageProxy(image, rotation, sensorToBuffer)
            try {
                val result = CompletableFuture<Text>()
                val analyzer = MlKitAnalyzer(listOf(recognizer), coordinateSystem, executor) { frame ->
                    val failure = frame.getThrowable(recognizer)
                    if (failure != null) result.completeExceptionally(failure)
                    else result.complete(frame.getValue(recognizer)!!)
                }
                analyzer.updateTransform(transform)
                analyzer.analyze(proxy)
                return result.get(20, TimeUnit.SECONDS)
            } finally { proxy.close() }
        } finally {
            writer.close()
            reader.close()
        }
    }

    private class SyntheticImageProxy(
        private val pixels: Image, rotation: Int, sensorToBuffer: Matrix
    ) : ImageProxy {
        private var closed = false
        private var crop = Rect(0, 0, pixels.width, pixels.height)
        private val info = object : ImageInfo {
            override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
            override fun getTimestamp() = 1L
            override fun getRotationDegrees() = rotation
            override fun getSensorToBufferTransformMatrix() = Matrix(sensorToBuffer)
            override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
        }
        override fun getImage() = pixels
        override fun getImageInfo() = info
        override fun getFormat() = pixels.format
        override fun getHeight() = pixels.height
        override fun getWidth() = pixels.width
        override fun getCropRect() = crop
        override fun setCropRect(rect: Rect?) { crop = rect ?: Rect(0, 0, width, height) }
        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = emptyArray()
        override fun close() { if (!closed) { closed = true; pixels.close() } }
    }
}

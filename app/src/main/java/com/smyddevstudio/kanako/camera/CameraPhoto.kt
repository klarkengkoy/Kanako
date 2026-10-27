package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal class CameraPhoto private constructor(private val pixels: Bitmap) : AutoCloseable {
    val width get() = pixels.width
    val height get() = pixels.height
    private var references = 1
    private val ownerClosed = AtomicBoolean()

    @Synchronized fun acquire(): Lease? {
        if (references == 0) return null
        references++
        return Lease(this, pixels)
    }

    @Synchronized private fun release() {
        references--
        if (references == 0) pixels.recycle()
    }

    override fun close() {
        if (ownerClosed.compareAndSet(false, true)) release()
    }

    class Lease internal constructor(private val owner: CameraPhoto, val bitmap: Bitmap) : AutoCloseable {
        private val closed = AtomicBoolean()
        override fun close() {
            if (closed.compareAndSet(false, true)) owner.release()
        }
    }

    companion object {
        fun fromCapture(image: ImageProxy): CameraPhoto {
            try {
                val raw = if (image.format == ImageFormat.JPEG) {
                    val buffer = image.planes[0].buffer.duplicate()
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }) ?: error("Camera image could not be decoded")
                } else image.toBitmap()
                try {
                    val scaleX = raw.width.toFloat() / image.width
                    val scaleY = raw.height.toFloat() / image.height
                    val crop = image.cropRect
                    val scaledCrop = Rect(
                        (crop.left * scaleX).roundToInt().coerceIn(0, raw.width - 1),
                        (crop.top * scaleY).roundToInt().coerceIn(0, raw.height - 1),
                        (crop.right * scaleX).roundToInt().coerceIn(1, raw.width),
                        (crop.bottom * scaleY).roundToInt().coerceIn(1, raw.height)
                    )
                    val upright = uprightCameraBitmap(raw, scaledCrop, image.imageInfo.rotationDegrees)
                    if (upright !== raw) raw.recycle()
                    return CameraPhoto(upright)
                } catch (error: Throwable) {
                    if (!raw.isRecycled) raw.recycle()
                    throw error
                }
            } finally {
                image.close()
            }
        }

        internal fun fromOwnedBitmap(bitmap: Bitmap) = CameraPhoto(bitmap)
    }
}

internal fun uprightCameraBitmap(bitmap: Bitmap, crop: Rect, rotationDegrees: Int): Bitmap {
    require(rotationDegrees in listOf(0, 90, 180, 270))
    require(crop.left >= 0 && crop.top >= 0 && crop.right <= bitmap.width && crop.bottom <= bitmap.height && !crop.isEmpty)
    return Bitmap.createBitmap(
        bitmap, crop.left, crop.top, crop.width(), crop.height(),
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true
    )
}

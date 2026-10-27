package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraImageTest {
    @Test fun captureCropAndRotationApplyExactlyOnce() {
        val original = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val source = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW)
        original.setPixels(source, 0, 3, 0, 0, 3, 2)
        val expected = mapOf(
            0 to intArrayOf(Color.GREEN, Color.BLUE, Color.MAGENTA, Color.YELLOW),
            90 to intArrayOf(Color.MAGENTA, Color.GREEN, Color.YELLOW, Color.BLUE),
            180 to intArrayOf(Color.YELLOW, Color.MAGENTA, Color.BLUE, Color.GREEN),
            270 to intArrayOf(Color.BLUE, Color.YELLOW, Color.GREEN, Color.MAGENTA)
        )
        try {
            expected.forEach { (rotation, colors) ->
                val upright = uprightCameraBitmap(original, Rect(1, 0, 3, 2), rotation)
                try {
                    assertEquals(2, upright.width)
                    assertEquals(2, upright.height)
                    val actual = IntArray(4)
                    upright.getPixels(actual, 0, 2, 0, 0, 2, 2)
                    assertArrayEquals(colors, actual)
                } finally { if (upright !== original) upright.recycle() }
            }
            val portrait = uprightCameraBitmap(original, Rect(0, 0, 3, 2), 90)
            assertEquals(2, portrait.width)
            assertEquals(3, portrait.height)
            portrait.recycle()
        } finally { original.recycle() }
    }

    @Test fun japaneseRecognitionReadsASyntheticCameraImageLocally() {
        val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        android.graphics.Canvas(bitmap).drawText("日本語を読む", 80f, 220f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 100f
        })
        val photo = CameraPhoto.fromOwnedBitmap(bitmap)
        try {
            val text = try {
                kotlinx.coroutines.runBlocking { recognizeCameraPhoto(photo) }
            } catch (error: com.google.mlkit.common.MlKitException) {
                if (error.errorCode == com.google.mlkit.common.MlKitException.UNAVAILABLE) {
                    org.junit.Assume.assumeNoException("Japanese model is not yet available on this device", error)
                }
                throw error
            }
            val lines = cameraReadingLines(text)
            assertTrue(lines.hasReadings)
            assertTrue(lines.any { line -> line.segments.any { it.reading == "にほんご" } })
        } finally { photo.close() }
    }

    @Test fun photoIsRecycledOnlyAfterDisplayAndOcrLeasesFinish() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val photo = CameraPhoto.fromOwnedBitmap(bitmap)
        val display = photo.acquire()!!
        val ocr = photo.acquire()!!
        photo.close()
        photo.close()
        assertFalse(bitmap.isRecycled)
        display.close()
        display.close()
        assertFalse(bitmap.isRecycled)
        ocr.close()
        assertTrue(bitmap.isRecycled)
        assertNull(photo.acquire())
    }

    @Test fun exportUsesTheSameReadingLayoutAndLeavesTheOriginalUntouched() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val photo = CameraPhoto.fromOwnedBitmap(bitmap)
        val lines = listOf(CameraReadingLine(
            "日本語", CameraRect(100f, 140f, 250f, 180f), listOf(FuriganaSegment("日本語", "にほんご"))
        ))
        val colors = CameraAnnotationColors(Color.BLACK, Color.rgb(245, 235, 215))
        val request = CameraPhotoExport(photo.acquire()!!, lines, CameraExportStyle(24f, ScriptType.KANA, colors))
        try {
            val bytes = ByteArrayOutputStream().also { writeCameraPhotoExport(request, it) }.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertEquals(400, decoded.width)
                assertEquals(300, decoded.height)
                val layout = layoutCameraAnnotations(lines, 400, 300, 24f, ScriptType.KANA)
                assertEquals(1, layout.labels.size)
                var painted = 0
                for (y in 0 until 300) for (x in 0 until 400) {
                    assertEquals(Color.WHITE, bitmap.getPixel(x, y))
                    if (decoded.getPixel(x, y) != Color.WHITE) {
                        painted++
                        assertTrue(layout.labels.any { x >= it.bounds.left && x < it.bounds.right && y >= it.bounds.top && y < it.bounds.bottom })
                    }
                }
                assertTrue(painted > 0)
                assertEquals(Color.WHITE, decoded.getPixel(175, 160))
            } finally { decoded.recycle() }
        } finally {
            request.close()
            photo.close()
        }
    }

    @Test fun denseSmallDocumentKeepsReadingsLocalInLivePhotoAndExportCoordinates() {
        val lines = (0..10).map { row ->
            val top = 100f + row * 20
            CameraReadingLine("日本語", CameraRect(100f, top, 150f, top + 16),
                listOf(FuriganaSegment("日本語", "にほんご")))
        }
        ScriptType.entries.forEach { script ->
            val live = layoutCameraAnnotations(lines, 400, 500, 24f, script)
            assertEquals(lines.size, live.labels.size)
            live.labels.zip(lines).forEach { (label, line) ->
                val height = label.bounds.bottom - label.bounds.top
                assertTrue(label.bounds.bottom >= line.bounds.top - height - label.gap * 2 - 1)
                assertTrue(label.bounds.top <= line.bounds.bottom + label.gap + 1)
            }
            // Check locality separately at photo/export resolution. Font rounding may choose a
            // different nearby collision lane; that is independent of the image transform.
            val photoLines = lines.map { line ->
                line.copy(bounds = CameraRect(line.bounds.left * 2, line.bounds.top * 2,
                    line.bounds.right * 2, line.bounds.bottom * 2))
            }
            val photo = layoutCameraAnnotations(photoLines, 800, 1000, 48f, script)
            assertEquals(live.labels.size, photo.labels.size)
            photo.labels.zip(photoLines).forEach { (label, line) ->
                val height = label.bounds.bottom - label.bounds.top
                assertTrue(label.bounds.bottom >= line.bounds.top - height - label.gap * 2 - 1)
                assertTrue(label.bounds.top <= line.bounds.bottom + label.gap + 1)
                assertTrue(label.bounds.left >= 0 && label.bounds.right <= 800)
            }
            val isolated = layoutCameraAnnotations(lines.take(1), 400, 500, 24f, script).labels.single()
            val scaled = layoutCameraAnnotations(photoLines.take(1), 800, 1000, 48f, script).labels.single()
            assertEquals(isolated.bounds.left * 2f, scaled.bounds.left.toFloat(), 4f)
            assertEquals(isolated.bounds.top * 2f, scaled.bounds.top.toFloat(), 4f)
        }
    }

    @Test fun measuredGlyphAnchorsRespectWhitespaceAndSelectedReadingSize() {
        val line = CameraReadingLine("A 日本語", CameraRect(0f, 200f, 300f, 230f),
            listOf(FuriganaSegment("A", null), FuriganaSegment("日本語", "にほんご")),
            listOf(CameraTextSpan(2, 5, CameraRect(40f, 200f, 100f, 230f))))
        val small = layoutCameraAnnotations(listOf(line), 400, 400, 20f, ScriptType.KANA).labels.single()
        val large = layoutCameraAnnotations(listOf(line), 400, 400, 28f, ScriptType.KANA).labels.single()
        assertEquals(70f, (small.bounds.left + small.bounds.right) / 2f, 1f)
        assertEquals(70f, (large.bounds.left + large.bounds.right) / 2f, 1f)
        assertTrue(large.layouts.single().paint.textSize > small.layouts.single().paint.textSize)
        assertTrue(small.bounds.bottom in 197..199)
    }

    @Test fun verticalAndRomajiReadingsStayWithinImageBounds() {
        val lines = listOf(CameraReadingLine(
            "情報", CameraRect(100f, 40f, 130f, 200f), listOf(FuriganaSegment("情報", "じょうほう"))
        ))
        ScriptType.entries.forEach { script ->
            val layout = layoutCameraAnnotations(lines, 300, 240, 24f, script)
            assertEquals(1, layout.labels.size)
            val label = layout.labels.single()
            assertTrue(label.vertical)
            assertTrue(label.bounds.left >= 0 && label.bounds.right <= 300 && label.bounds.top >= 0 && label.bounds.bottom <= 240)
            assertEquals(if (script == ScriptType.KANA) "じょうほう" else "jouhou", label.reading)
        }
    }
}

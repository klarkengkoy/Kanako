package com.smyddevstudio.kanako.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPhotoAnnotationTest {
    private val document = listOf(
        CameraReadingLine("市役所", CameraRect(100f, 200f, 180f, 230f),
            listOf(FuriganaSegment("市役所", "しやくしょ"))),
        CameraReadingLine("銀行", CameraRect(200f, 200f, 260f, 230f),
            listOf(FuriganaSegment("銀行", "ぎんこう")))
    )

    @Test fun realTextMeasurementDefersCrowdedRomajiAndRevealsItAtCloserZoom() {
        val overview = layoutCameraPhotoAnnotations(document, 500, 400, 36f, ScriptType.ROMAJI)
        assertTrue(overview.labels.isEmpty())
        val closer = layoutCameraPhotoAnnotations(document, 500, 400, 12f, ScriptType.ROMAJI)
        assertEquals(2, closer.labels.size)
        assertNull(closer.labels[0].bounds.intersection(closer.labels[1].bounds))
        closer.labels.forEach { label ->
            assertTrue(label.layouts.all { it.paint.textSize >= 12f })
            document.forEach { line ->
                val source = com.smyddevstudio.kanako.service.ReadingBounds(
                    line.bounds.left.toInt(), line.bounds.top.toInt(), line.bounds.right.toInt(), line.bounds.bottom.toInt()
                )
                assertNull(label.bounds.intersection(source))
            }
        }
    }

    @Test fun largeJapaneseSourceCharactersGrowReadingsAboveTheChosenFloor() {
        val line = CameraReadingLine("日本語", CameraRect(200f, 300f, 400f, 400f),
            listOf(FuriganaSegment("日本語", "にほんご")))
        val ruby = layoutCameraPhotoAnnotations(listOf(line), 700, 600, 12f, ScriptType.KANA).labels.single()
        assertTrue(ruby.layouts.all { it.paint.textSize >= 12f })
        assertEquals(50f, ruby.layouts.single().paint.textSize, 0.01f)
        assertEquals(600, ruby.bounds.left + ruby.bounds.right)
    }

    @Test fun cancellingDuringLayoutDoesNotWriteOutputOrRecycleTheSourcePhoto() {
        val original = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888)
        val photo = CameraPhoto.fromOwnedBitmap(original)
        val request = CameraPhotoExport(photo.acquire()!!, document,
            CameraExportStyle(12f, ScriptType.ROMAJI, CameraAnnotationColors(Color.BLACK, Color.WHITE)))
        val output = ByteArrayOutputStream()
        var checks = 0
        try {
            val error = runCatching {
                writeCameraPhotoExport(request, output, checkCancellation = {
                    checks++
                    if (checks == 2) throw kotlinx.coroutines.CancellationException("Retake")
                })
            }.exceptionOrNull()
            assertTrue(error is kotlinx.coroutines.CancellationException)
            assertEquals(0, output.size())
            assertFalse(original.isRecycled)
        } finally { request.close(); photo.close() }
    }

    @Test fun pngUsesNativePhotoLayoutAndPreservesTheOriginalPhoto() {
        val original = Bitmap.createBitmap(500, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val photo = CameraPhoto.fromOwnedBitmap(original)
        val colors = CameraAnnotationColors(Color.BLACK, Color.LTGRAY)
        val request = CameraPhotoExport(photo.acquire()!!, document, CameraExportStyle(12f, ScriptType.ROMAJI, colors))
        try {
            assertTrue(layoutCameraPhotoAnnotations(document, 500, 400, 36f, ScriptType.ROMAJI).labels.isEmpty())
            val native = layoutCameraPhotoAnnotations(document, 500, 400, 12f, ScriptType.ROMAJI)
            val encoded = ByteArrayOutputStream().also { writeCameraPhotoExport(request, it) }.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            try {
                assertEquals(500, decoded.width)
                assertEquals(400, decoded.height)
                var painted = 0
                for (y in 0 until decoded.height) for (x in 0 until decoded.width) {
                    assertEquals(Color.WHITE, original.getPixel(x, y))
                    if (decoded.getPixel(x, y) != Color.WHITE) {
                        painted++
                        assertTrue(native.labels.any {
                            x >= it.bounds.left && x < it.bounds.right && y >= it.bounds.top && y < it.bounds.bottom
                        })
                    }
                }
                assertTrue(painted > 0)
            } finally { decoded.recycle() }
        } finally { request.close(); photo.close() }
    }
}

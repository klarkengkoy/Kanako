package com.smyddevstudio.kanako.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenReadingTest {
    @Test fun syntheticJapaneseKeepsCharacterGeometryAndWindowOffsets() {
        val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        Canvas(bitmap).drawText("日本語を読む", 70f, 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 80f
        })
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
            val clip = ReadingBounds(0, 0, 1300, 600)
            val original = screenReadingLines(text, 0, 0, 7, clip)
            val shifted = screenReadingLines(text, 43, 71, 7, clip)
            assertTrue(original.any { it.text.contains("日本語") })
            assertEquals(original.size, shifted.size)
            original.zip(shifted).forEach { (a, b) ->
                assertEquals(a.text, b.text)
                assertEquals(a.bounds.left + 43, b.bounds.left)
                assertEquals(a.bounds.top + 71, b.bounds.top)
                assertEquals(a.segments, b.segments)
                assertEquals(a.text, a.segments!!.joinToString("") { it.text })
                assertEquals(7, b.windowId)
                assertTrue(a.spans.isNotEmpty())
                assertEquals(a.spans.map { it.copy(bounds = it.bounds.moved(43, 71)) }, b.spans)
            }
            assertTrue(original.flatMap { it.segments!! }.any { it.reading == "にほんご" })
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}

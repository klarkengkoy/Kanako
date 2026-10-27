package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import com.smyddevstudio.kanako.service.RubyRequest
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraAnnotationGeometryTest {
    private val clip = ReadingBounds(0, 0, 1000, 1600)

    @Test fun unevenCharacterAndElementBoundsReplaceEqualLineSubdivisions() {
        val line = CameraReadingLine("A 日本語", CameraRect(0f, 200f, 300f, 230f), emptyList(), listOf(
            CameraTextSpan(0, 1, CameraRect(0f, 200f, 10f, 230f)),
            CameraTextSpan(2, 3, CameraRect(40f, 201f, 60f, 229f)),
            CameraTextSpan(3, 4, CameraRect(60f, 202f, 81f, 230f)),
            CameraTextSpan(4, 5, CameraRect(82f, 201f, 104f, 229f))
        ))
        assertEquals(CameraRect(40f, 201f, 104f, 230f), cameraTokenBounds(line, 2, 5))
        val element = line.copy(spans = listOf(CameraTextSpan(2, 5, CameraRect(40f, 200f, 100f, 230f))))
        assertEquals(CameraRect(60f, 200f, 100f, 230f), cameraTokenBounds(element, 3, 5))
    }

    @Test fun verticalSpansAndMissingGeometryUseTheCorrectAxis() {
        val line = CameraReadingLine("日本語", CameraRect(100f, 10f, 130f, 190f),
            listOf(FuriganaSegment("日本語", "にほんご")))
        assertEquals(CameraRect(100f, 70f, 130f, 190f), cameraTokenBounds(line, 1, 3))
        val measured = line.copy(spans = listOf(CameraTextSpan(0, 3, CameraRect(101f, 20f, 129f, 140f))))
        assertEquals(CameraRect(101f, 60f, 129f, 140f), cameraTokenBounds(measured, 1, 3))
    }

    @Test fun noCollisionKeepsReadingDirectlyAboveItsKanji() {
        val request = RubyRequest(ReadingBounds(400, 500, 450, 530), clip, 100, 24)
        assertEquals(ReadingBounds(375, 474, 475, 498),
            placeCameraRubyReadings(listOf(request), listOf(request.anchor), 2).single())
    }

    @Test fun adjacentLabelsStaggerOnlyWhenTheyActuallyCollide() {
        val anchors = listOf(ReadingBounds(400, 500, 420, 530), ReadingBounds(425, 500, 445, 530))
        val results = placeCameraRubyReadings(anchors.map { RubyRequest(it, clip, 100, 24) }, anchors, 2).map { it!! }
        assertNull(results[0].intersection(results[1]))
        assertEquals(498, results[0].bottom)
        assertEquals(472, results[1].bottom)
        assertTrue(results.all { it.right - it.left == 100 })
    }

    @Test fun denseDocumentCannotSendReadingsToDistantEmptyMargins() {
        val sources = (0..30).map { ReadingBounds(300, 400 + it * 20, 700, 416 + it * 20) }
        val requests = sources.map { RubyRequest(it, clip, 120, 30) }
        val results = placeCameraRubyReadings(requests, sources, 1)
        results.zip(requests).forEach { (rect, request) ->
            assertNotNull(rect)
            assertTrue(clip.contains(rect!!))
            assertTrue(rect.bottom >= request.anchor.top - request.height - 2)
            assertTrue(rect.top <= request.anchor.bottom + 1)
        }
    }

    @Test fun verticalStaggeringAndViewportEdgesStayLocalAndBounded() {
        val anchors = listOf(ReadingBounds(400, 500, 430, 520), ReadingBounds(400, 525, 430, 545))
        val results = placeCameraRubyReadings(anchors.map { RubyRequest(it, clip, 24, 100, true) }, anchors, 2).map { it!! }
        assertNull(results[0].intersection(results[1]))
        assertTrue(results.all { it.left in 374..458 && clip.contains(it) })
        val edge = RubyRequest(ReadingBounds(0, 0, 16, 16), clip, 100, 24)
        val edgeResult = placeCameraRubyReadings(listOf(edge), listOf(edge.anchor), 1).single()!!
        assertTrue(clip.contains(edgeResult))
        assertNull(edgeResult.intersection(edge.anchor))
        assertNull(placeCameraRubyReadings(listOf(edge.copy(width = 1001)), emptyList(), 1).single())
    }
}

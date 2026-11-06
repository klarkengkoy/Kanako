package com.smyddevstudio.kanako.camera

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraTextGeometryTest {
    private val tilted = listOf(CameraPoint(10f, 20f), CameraPoint(110f, 40f),
        CameraPoint(106f, 60f), CameraPoint(6f, 40f))
    private val span = CameraTextSpan(0, 2, CameraRect(6f, 20f, 110f, 60f), tilted)

    @Test fun aTiltedWordUsesItsActualCornersAndSlicesAlongTheTextDirection() {
        assertEquals(tilted, cameraSpanPolygon(span, 0, 2, false).points)
        assertEquals(listOf(CameraPoint(60f, 30f), CameraPoint(110f, 40f),
            CameraPoint(106f, 60f), CameraPoint(56f, 50f)), cameraSpanPolygon(span, 1, 2, false).points)
    }

    @Test fun verticalTextSlicesDownItsSlantedEdges() {
        assertEquals(listOf(CameraPoint(8f, 30f), CameraPoint(108f, 50f),
            CameraPoint(106f, 60f), CameraPoint(6f, 40f)), cameraSpanPolygon(span, 1, 2, true).points)
    }

    @Test fun separatedCharactersStaySeparateRatherThanEnclosingTheGap() {
        val line = CameraReadingLine("日本", CameraRect(0f, 0f, 100f, 30f), emptyList(), listOf(
            CameraTextSpan(0, 1, CameraRect(5f, 2f, 25f, 28f)),
            CameraTextSpan(1, 2, CameraRect(60f, 1f, 82f, 29f))
        ))
        val polygons = cameraTokenPolygons(line, 0, 2)
        assertEquals(2, polygons.size)
        assertEquals(CameraRect(5f, 2f, 25f, 28f), polygons[0].bounds)
        assertEquals(CameraRect(60f, 1f, 82f, 29f), polygons[1].bounds)
        assertEquals(CameraRect(5f, 1f, 82f, 29f), cameraTokenBounds(line, 0, 2))
    }

    @Test fun aCompoundHasOneSharedOutlineWhileItsCharactersKeepTheirMeasuredGeometry() {
        val line = CameraReadingLine("日本", CameraRect(0f, 0f, 100f, 30f), emptyList(), listOf(
            CameraTextSpan(0, 1, CameraRect(5f, 2f, 25f, 28f)),
            CameraTextSpan(1, 2, CameraRect(60f, 1f, 82f, 29f))))
        assertEquals(CameraRect(5f, 1f, 82f, 29f), cameraWordOutline(line, 0, 2).bounds)
        assertEquals(2, cameraTokenPolygons(line, 0, 2).size)
    }

    @Test fun aTiltedCompoundKeepsAnOrientedOutlineRatherThanAnAxisAlignedBox() {
        val line = CameraReadingLine("日本", CameraRect(6f, 20f, 110f, 60f), emptyList(), listOf(
            CameraTextSpan(0, 1, CameraRect(6f, 20f, 60f, 50f),
                listOf(CameraPoint(10f, 20f), CameraPoint(60f, 30f),
                    CameraPoint(56f, 50f), CameraPoint(6f, 40f))),
            CameraTextSpan(1, 2, CameraRect(56f, 30f, 110f, 60f),
                listOf(CameraPoint(60f, 30f), CameraPoint(110f, 40f),
                    CameraPoint(106f, 60f), CameraPoint(56f, 50f)))))
        val outline = cameraWordOutline(line, 0, 2)
        assertEquals(10f, outline.points[0].x, 0.01f)
        assertEquals(20f, outline.points[0].y, 0.01f)
        assertEquals(110f, outline.points[1].x, 0.01f)
        assertEquals(40f, outline.points[1].y, 0.01f)
        assertEquals(60f, outline.points[2].y, 0.01f)
    }

    @Test fun malformedOrMismatchedCornersFallBackToTheBoundingRectangle() {
        val rectangle = listOf(CameraPoint(6f, 20f), CameraPoint(110f, 20f),
            CameraPoint(110f, 60f), CameraPoint(6f, 60f))
        listOf(emptyList(), tilted.take(3), tilted.reversed().toMutableList().apply {
            this[0] = CameraPoint(Float.NaN, 20f)
        }, tilted.map { CameraPoint(it.x + 1000f, it.y) },
            listOf(tilted[0], tilted[2], tilted[1], tilted[3])).forEach { corners ->
            assertEquals(rectangle, cameraSpanPolygon(span.copy(corners = corners), 0, 2, false).points)
        }
    }

    @Test fun missingCharacterGeometryKeepsTheExistingHorizontalAndVerticalFallbacks() {
        val horizontal = CameraReadingLine("日本語", CameraRect(0f, 0f, 120f, 30f), emptyList())
        assertEquals(CameraRect(40f, 0f, 120f, 30f), cameraTokenBounds(horizontal, 1, 3))
        val vertical = horizontal.copy(bounds = CameraRect(0f, 0f, 30f, 120f))
        assertEquals(CameraRect(0f, 40f, 30f, 120f), cameraTokenBounds(vertical, 1, 3))
    }
}

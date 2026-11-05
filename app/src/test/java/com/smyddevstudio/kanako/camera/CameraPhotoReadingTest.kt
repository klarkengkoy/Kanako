package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import org.junit.Assert.*
import org.junit.Test

class CameraPhotoReadingTest {
    private val clip = ReadingBounds(0, 0, 400, 400)
    private val first = CameraPhotoReading(ReadingBounds(100, 200, 140, 230), false, "にほんご", "first", "日本語")
    private val second = CameraPhotoReading(ReadingBounds(150, 200, 180, 230), false, "ぎんこう", "second", "銀行")

    @Test fun groupedSourcesRemainSelectableWithoutAnyVisibleKanaLabel() {
        assertEquals("first", selectCameraPhotoReading(listOf(first, second), 110f, 215f, null))
        assertEquals("second", selectCameraPhotoReading(listOf(first, second), 165f, 215f, "first"))
    }

    @Test fun tappingTheSameSourceOrEmptySpaceRestoresAutomaticReadings() {
        assertNull(selectCameraPhotoReading(listOf(first, second), 110f, 215f, "first"))
        assertNull(selectCameraPhotoReading(listOf(first, second), 20f, 20f, "first"))
        assertNull(selectCameraPhotoReading(listOf(first, second), Float.NaN, 215f, "first"))
    }

    @Test fun calloutsStayAboveHorizontalWordsWhenThereIsRoom() {
        val placed = placeCameraPhotoCallout(first.anchor, clip, 120, 30, 2, false)!!
        assertEquals(198, placed.bottom)
        assertTrue(clip.contains(placed))
        assertNull(placed.intersection(first.anchor))
    }

    @Test fun verticalSourcesPreferCalloutsOnTheirRight() {
        val placed = placeCameraPhotoCallout(first.anchor, clip, 120, 30, 2, true)!!
        assertEquals(142, placed.left)
        assertNull(placed.intersection(first.anchor))
    }

    @Test fun edgeCalloutsStayInsideTheUsableViewport() {
        val source = ReadingBounds(2, 2, 30, 28)
        val viewport = ReadingBounds(0, 0, 180, 180)
        val placed = placeCameraPhotoCallout(source, viewport, 160, 60, 2, false)!!
        assertTrue(viewport.contains(placed))
        assertNull(source.intersection(placed))
    }

    @Test fun calloutsRejectInvalidAndOversizedBounds() {
        assertNull(placeCameraPhotoCallout(first.anchor, clip, 500, 30, 2, false))
        assertNull(placeCameraPhotoCallout(first.anchor, clip, 0, 30, 2, false))
        assertNull(placeCameraPhotoCallout(first.anchor, ReadingBounds(0, 0, 0, 0), 20, 30, 2, false))
    }
}

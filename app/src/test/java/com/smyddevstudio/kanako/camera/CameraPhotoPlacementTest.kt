package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.service.ReadingBounds
import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Test

class CameraPhotoPlacementTest {
    private val clip = ReadingBounds(0, 0, 1000, 1000)
    private fun measured(font: Float, widthFactor: Float = 2f) =
        PhotoRubyMeasurement(ceil(font * widthFactor).toInt(), ceil(font).toInt(), 1, Unit)

    @Test fun chosenSizeIsAMinimumAndLargeSourceCharactersCanGrowReadings() {
        val request = PhotoRubyRequest(ReadingBounds(200, 400, 280, 500))
        val placed = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor), 12f,
            measure = { _, font -> measured(font) }).single()!!
        assertEquals(50f, placed.fontPixels, 0.001f)
        assertNull(placed.bounds.intersection(request.anchor))
        assertEquals(request.anchor.left + request.anchor.right, placed.bounds.left + placed.bounds.right)
    }

    @Test fun smallSourceCharactersNeverShrinkBelowTheSelectedMinimum() {
        val request = PhotoRubyRequest(ReadingBounds(200, 400, 220, 420))
        val placed = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor), 24f,
            measure = { _, font -> measured(font) }).single()!!
        assertEquals(24f, placed.fontPixels, 0f)
    }

    @Test fun collidingMinimumReadingsAreDeferredUntilZoomProvidesSpace() {
        val requests = listOf(
            PhotoRubyRequest(ReadingBounds(100, 300, 120, 320)),
            PhotoRubyRequest(ReadingBounds(160, 300, 180, 320))
        )
        fun layout(minimum: Float) = placeCameraPhotoReadings(requests, clip, requests.map { it.anchor }, minimum,
            measure = { _, font -> measured(font, 4f) })
        assertTrue(layout(20f).all { it == null })
        val zoomed = layout(10f).map { requireNotNull(it) }
        assertEquals(2, zoomed.size)
        assertTrue(zoomed.all { it.fontPixels >= 10f })
        assertNull(zoomed[0].bounds.intersection(zoomed[1].bounds))
    }

    @Test fun growingOneReadingReservesRoomForItsNeighborsMinimum() {
        val requests = listOf(
            PhotoRubyRequest(ReadingBounds(100, 300, 120, 400)),
            PhotoRubyRequest(ReadingBounds(160, 300, 180, 400))
        )
        val result = placeCameraPhotoReadings(requests, clip, requests.map { it.anchor }, 10f,
            measure = { _, font -> measured(font, 3f) }).map { requireNotNull(it) }
        assertTrue(result.all { it.fontPixels in 10f..50f })
        assertTrue(result.any { it.fontPixels > 10f })
        assertNull(result[0].bounds.intersection(result[1].bounds))
    }

    @Test fun surroundingPrintedTextLimitsGrowthWithoutMovingToAnotherLane() {
        val request = PhotoRubyRequest(ReadingBounds(200, 200, 250, 240))
        val previousLine = ReadingBounds(100, 150, 400, 180)
        val blocked = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor, previousLine), 24f,
            measure = { _, font -> measured(font) }).single()
        assertNull(blocked)
        val zoomed = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor, previousLine), 12f,
            measure = { _, font -> measured(font) }).single()!!
        assertTrue(zoomed.fontPixels >= 12f && zoomed.fontPixels < 20f)
        assertNull(zoomed.bounds.intersection(previousLine))
        assertEquals(199, zoomed.bounds.bottom)
    }

    @Test fun verticalReadingsGrowBesideTheirSourceAndStayClearOfOtherColumns() {
        val request = PhotoRubyRequest(ReadingBounds(400, 200, 480, 300), vertical = true)
        val nextColumn = ReadingBounds(505, 100, 550, 500)
        val placed = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor, nextColumn), 12f,
            measure = { _, font -> PhotoRubyMeasurement(ceil(font).toInt(), ceil(font * 3).toInt(), 1, Unit) }).single()!!
        assertEquals(481, placed.bounds.left)
        assertTrue(placed.fontPixels in 12f..24f)
        assertNull(placed.bounds.intersection(nextColumn))
        assertEquals(500, placed.bounds.top + placed.bounds.bottom)
    }

    @Test fun imageEdgesCanUseTheOppositeSideButCannotCoverSourceText() {
        val request = PhotoRubyRequest(ReadingBounds(0, 0, 30, 20))
        val result = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor), 12f,
            measure = { _, font -> measured(font) }).single()!!
        assertTrue(clip.contains(result.bounds))
        assertTrue(result.bounds.top > request.anchor.bottom)
        assertNull(result.bounds.intersection(request.anchor))
    }

    @Test fun labelsThatCannotFitAtTheFloorAreOmittedWithoutWrapping() {
        val request = PhotoRubyRequest(ReadingBounds(100, 400, 120, 420))
        val placed = placeCameraPhotoReadings(listOf(request), clip, listOf(request.anchor), 20f,
            measure = { _, font -> measured(font, 100f) }).single()
        assertNull(placed)
    }

    @Test fun invalidMinimumDoesNotMeasureOrCreateLabels() {
        for (minimum in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val placed = placeCameraPhotoReadings<Unit>(listOf(PhotoRubyRequest(ReadingBounds(100, 100, 140, 140))),
                clip, emptyList(), minimum, measure = { _, _ -> error("Should not measure") })
            assertNull(placed.single())
        }
    }

    @Test fun cancellationStopsWorkBeforeMoreLabelsAreMeasured() {
        var measurements = 0
        val cancelled = IllegalStateException("cancelled")
        val error = runCatching {
            placeCameraPhotoReadings(listOf(PhotoRubyRequest(ReadingBounds(100, 100, 140, 140))),
                clip, emptyList(), 12f, measure = { _, font -> measurements++; measured(font) },
                checkCancellation = { throw cancelled })
        }.exceptionOrNull()
        assertSame(cancelled, error)
        assertEquals(0, measurements)
    }
}

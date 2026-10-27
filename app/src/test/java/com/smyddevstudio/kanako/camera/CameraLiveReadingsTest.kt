package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraLiveReadingsTest {
    private fun line(text: String = "日本語", x: Float = 100f, y: Float = 200f) =
        CameraReadingLine(text, CameraRect(x, y, x + 120, y + 30), listOf(FuriganaSegment(text, "にほんご")),
            listOf(CameraTextSpan(0, text.length, CameraRect(x, y, x + 120, y + 30))))

    @Test fun contentNeedsConfirmationAndChangesNoFasterThanSevenHundredMilliseconds() {
        val readings = CameraLiveReadings()
        assertTrue(readings.update(1000, 1010, listOf(line()))!!.isEmpty())
        assertEquals("日本語", readings.update(1160, 1170, listOf(line()))!!.single().text)
        assertTrue(readings.update(1320, 1330, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.update(1480, 1490, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.update(1640, 1650, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.update(1859, 1869, listOf(line("学校")))!!.isEmpty())
        assertEquals("学校", readings.update(1860, 1870, listOf(line("学校")))!!.single().text)
    }

    @Test fun matchingTextAndCharacterGeometryFollowEachFrameDuringTheContentInterval() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1010, listOf(line()))
        readings.update(1160, 1170, listOf(line()))
        val moved = line(x = 135f, y = 225f)
        val result = readings.update(1320, 1330, listOf(moved))!!.single()
        assertEquals(moved.bounds, result.bounds)
        assertEquals(moved.spans, result.spans)
        assertEquals("にほんご", result.segments.single().reading)
    }

    @Test fun transientMisrecognitionDoesNotPublishAndUnchangedLinesKeepMoving() {
        val readings = CameraLiveReadings()
        val initial = listOf(line(), line("学校", y = 300f))
        readings.update(1000, 1010, initial)
        readings.update(1160, 1170, initial)
        val transient = listOf(line("日木語"), line("学校", x = 110f, y = 310f))
        assertEquals(listOf(transient[1]), readings.update(1320, 1330, transient))
        // A single alternate value must not be published even after the 700 ms deadline.
        assertEquals(listOf(transient[1]), readings.update(1900, 1910, listOf(line("別物"), transient[1])))
    }

    @Test fun staleFutureAndOutOfOrderResultsCannotMoveOrReplaceReadings() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1000, listOf(line()))
        readings.update(1160, 1160, listOf(line()))
        assertNull(readings.update(1100, 1200, listOf(line(x = 500f))))
        assertNull(readings.update(1320, 1971, listOf(line())))
        assertNull(readings.update(2000, 1999, listOf(line())))
        assertEquals(line(x = 110f), readings.update(1320, 1330, listOf(line(x = 110f)))!!.single())
    }

    @Test fun expiryAndSessionResetRequireFreshConfirmation() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1000, listOf(line()))
        readings.update(1160, 1160, listOf(line()))
        assertTrue(readings.update(1811, 1811, listOf(line()))!!.isEmpty())
        assertEquals(1, readings.update(1971, 1971, listOf(line()))!!.size)
        readings.reset()
        assertTrue(readings.update(2000, 2000, listOf(line()))!!.isEmpty())
        assertEquals(1, readings.update(2160, 2160, listOf(line()))!!.size)
    }

    @Test fun duplicateTextUsesDistinctNearbyBoxesAndNeverJumpsAcrossThePage() {
        val readings = CameraLiveReadings()
        val initial = listOf(line(y = 200f), line(y = 400f))
        readings.update(1000, 1000, initial)
        readings.update(1160, 1160, initial)
        val reversed = listOf(line(y = 410f), line(y = 210f))
        assertEquals(listOf(reversed[1], reversed[0]), readings.update(1320, 1320, reversed))
        assertTrue(readings.update(1480, 1480, listOf(line(x = 800f, y = 800f)))!!.isEmpty())
    }

    @Test fun oneUnstableLineCannotBlockOtherConfirmedDocumentText() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1000, listOf(line(), line("学校", y = 300f)))
        val second = readings.update(1160, 1160, listOf(line(x = 110f), line("学技", y = 310f)))!!
        assertEquals(listOf(line(x = 110f)), second)
        val reordered = readings.update(1320, 1320, listOf(line("別物", y = 310f), line(x = 120f)))!!
        assertEquals(listOf(line(x = 120f)), reordered)
    }

    @Test fun aSingleDropoutHidesButDoesNotForgetConfirmedText() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1000, listOf(line()))
        readings.update(1160, 1160, listOf(line()))
        assertTrue(readings.update(1320, 1320, emptyList())!!.isEmpty())
        assertEquals(listOf(line(x = 110f)), readings.update(1480, 1480, listOf(line(x = 110f))))
    }

    @Test fun confirmedTextCanBeReacquiredAfterALargeMoveWithoutReusingItsOldPosition() {
        val readings = CameraLiveReadings()
        readings.update(1000, 1000, listOf(line()))
        readings.update(1160, 1160, listOf(line()))
        val moved = line(x = 600f, y = 800f)
        assertTrue(readings.update(1320, 1320, listOf(moved))!!.isEmpty())
        assertTrue(readings.update(1480, 1480, listOf(moved))!!.isEmpty())
        assertTrue(readings.update(1800, 1800, listOf(moved))!!.isEmpty())
        assertEquals(listOf(moved), readings.update(1860, 1860, listOf(moved)))
    }

    @Test fun oldResultAgeBoundaryIsInclusive() {
        val readings = CameraLiveReadings()
        assertNotNull(readings.update(1000, 1650, emptyList()))
        assertNull(readings.update(1100, 1751, emptyList()))
    }
}

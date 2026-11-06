package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraLiveReadingsTest {
    private fun CameraLiveReadings.observe(started: Long, now: Long, lines: List<CameraReadingLine>) =
        update(started, now, lines)?.map { it.copy(trackingKey = null) }

    private fun line(text: String = "日本語", x: Float = 100f, y: Float = 200f) =
        CameraReadingLine(text, CameraRect(x, y, x + 120, y + 30), listOf(FuriganaSegment(text, "にほんご")),
            listOf(CameraTextSpan(0, text.length, CameraRect(x, y, x + 120, y + 30))))

    @Test fun contentNeedsConfirmationAndChangesNoFasterThanSevenHundredMilliseconds() {
        val readings = CameraLiveReadings()
        assertTrue(readings.observe(1000, 1010, listOf(line()))!!.isEmpty())
        assertEquals("日本語", readings.observe(1160, 1170, listOf(line()))!!.single().text)
        assertTrue(readings.observe(1320, 1330, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.observe(1480, 1490, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.observe(1640, 1650, listOf(line("学校")))!!.isEmpty())
        assertTrue(readings.observe(1859, 1869, listOf(line("学校")))!!.isEmpty())
        assertEquals("学校", readings.observe(1860, 1870, listOf(line("学校")))!!.single().text)
    }

    @Test fun matchingTextAndCharacterGeometryFollowEachFrameDuringTheContentInterval() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1010, listOf(line()))
        readings.observe(1160, 1170, listOf(line()))
        val moved = line(x = 135f, y = 225f)
        val result = readings.observe(1320, 1330, listOf(moved))!!.single()
        assertEquals(moved.bounds, result.bounds)
        assertEquals(moved.spans, result.spans)
        assertEquals("にほんご", result.segments.single().reading)
    }

    @Test fun transientMisrecognitionDoesNotPublishAndUnchangedLinesKeepMoving() {
        val readings = CameraLiveReadings()
        val initial = listOf(line(), line("学校", y = 300f))
        readings.observe(1000, 1010, initial)
        readings.observe(1160, 1170, initial)
        val transient = listOf(line("日木語"), line("学校", x = 110f, y = 310f))
        assertEquals(listOf(transient[1]), readings.observe(1320, 1330, transient))
        // A single alternate value must not be published even after the 700 ms deadline.
        assertEquals(listOf(transient[1]), readings.observe(1900, 1910, listOf(line("別物"), transient[1])))
    }

    @Test fun staleFutureAndOutOfOrderResultsCannotMoveOrReplaceReadings() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1000, listOf(line()))
        readings.observe(1160, 1160, listOf(line()))
        assertNull(readings.observe(1100, 1200, listOf(line(x = 500f))))
        assertNull(readings.observe(1320, 1971, listOf(line())))
        assertNull(readings.observe(2000, 1999, listOf(line())))
        assertEquals(line(x = 110f), readings.observe(1320, 1330, listOf(line(x = 110f)))!!.single())
    }

    @Test fun expiryAndSessionResetRequireFreshConfirmation() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1000, listOf(line()))
        readings.observe(1160, 1160, listOf(line()))
        assertTrue(readings.expire(1960).isEmpty())
        assertTrue(readings.observe(1970, 1970, listOf(line()))!!.isEmpty())
        assertEquals(1, readings.observe(2130, 2130, listOf(line()))!!.size)
        readings.reset()
        assertTrue(readings.observe(2290, 2290, listOf(line()))!!.isEmpty())
        assertEquals(1, readings.observe(2450, 2450, listOf(line()))!!.size)
    }

    @Test fun duplicateTextUsesDistinctNearbyBoxesAndNeverJumpsAcrossThePage() {
        val readings = CameraLiveReadings()
        val initial = listOf(line(y = 200f), line(y = 400f))
        readings.observe(1000, 1000, initial)
        readings.observe(1160, 1160, initial)
        val reversed = listOf(line(y = 410f), line(y = 210f))
        assertEquals(listOf(reversed[1], reversed[0]), readings.observe(1320, 1320, reversed))
        assertTrue(readings.observe(1480, 1480, listOf(line(x = 800f, y = 800f)))!!.isEmpty())
    }

    @Test fun oneUnstableLineCannotBlockOtherConfirmedDocumentText() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1000, listOf(line(), line("学校", y = 300f)))
        val second = readings.observe(1160, 1160, listOf(line(x = 110f), line("学技", y = 310f)))!!
        assertEquals(listOf(line(x = 110f)), second)
        val reordered = readings.observe(1320, 1320, listOf(line("別物", y = 310f), line(x = 120f)))!!
        assertEquals(listOf(line(x = 120f)), reordered)
    }

    @Test fun aSingleDropoutHidesButDoesNotForgetConfirmedText() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1000, listOf(line()))
        readings.observe(1160, 1160, listOf(line()))
        assertTrue(readings.observe(1320, 1320, emptyList())!!.isEmpty())
        assertEquals(listOf(line(x = 110f)), readings.observe(1480, 1480, listOf(line(x = 110f))))
    }

    @Test fun confirmedTextCanBeReacquiredAfterALargeMoveWithoutReusingItsOldPosition() {
        val readings = CameraLiveReadings()
        readings.observe(1000, 1000, listOf(line()))
        readings.observe(1160, 1160, listOf(line()))
        val moved = line(x = 600f, y = 800f)
        assertTrue(readings.observe(1320, 1320, listOf(moved))!!.isEmpty())
        assertTrue(readings.observe(1480, 1480, listOf(moved))!!.isEmpty())
        assertTrue(readings.observe(1800, 1800, listOf(moved))!!.isEmpty())
        assertEquals(listOf(moved), readings.observe(1860, 1860, listOf(moved)))
    }

    @Test fun changedContextReadingHidesTheOldPronunciationUntilConfirmed() {
        val readings = CameraLiveReadings()
        val old = line("言").copy(segments = listOf(FuriganaSegment("言", "げん")))
        val corrected = old.copy(segments = listOf(FuriganaSegment("言", "い")))
        readings.observe(1000, 1010, listOf(old))
        assertEquals(listOf(old), readings.observe(1160, 1170, listOf(old)))
        assertTrue(readings.observe(1320, 1330, listOf(corrected))!!.isEmpty())
        assertTrue(readings.observe(1480, 1490, listOf(corrected))!!.isEmpty())
        assertTrue(readings.observe(1800, 1810, listOf(corrected))!!.isEmpty())
        assertEquals(listOf(corrected), readings.observe(1860, 1870, listOf(corrected)))
    }

    @Test fun unstableContextCannotPublishAReadingAfterTheContentDeadline() {
        val readings = CameraLiveReadings()
        val original = line("言").copy(segments = listOf(FuriganaSegment("言", "げん")))
        val transient = original.copy(segments = listOf(FuriganaSegment("言", "い")))
        val unknown = original.copy(segments = listOf(FuriganaSegment("言")))
        readings.observe(1000, 1000, listOf(original))
        readings.observe(1160, 1160, listOf(original))
        readings.observe(1700, 1700, listOf(original))
        assertTrue(readings.observe(1900, 1900, listOf(transient))!!.isEmpty())
        assertTrue(readings.observe(2060, 2060, listOf(unknown))!!.isEmpty())
        assertTrue(readings.observe(2220, 2220, listOf(transient))!!.isEmpty())
        assertEquals(listOf(original), readings.observe(2380, 2380, listOf(original)))
    }

    @Test fun oldResultAgeBoundaryIsInclusive() {
        val readings = CameraLiveReadings()
        assertNotNull(readings.observe(1000, 1650, emptyList()))
        assertNull(readings.observe(1100, 1751, emptyList()))
    }
}

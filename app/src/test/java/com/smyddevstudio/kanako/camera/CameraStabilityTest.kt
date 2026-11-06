package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraStabilityTest {
    private fun CameraLiveReadings.observe(started: Long, now: Long, lines: List<CameraReadingLine>) =
        update(started, now, lines)?.map { it.copy(trackingKey = null) }

    private fun line(text: String = "日本語", x: Float = 100f, y: Float = 200f) =
        CameraReadingLine(text, CameraRect(x, y, x + 120, y + 30), listOf(FuriganaSegment(text, "にほんご")),
            listOf(CameraTextSpan(0, text.length, CameraRect(x, y, x + 120, y + 30))))

    private fun confirmed(): CameraLiveReadings = CameraLiveReadings().also {
        it.update(1000, 1010, listOf(line(), line("学校", y = 300f)))
        it.update(1160, 1170, listOf(line(), line("学校", y = 300f)))
    }

    @Test fun isolatedDropoutUsesABoundedGraceWithoutExpiringTheStableNeighbor() {
        val readings = confirmed()
        val stable = line("学校", y = 300f)
        assertEquals(listOf(line(), stable), readings.observe(1320, 1330, listOf(stable)))
        assertEquals(1570L, readings.nextExpiryAt)
        assertEquals(listOf(stable), readings.expire(1570).map { it.copy(trackingKey = null) })
        assertEquals(2130L, readings.nextExpiryAt)
        assertTrue(readings.expire(2130).isEmpty())
        assertNull(readings.nextExpiryAt)
    }

    @Test fun slowRecognitionDoesNotConsumeTheMissingWordsGracePeriod() {
        val readings = CameraLiveReadings()
        val pair = listOf(line(), line("学校", y = 300f))
        readings.observe(1000, 1600, pair)
        readings.observe(1160, 1760, pair)
        assertEquals(pair, readings.observe(1320, 1900, listOf(pair[1])))
        assertEquals(2160L, readings.nextExpiryAt)
        assertEquals(listOf(pair[1]), readings.expire(2160).map { it.copy(trackingKey = null) })
        assertEquals(2700L, readings.nextExpiryAt)
    }

    @Test fun conflictingTextAndBlankScenesHideImmediately() {
        val readings = confirmed()
        val stable = line("学校", y = 300f)
        assertEquals(listOf(stable), readings.observe(1320, 1330, listOf(line("日木語"), stable)))
        assertTrue(readings.observe(1480, 1490, emptyList())!!.isEmpty())
        assertNull(readings.nextExpiryAt)
    }

    @Test fun aMovingNeighborCannotKeepAnAbsentLineAtItsOldPosition() {
        val readings = confirmed()
        val moving = line("学校", x = 140f, y = 300f)
        assertEquals(listOf(moving), readings.observe(1320, 1330, listOf(moving)))
    }

    @Test fun aRecoveredLineDoesNotBlinkDuringItsGracePeriod() {
        val readings = confirmed()
        val stable = line("学校", y = 300f)
        readings.observe(1320, 1330, listOf(stable))
        val recovered = line(x = 102f)
        val result = readings.observe(1340, 1350, listOf(recovered, stable))!!
        assertEquals(2, result.size)
        assertTrue(result[0].bounds.left in 100f..102f)
        assertEquals(2150L, readings.nextExpiryAt)
    }

    @Test fun freshGeometryIsPublishedWithoutAddingASecondSmoothingDelay() {
        val readings = confirmed()
        val stable = line("学校", y = 300f)
        val result = readings.observe(1320, 1330, listOf(line(x = 102f), stable))!!.first()
        assertEquals(102f, result.bounds.left, 0.001f)
        assertEquals(result.bounds, result.spans.single().bounds)
        val moved = line(x = 130f)
        assertEquals(moved, readings.observe(1480, 1490, listOf(moved, stable))!!.first())
    }

    @Test fun repeatedMissesCannotRenewGraceAndResetRemovesAllVisibleState() {
        val readings = confirmed()
        val stable = line("学校", y = 300f)
        readings.observe(1320, 1330, listOf(stable))
        assertEquals(listOf(stable), readings.observe(1480, 1490, listOf(stable)))
        readings.reset()
        assertNull(readings.nextExpiryAt)
        assertTrue(readings.expire(1500).isEmpty())
        assertTrue(readings.observe(1640, 1650, listOf(stable))!!.isEmpty())
    }
}

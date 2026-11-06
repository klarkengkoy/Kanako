package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test

class CameraLiveContinuityTest {
    private fun word(x: Float = 100f) = CameraReadingLine(
        "日本語", CameraRect(x, 200f, x + 120f, 230f),
        listOf(FuriganaSegment("日本語", "にほんご"))
    )

    @Test fun theRecordedSlowPipelineDoesNotSpendTheDisplayLeaseBeforeDrawing() {
        val tracker = CameraLiveReadings()
        assertTrue(tracker.update(1000, 1546, listOf(word()))!!.isEmpty())
        val confirmed = tracker.update(1550, 2096, listOf(word()))!!.single()
        assertEquals(2896L, tracker.nextExpiryAt)

        // The recorded blank occurred 683 ms after analysis started, despite a valid result.
        assertEquals(listOf(confirmed), tracker.expire(2233))
        val next = tracker.update(2100, 2702, listOf(word(102f)))!!.single()
        assertEquals(confirmed.trackingKey, next.trackingKey)
        assertEquals(102f, next.bounds.left, 0.001f)
        assertEquals(3502L, tracker.nextExpiryAt)
    }

    @Test fun consecutiveSlowResultsKeepOneIdentityThroughEachExpiryCheck() {
        val tracker = CameraLiveReadings()
        var started = 1000L
        var key: String? = null
        for ((index, latency) in listOf(546L, 602L, 438L, 573L, 650L, 315L).withIndex()) {
            val now = started + latency
            val before = tracker.expire(now - 1)
            if (key != null) assertEquals(key, before.single().trackingKey)
            val result = tracker.update(started, now, listOf(word()))!!
            if (index == 0) assertTrue(result.isEmpty()) else {
                val current = result.single().trackingKey
                if (key == null) key = current else assertEquals(key, current)
                assertTrue(tracker.nextExpiryAt!! > now)
            }
            started = now
        }
    }

    @Test fun aSlowSourceGapKeepsConfirmedTracksButResetsUnconfirmedCandidates() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1400, listOf(word()))
        val confirmed = tracker.update(1500, 1800, listOf(word()))!!.single()
        assertEquals(listOf(confirmed), tracker.expire(2399))
        // 700 ms between source starts exceeds the incoming-result age limit.
        val next = tracker.update(2200, 2400, listOf(word()))!!.single()
        assertEquals(confirmed.trackingKey, next.trackingKey)

        val unconfirmed = CameraLiveReadings()
        assertTrue(unconfirmed.update(1000, 1200, listOf(word()))!!.isEmpty())
        assertTrue(unconfirmed.update(1700, 1800, listOf(word()))!!.isEmpty())
        assertEquals(1, unconfirmed.update(1860, 1960, listOf(word()))!!.size)
    }

    @Test fun stoppedResultsExpireAtTheDisplayDeadlineAndNeedConfirmationAfterALongGap() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1650, listOf(word()))
        val confirmed = tracker.update(1650, 2300, listOf(word()))!!.single()
        val deadline = tracker.nextExpiryAt!!
        assertEquals(3100L, deadline)
        assertEquals(1650 + CAMERA_LIVE_TRACK_MAX_SOURCE_AGE_MS, deadline)
        assertEquals(listOf(confirmed), tracker.expire(deadline - 1))
        assertTrue(tracker.expire(deadline).isEmpty())
        assertNull(tracker.nextExpiryAt)
        assertTrue(tracker.update(3100, 3200, listOf(word()))!!.isEmpty())
        val reacquired = tracker.update(3260, 3360, listOf(word()))!!.single()
        assertNotEquals(confirmed.trackingKey, reacquired.trackingKey)
    }

    @Test fun rejectedResultsCannotRenewTheDisplayDeadline() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1500, listOf(word()))
        val confirmed = tracker.update(1500, 2000, listOf(word()))!!.single()
        val deadline = tracker.nextExpiryAt
        assertNull(tracker.update(2050, 2701, listOf(word(104f))))
        assertEquals(deadline, tracker.nextExpiryAt)
        assertEquals(listOf(confirmed), tracker.expire(2799))
        assertTrue(tracker.expire(2800).isEmpty())
    }

    @Test fun aFreshBlankChangedPronunciationOrLargeMoveStillClearsImmediately() {
        val original = word()
        val alternatives = listOf(
            emptyList(),
            listOf(original.copy(segments = listOf(FuriganaSegment("日本語", "にっぽんご")))),
            listOf(word(800f)),
            listOf(original.copy(text = "学校", segments = listOf(FuriganaSegment("学校", "がっこう"))))
        )
        for (replacement in alternatives) {
            val tracker = CameraLiveReadings()
            tracker.update(1000, 1500, listOf(original))
            assertEquals(1, tracker.update(1500, 2000, listOf(original))!!.size)
            // Still within the display lease, but fresh contradictory evidence takes priority.
            assertTrue(tracker.update(2050, 2550, replacement)!!.isEmpty())
            assertNull(tracker.nextExpiryAt)
        }
    }

    @Test fun aSlowMissCannotUseTheFullDisplayLeaseToKeepAnAbsentWord() {
        val tracker = CameraLiveReadings()
        val pair = listOf(word(), word(500f))
        tracker.update(1000, 1500, pair)
        val confirmed = tracker.update(1500, 2000, pair)!!
        val result = tracker.update(2050, 2600, listOf(pair[1]))!!
        // The last seen observation is 600 ms old: it exceeds the 400 ms missing-word grace,
        // even though the normal 800 ms display lease has not elapsed yet.
        assertEquals(listOf(confirmed[1].trackingKey), result.map { it.trackingKey })
    }

    @Test fun resetCancelsTheLongerDisplayLease() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1500, listOf(word()))
        val key = tracker.update(1500, 2000, listOf(word()))!!.single().trackingKey
        tracker.reset()
        assertNull(tracker.nextExpiryAt)
        assertTrue(tracker.expire(2001).isEmpty())
        assertTrue(tracker.update(2050, 2550, listOf(word()))!!.isEmpty())
        assertNotEquals(key, tracker.update(2550, 3050, listOf(word()))!!.single().trackingKey)
    }
}

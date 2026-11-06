package com.smyddevstudio.kanako.camera

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraReadingGroupsTest {
    private fun sentence(last: String = "学校", lastReading: String = "がっこう") =
        CameraReadingLine("日本$last", CameraRect(10f, 20f, 110f, 40f),
            listOf(FuriganaSegment("日本", "にほん"), FuriganaSegment(last, lastReading)),
            listOf(CameraTextSpan(0, 2, CameraRect(10f, 20f, 50f, 40f)),
                CameraTextSpan(2, 4, CameraRect(70f, 20f, 110f, 40f))))

    @Test fun changingOneWordKeepsTheNeighborVisibleWithTheSameIdentity() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1010, listOf(sentence()))
        val before = tracker.update(1160, 1170, listOf(sentence()))!!
        val after = tracker.update(1320, 1330, listOf(sentence("学技", "がくぎ")))!!
        assertEquals(listOf("日本"), after.map { it.text })
        assertEquals(before.first().trackingKey, after.single().trackingKey)
        assertEquals(CameraRect(10f, 20f, 50f, 40f), after.single().bounds)
        assertEquals(0 to 2, after.single().spans.single().let { it.start to it.end })
    }

    @Test fun duplicateReadingsKeepDistinctKeysWhenOcrOrderChanges() {
        val tracker = CameraLiveReadings()
        val word = cameraReadingGroups(listOf(sentence())).first()
        val second = word.copy(bounds = CameraRect(10f, 200f, 110f, 220f), spans = emptyList())
        tracker.update(1000, 1010, listOf(word, second))
        val before = tracker.update(1160, 1170, listOf(word, second))!!
        val after = tracker.update(1320, 1330, listOf(second, word))!!
        assertEquals(2, before.map { it.trackingKey }.toSet().size)
        assertEquals(before.map { it.trackingKey }, after.map { it.trackingKey })
    }

    @Test fun oneMissedFrameRecoversAtNormalCadenceWithoutBlinking() {
        val tracker = CameraLiveReadings()
        val groups = cameraReadingGroups(listOf(sentence()))
        tracker.update(1000, 1010, listOf(sentence()))
        val before = tracker.update(1160, 1170, listOf(sentence()))!!
        assertEquals(2, tracker.update(1320, 1330, listOf(groups[1]))!!.size)
        val recovered = tracker.update(1480, 1490, listOf(sentence()))!!
        assertEquals(before.map { it.trackingKey }, recovered.map { it.trackingKey })
        assertEquals(2290L, tracker.nextExpiryAt)
    }

    @Test fun recoveryAcrossTheContentDeadlineKeepsTheAlreadyConfirmedGroupVisible() {
        for (now in listOf(1869L, 1870L, 1871L)) {
            val tracker = CameraLiveReadings()
            val original = listOf(sentence())
            val stableNeighbor = cameraReadingGroups(original)[1]
            tracker.update(1000, 1010, original)
            val before = tracker.update(1160, 1170, original)!!
            tracker.update(1320, 1330, original)
            tracker.update(1480, 1490, original)
            assertEquals(2, tracker.update(1640, 1650, listOf(stableNeighbor))!!.size)

            val recovered = tracker.update(now - 10, now, original)!!
            assertEquals("Recovery at $now", before.map { it.trackingKey }, recovered.map { it.trackingKey })
            assertEquals(listOf("日本", "学校"), recovered.map { it.text })
            assertEquals(before.map { it.trackingKey },
                tracker.update(2040, 2050, original)!!.map { it.trackingKey })
        }
    }

    @Test fun aTemporaryOmissionDoesNotDelayConfirmationOfANewWord() {
        val tracker = CameraLiveReadings()
        val original = listOf(sentence())
        val words = cameraReadingGroups(original)
        tracker.update(1000, 1010, original)
        tracker.update(1160, 1170, original)
        tracker.update(1320, 1330, original)
        tracker.update(1480, 1490, original)
        tracker.update(1640, 1650, original)
        tracker.update(1800, 1810, original)
        tracker.update(1960, 1970, listOf(words[1]))
        val newWord = CameraReadingLine("銀行", CameraRect(10f, 80f, 60f, 100f),
            listOf(FuriganaSegment("銀行", "ぎんこう")))
        val observed = listOf(words[1], newWord)
        val first = tracker.update(2120, 2130, observed)!!
        assertEquals(listOf("学校"), first.map { it.text })
        val second = tracker.update(2280, 2290, observed)!!
        assertEquals(listOf("学校", "銀行"), second.map { it.text })
    }

    @Test fun splitVerticalGroupsKeepTheirReadingDirectionEvenForSquareCharacters() {
        val line = CameraReadingLine("日本", CameraRect(0f, 0f, 20f, 50f),
            listOf(FuriganaSegment("日", "ひ"), FuriganaSegment("本", "ほん")),
            listOf(CameraTextSpan(0, 1, CameraRect(0f, 0f, 20f, 20f)),
                CameraTextSpan(1, 2, CameraRect(0f, 30f, 20f, 50f))))
        val groups = cameraReadingGroups(listOf(line))
        assertTrue(groups.all { it.isVertical })
        assertEquals(CameraRect(0f, 30f, 20f, 50f), cameraTokenBounds(groups[1], 0, 1))
    }

    @Test fun matchingKanjiAndPronunciationKeepTheirIdentityWhenOkuriganaChanges() {
        val tracker = CameraLiveReadings()
        fun verb(text: String, reading: String) = CameraReadingLine(text, CameraRect(0f, 0f, 40f, 20f),
            listOf(FuriganaSegment(text, reading)), listOf(
                CameraTextSpan(0, 1, CameraRect(0f, 0f, 20f, 20f)),
                CameraTextSpan(1, 2, CameraRect(20f, 0f, 40f, 20f))))
        tracker.update(1000, 1010, listOf(verb("読み", "よみ")))
        val before = tracker.update(1160, 1170, listOf(verb("読み", "よみ")))!!.single()
        val after = tracker.update(1320, 1330, listOf(verb("読む", "よむ")))!!.single()
        assertEquals("読", after.text)
        assertEquals("よ", after.segments.single().reading)
        assertEquals(before.trackingKey, after.trackingKey)
    }

    @Test fun sessionResetDoesNotReuseTheOldVisualIdentity() {
        val tracker = CameraLiveReadings()
        tracker.update(1000, 1010, listOf(sentence()))
        val before = tracker.update(1160, 1170, listOf(sentence()))!!.first().trackingKey
        tracker.reset()
        assertTrue(tracker.update(1320, 1330, listOf(sentence()))!!.isEmpty())
        val after = tracker.update(1480, 1490, listOf(sentence()))!!.first().trackingKey
        assertNotEquals(before, after)
    }
}

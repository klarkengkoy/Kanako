package com.smyddevstudio.kanako.service

import org.junit.Assert.*
import org.junit.Test

class ReadingSelectionTest {
    private val first = ReadingTarget("first", ReadingBounds(100, 100, 120, 140), ReadingBounds(60, 70, 160, 96))
    private val second = ReadingTarget("second", ReadingBounds(125, 100, 145, 140), ReadingBounds(85, 70, 185, 96))

    @Test fun tappingKanjiSelectsItsPartnerAndChangesOnlyDrawingOrder() {
        val targets = listOf(first, second)
        val selected = selectReading(targets, 110f, 120f, null)
        assertEquals("first", selected)
        assertEquals(listOf(1, 0), readingDrawOrder(targets, selected))
        assertEquals(first.label, targets[0].label)
        assertEquals("second", selectReading(targets, 135f, 120f, selected))
    }

    @Test fun repeatedTapsCycleOverlappingReadingsAndEmptySpaceClearsSelection() {
        val targets = listOf(first, second)
        assertEquals("second", selectReading(targets, 100f, 80f, null))
        assertEquals("first", selectReading(targets, 100f, 80f, "second"))
        assertEquals("second", selectReading(targets, 100f, 80f, "first"))
        assertNull(selectReading(targets, 0f, 0f, "first"))
    }

    @Test fun sourceKanjiTakesPriorityOverAnotherWordsCoveringLabel() {
        val covering = second.copy(label = first.anchor)
        assertEquals("first", selectReading(listOf(first, covering), 110f, 120f, null))
    }

    @Test fun removedSelectionDoesNotDropAnyReadings() {
        assertEquals(listOf(0, 1), readingDrawOrder(listOf(first, second), "removed"))
        assertNull(selectReading(emptyList(), 10f, 10f, "removed"))
    }

    @Test fun matchingVisibleKanaIsTrimmedBeforeCenteringOnKanji() {
        assertEquals(KanjiAnnotation(0, 1, "よ"), kanjiAnnotation("読み", "よみ"))
        assertEquals(KanjiAnnotation(1, 2, "ちゃ"), kanjiAnnotation("お茶", "おちゃ"))
        assertEquals(KanjiAnnotation(0, 1, "い"), kanjiAnnotation("言いました", "いいました"))
        assertEquals(KanjiAnnotation(0, 3, "とりあつか"), kanjiAnnotation("取り扱い", "とりあつかい"))
    }

    @Test fun unmatchedKanaAndUnknownReadingsAreNotGuessed() {
        assertEquals(KanjiAnnotation(0, 2, "べつ"), kanjiAnnotation("読み", "べつ"))
        assertEquals(KanjiAnnotation(0, 3, "にほんご"), kanjiAnnotation("日本語", "にほんご"))
    }
}

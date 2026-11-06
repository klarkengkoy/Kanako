package com.smyddevstudio.kanako.service

import com.smyddevstudio.kanako.data.tokenizer.JapaneseReadingEngine
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.data.tokenizer.ReadingEngineFixtures
import org.junit.Assert.*
import org.junit.Test

class ReadingContextTest {

    private val calls = mutableListOf<String>()
    private val tokenizer = JapaneseTextTokenizer {
        val engine = ReadingEngineFixtures.sudachi
        JapaneseReadingEngine { text ->
            calls.add(text)
            engine.tokenize(text)
        }
    }
    private val context = ReadingContext(tokenizer)
    private data class Line(val text: String, val bounds: ReadingBounds)
    private fun line(text: String, top: Int = 0, left: Int = 0) =
        Line(text, ReadingBounds(left, top, left + 600, top + 40))
    private fun read(vararg blocks: List<Line>) = context.tokenize(blocks.toList(), { it.text }, { it.bounds })

    @Test fun wrappedVerbUsesTheFollowingLineWithoutMovingItsText() {
        val lines = listOf(line("子どもはそう言"), line("いました。", 60))
        val readings = read(lines)
        assertEquals(lines.map { it.text }, readings.map { it.joinToString("") { segment -> segment.text } })
        assertEquals("い", readings[0].last().reading)
        assertTrue(readings[1].all { it.reading == null })
        assertEquals(listOf("子どもはそう言いました。"), calls)
    }

    @Test fun changedNeighborReanalyzesAnOtherwiseIdenticalLine() {
        val first = line("子どもはそう言")
        assertEquals("い", read(listOf(first, line("いました。", 60)))[0].last().reading)
        val changed = listOf(first, line("葉を読む。", 60))
        val actual = read(changed)
        val expected = JapaneseTextTokenizer {
            ReadingEngineFixtures.sudachi
        }.tokenizeLines(changed.map { it.text })
        assertEquals(expected, actual)
        assertNotEquals("い", actual[0].last().reading)
        assertEquals(2, calls.size)
    }

    @Test fun separateBlocksNeverBecomeOneWord() {
        val a = line("子どもはそう言")
        val b = line("いました。", 60)
        val actual = read(listOf(a), emptyList(), listOf(b))
        assertEquals(listOf(tokenizer.tokenize(a.text), tokenizer.tokenize(b.text)), actual)
        assertEquals(listOf(a.text, b.text), calls.take(2))
    }

    @Test fun distantColumnsHeadingsAndVerticalLinesStaySeparate() {
        val first = line("子どもはそう言")
        val next = "いました。"
        listOf(
            line(next, 60, 400),
            line(next, 200),
            Line(next, ReadingBounds(0, 60, 600, 140)),
            Line(next, ReadingBounds(0, 60, 40, 500))
        ).forEach { second ->
            calls.clear()
            ReadingContext(tokenizer).tokenize(listOf(listOf(first, second)), { it.text }, { it.bounds })
            assertEquals(listOf(first.text, next), calls)
        }
    }

    @Test fun sentenceEndingPreventsUnrelatedContext() {
        listOf("本を読みました。", "本を読みました。  ", "本を読みました！\t", "「読みました」　").forEach { sentence ->
            calls.clear()
            ReadingContext(tokenizer).tokenize(
                listOf(listOf(line(sentence), line("子どもはそう言", 60))), { it.text }, { it.bounds }
            )
            assertEquals(listOf(sentence, "子どもはそう言"), calls)
        }
    }

    @Test fun geometryCanChangeWithoutRetokenizingAnUnchangedGroup() {
        val lines = listOf(line("子どもはそう言"), line("いました。", 60))
        val expected = read(lines)
        val moved = lines.map { it.copy(bounds = it.bounds.moved(37, 91)) }
        assertEquals(expected, read(moved))
        assertEquals(1, calls.size)
    }

    @Test fun changingGroupingOrLineBoundariesCannotReuseStaleProjections() {
        val a = line("子どもはそう言")
        val b = line("いました。", 60)
        read(listOf(a, b))
        val separated = read(listOf(a, b.copy(bounds = b.bounds.moved(0, 300))))
        assertEquals(listOf(tokenizer.tokenize(a.text), tokenizer.tokenize(b.text)), separated)

        val newLines = listOf(line("子どもはそう"), line("言いました。", 60))
        val projected = read(newLines)
        assertEquals(newLines.map { it.text }, projected.map { it.joinToString("") { segment -> segment.text } })
        assertEquals("いい", projected[1].first().reading)
    }

    @Test fun cacheRetainsOnlyThePreviousFrameAndHandlesDuplicateGroups() {
        val a = listOf(line("日本語を読む。"))
        val b = listOf(line("学校へ行く。"))
        read(a, a)
        assertEquals(listOf(a.single().text), calls)
        read(b)
        read(a)
        assertEquals(listOf(a.single().text, b.single().text, a.single().text), calls)
        read(emptyList())
        read(a)
        assertEquals(4, calls.size)
    }

    @Test fun whitespaceSymbolsAndUnknownKanjiKeepTheirExactLineOffsets() {
        val lines = listOf(line("  猫🐈とＡＢＣ。"), line("  \u9fff と日本語。 ", 60))
        val readings = read(lines)
        assertEquals(lines.map { it.text }, readings.map { it.joinToString("") { segment -> segment.text } })
        assertTrue(readings[1].filter { "\u9fff" in it.text }.all { it.reading == null })
        assertTrue(readings[1].any { it.text == "日本語" && it.reading == "にほんご" })
    }

    @Test fun splitKanjiCompoundKeepsSurfaceWithoutGuessingPronunciation() {
        val lines = listOf(line("学"), line("校に行く", 60))
        val readings = read(lines)
        assertEquals(lines.map { it.text }, readings.map { it.joinToString("") { segment -> segment.text } })
        assertNull(readings[0].single().reading)
        assertNull(readings[1].first().reading)
    }
}

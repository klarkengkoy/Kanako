package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class LineProjectionTest {

    private val tokenizer = JapaneseTextTokenizer {
        ReadingEngineFixtures.sudachi
    }

    @Test fun emptyRowsAndAllSplitPositionsMatchTheOriginalProjection() {
        val texts = listOf("", "そう言いました。", "学校で本を一冊読む。", " 猫🐈とＡＢＣと\u9fff。 ")
        texts.forEach { text ->
            for (split in 0..text.length) {
                verify(listOf("", text.take(split), "", text.drop(split), ""))
            }
        }
    }

    @Test fun unevenAndRepeatedRowPartitionsPreserveEverySourceOffset() {
        val text = "子どもはそう言いました。木の下に二人で座り、本を一冊読みました。 猫🐈も来た。"
        val random = Random(1729)
        repeat(60) {
            val cuts = (listOf(0, text.length) + List(10) { random.nextInt(text.length + 1) }).sorted()
            verify(cuts.zipWithNext { a, b -> text.substring(a, b) })
        }
    }

    @Test fun densePagesDoNotLoseRowsOrTokens() {
        verify(List(128) { if (it % 3 == 0) "" else "日本語を読む。" })
    }

    private fun verify(lines: List<String>) {
        val actual = tokenizer.tokenizeLines(lines)
        assertEquals(lines, actual.map { it.joinToString("") { segment -> segment.text } })
        assertEquals(originalProjection(lines), actual)
    }

    // Keep the original full scan as a differential oracle for the cursor-based implementation.
    private fun originalProjection(lines: List<String>): List<List<FuriganaSegment>> {
        val segments = tokenizer.tokenize(lines.joinToString(""))
        var lineStart = 0
        return lines.map { line ->
            val lineEnd = lineStart + line.length
            var offset = 0
            val result = segments.mapNotNull { segment ->
                val start = offset
                offset += segment.text.length
                val from = maxOf(lineStart, start) - start
                val to = minOf(lineEnd, offset) - start
                if (from >= to) return@mapNotNull null
                val surface = segment.text.substring(from, to)
                val reading = if (from == 0 && to == segment.text.length) segment.reading else {
                    val before = segment.text.take(from)
                    val after = segment.text.drop(to)
                    val raw = segment.reading
                    if (!hasKanji(surface) || hasKanji(before) || hasKanji(after) ||
                        raw == null || !raw.startsWith(before) || !raw.endsWith(after)
                    ) null else raw.substring(before.length, raw.length - after.length)
                }
                FuriganaSegment(surface, reading)
            }
            lineStart = lineEnd
            result
        }
    }

    private fun hasKanji(text: String) = text.any {
        it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' || it == '\u3005' || it in '\uFA0E'..'\uFA29'
    }
}

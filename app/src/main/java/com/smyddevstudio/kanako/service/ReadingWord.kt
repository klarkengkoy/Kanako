package com.smyddevstudio.kanako.service

import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

internal data class ReadingWord(
    val start: Int, val end: Int,
    val readingStart: Int, val readingEnd: Int,
    val reading: String, val source: String
)

internal fun readingWords(text: String, segments: List<FuriganaSegment>, checkCancellation: () -> Unit = {}): List<ReadingWord> {
    val formatter = JapaneseTextTokenizer()
    var cursor = 0
    return buildList {
        segments.forEach { segment ->
            checkCancellation()
            val start = text.indexOf(segment.text, cursor)
            if (start < 0) return@forEach
            cursor = start + segment.text.length
            val raw = segment.reading ?: return@forEach
            if (segment.text.none { it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' || it == '々' }) return@forEach
            val annotation = kanjiAnnotation(segment.text, formatter.formatReading(raw, ScriptType.KANA))
            if (annotation.reading.isEmpty()) return@forEach
            val suffixEnd = (cursor + segment.highlightSuffixLength.coerceAtLeast(0)).coerceAtMost(text.length)
            val end = if (text.substring(cursor, suffixEnd).all { it in 'ぁ'..'ゖ' || it == 'ー' }) suffixEnd else cursor
            add(ReadingWord(start, end, start + annotation.start, start + annotation.end,
                annotation.reading, text.substring(start, end)))
        }
    }
}

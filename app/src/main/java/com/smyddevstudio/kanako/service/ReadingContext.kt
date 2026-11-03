package com.smyddevstudio.kanako.service

import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

// Worker-confined. Retain only the previous frame's groups, never OCR images or geometry.
internal class ReadingContext(private val tokenizer: JapaneseTextTokenizer = JapaneseTextTokenizer()) {
    private var cachedGroups = emptyMap<List<String>, List<List<FuriganaSegment>>>()

    // Results follow the original block/line order; source spans and coordinates stay with callers.
    fun <T> tokenize(
        blocks: List<List<T>>, text: (T) -> String, bounds: (T) -> ReadingBounds
    ): List<List<FuriganaSegment>> {
        val nextCache = mutableMapOf<List<String>, List<List<FuriganaSegment>>>()
        val result = buildList {
            blocks.forEach { lines ->
                val groups = mutableListOf<MutableList<T>>()
                lines.forEach { line ->
                    val previous = groups.lastOrNull()?.lastOrNull()
                    if (previous != null && continuesReadingLine(text(previous), bounds(previous), bounds(line))) {
                        groups.last().add(line)
                    } else groups.add(mutableListOf(line))
                }
                groups.forEach { group ->
                    // Line boundaries matter even when concatenated text is identical.
                    val key = group.map(text)
                    val readings = nextCache[key] ?: cachedGroups[key] ?: tokenizer.tokenizeLines(key)
                    nextCache[key] = readings
                    addAll(readings)
                }
            }
        }
        cachedGroups = nextCache
        return result
    }
}

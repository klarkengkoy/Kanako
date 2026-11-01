package com.smyddevstudio.kanako.service

internal data class ReadingTarget(val key: String, val anchor: ReadingBounds, val label: ReadingBounds)

internal fun selectReading(
    targets: List<ReadingTarget>, x: Float, y: Float, selected: String?
): String? {
    fun ReadingBounds.hit() = x >= left && x < right && y >= top && y < bottom
    val sources = targets.filter { it.anchor.hit() }
    val candidates = sources.ifEmpty { targets.filter { it.label.hit() } }
    if (candidates.isEmpty()) return null
    val current = candidates.indexOfFirst { it.key == selected }
    return if (current < 0) candidates.last().key else candidates[(current + 1) % candidates.size].key
}

internal fun readingDrawOrder(targets: List<ReadingTarget>, selected: String?): List<Int> =
    targets.indices.filter { targets[it].key != selected } +
        targets.indices.filter { targets[it].key == selected }

internal data class KanjiAnnotation(val start: Int, val end: Int, val reading: String)

internal fun kanjiAnnotation(text: String, reading: String): KanjiAnnotation {
    fun Char.kanji() = this in '\u3400'..'\u9FFF' || this in '\uF900'..'\uFAFF' || this == '々'
    fun Char.kana() = this in '\u3041'..'\u3096' || this in '\u30A1'..'\u30F6' || this == 'ー'
    fun String.hiragana() = map { if (it in '\u30A1'..'\u30F6') (it.code - 0x60).toChar() else it }.joinToString("")
    val first = text.indexOfFirst { it.kanji() }
    val last = text.indexOfLast { it.kanji() }
    if (first < 0) return KanjiAnnotation(0, text.length, reading)
    val prefix = text.take(first)
    val suffix = text.drop(last + 1)
    if (!prefix.all { it.kana() } || !suffix.all { it.kana() }) return KanjiAnnotation(0, text.length, reading)
    val before = prefix.hiragana()
    val after = suffix.hiragana()
    if (!reading.startsWith(before) || !reading.endsWith(after) ||
        reading.length <= before.length + after.length
    ) return KanjiAnnotation(0, text.length, reading)
    return KanjiAnnotation(first, last + 1, reading.substring(before.length, reading.length - after.length))
}

package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

class JapaneseTextTokenizer internal constructor(
    private val tokenizerSupplier: () -> JapaneseReadingEngine
) {
    constructor() : this({ ActiveJapaneseReadingEngine.engine })

    private val tokenizer: JapaneseReadingEngine get() = tokenizerSupplier()

    companion object {
        // Evidence-backed spatial nouns only; abstract "指導の下" must retain モト.
        private val SPATIAL_MODIFIERS = setOf("木", "机")
        private val LOCATIVE_PARTICLES = setOf("で", "に", "へ", "から", "まで")

        private val DIGRAPHS = mapOf(
            "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
            "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
            "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
            "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
            "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
            "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
            "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
            "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
            "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
            "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
            "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
            "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
            "しぇ" to "she", "ちぇ" to "che", "つぁ" to "tsa", "つぇ" to "tse", "つぉ" to "tso",
            "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
            "じぇ" to "je", "てぃ" to "ti", "でぃ" to "di", "でゅ" to "dyu",
            "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo", "くぁ" to "kwa"
        )

        private val MONOGRAPHS = mapOf(
            "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
            "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
            "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
            "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
            "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
            "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
            "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
            "や" to "ya", "ゆ" to "yu", "よ" to "yo",
            "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
            "わ" to "wa", "ゐ" to "wi", "ゑ" to "we", "を" to "wo", "ん" to "n",
            "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
            "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
            "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
            "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
            "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
            "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o"
        )
    }

    fun tokenize(
        text: String,
        scriptType: ScriptType = ScriptType.KANA
    ): List<FuriganaSegment> {
        if (text.isEmpty()) return emptyList()

        val tokens = tokenizer.tokenize(text)
        return buildList {
            var index = 0
            var cursor = 0
            while (index < tokens.size) {
                val token = tokens[index]
                val surface = token.surface
                if (cursor < token.position) add(FuriganaSegment(text.substring(cursor, token.position)))
                check(token.position >= cursor && token.position + surface.length <= text.length &&
                    text.substring(token.position, token.position + surface.length) == surface
                ) { "Tokenizer must preserve original source spans" }
                val next = tokens.getOrNull(index + 1)
                if (token.isNumber) {
                    var numberEnd = index
                    while (tokens.getOrNull(numberEnd + 1)?.let {
                        it.isNumber && it.position == tokens[numberEnd].position + tokens[numberEnd].surface.length
                    } == true) numberEnd++
                    val counter = tokens.getOrNull(numberEnd + 1)
                    val number = tokens.subList(index, numberEnd + 1).joinToString("") { it.reading.orEmpty() }
                    val contracted = when {
                        number.endsWith("イチ") || number.endsWith("ハチ") -> number.dropLast(1) + "ッ"
                        number.endsWith("ジュウ") -> number.dropLast(1) + "ッ"
                        else -> null
                    }
                    if (counter?.surface == "冊" && counter.reading == "サツ" &&
                        (counter.partOfSpeechLevel1 == "接尾辞" || "接尾" in counter.partOfSpeech) &&
                        counter.position == tokens[numberEnd].position + tokens[numberEnd].surface.length &&
                        contracted != null
                    ) {
                        cursor = counter.position + counter.surface.length
                        add(FuriganaSegment(text.substring(token.position, cursor),
                            formatReading(contracted + "サツ", scriptType)))
                        index = numberEnd + 2
                        continue
                    }
                }
                val afterCounter = tokens.getOrNull(index + 2)
                val ordinaryCounter = afterCounter == null || afterCounter.partOfSpeechLevel1 in
                    listOf("助詞", "記号", "補助記号") || afterCounter.surface in listOf("組", "連れ")
                val precedingNumber = text.getOrNull(token.position - 1)?.let {
                    it in "0123456789０１２３４５６７８９一二三四五六七八九十百千万億第"
                } == true
                if (surface in listOf("一", "二") && next?.surface == "人" &&
                    next.position == token.position + surface.length && ordinaryCounter && !precedingNumber
                ) {
                    add(FuriganaSegment(surface + "人",
                        formatReading(if (surface == "一") "ヒトリ" else "フタリ", scriptType)))
                    cursor = next.position + 1
                    index += 2
                    continue
                }
                val possessive = tokens.getOrNull(index - 1)
                val modifier = tokens.getOrNull(index - 2)
                val spatialBelow = surface == "下" && token.reading == "モト" &&
                    !token.isPersonName && modifier?.surface in SPATIAL_MODIFIERS &&
                    modifier?.isPersonName == false && possessive?.surface == "の" &&
                    modifier.position + modifier.surface.length == possessive.position &&
                    possessive.position + possessive.surface.length == token.position &&
                    next?.surface in LOCATIVE_PARTICLES &&
                    next?.position == token.position + surface.length
                if (surface == "木の下" && next?.surface in LOCATIVE_PARTICLES) {
                    add(FuriganaSegment("木", formatReading("キ", scriptType)))
                    add(FuriganaSegment("の"))
                    add(FuriganaSegment("下", formatReading("シタ", scriptType)))
                } else {
                    val raw = if (spatialBelow) "シタ" else token.reading
                    add(FuriganaSegment(surface,
                        if (surface.containsKanji() && !raw.isNullOrEmpty() && raw != "*" && !raw.containsKanji())
                            formatReading(raw, scriptType) else null))
                }
                cursor = token.position + surface.length
                index++
            }
            if (cursor < text.length) add(FuriganaSegment(text.substring(cursor)))
        }
    }

    internal fun tokenizeLines(lines: List<String>): List<List<FuriganaSegment>> {
        val joined = lines.joinToString("")
        val segments = tokenize(joined)
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
                    if (!surface.containsKanji() || before.containsKanji() || after.containsKanji() ||
                        raw == null || !raw.startsWith(before) || !raw.endsWith(after)
                    ) null else raw.substring(before.length, raw.length - after.length)
                }
                FuriganaSegment(surface, reading)
            }
            lineStart = lineEnd
            result
        }
    }

    fun formatReading(rawReading: String, scriptType: ScriptType): String {
        val hiragana = katakanaToHiragana(rawReading)
        return when (scriptType) {
            ScriptType.KANA -> hiragana
            ScriptType.ROMAJI -> kanaToRomaji(hiragana)
        }
    }

    fun katakanaToHiragana(katakana: String): String {
        return katakana.map { char ->
            if (char in '\u30A1'..'\u30F6') {
                (char.code - 0x60).toChar()
            } else {
                char
            }
        }.joinToString("")
    }

    fun hiraganaToKatakana(hiragana: String): String {
        return hiragana.map { char ->
            if (char in '\u3041'..'\u3096') {
                (char.code + 0x60).toChar()
            } else {
                char
            }
        }.joinToString("")
    }

    fun kanaToRomaji(hiraganaInput: String): String {
        val hiragana = katakanaToHiragana(hiraganaInput)
        val sb = StringBuilder()
        var i = 0
        val len = hiragana.length

        while (i < len) {
            val char = hiragana[i]

            if (char == 'ん' && i + 1 < len && hiragana[i + 1] in "あいうえおやゆよ") {
                sb.append("n'")
                i++
                continue
            }

            // Sokuon check (っ / ッ)
            if (char == 'っ' || char == 'ッ') {
                val nextRomaji = getNextRomaji(hiragana, i + 1)
                if (!nextRomaji.isNullOrEmpty()) {
                    if (nextRomaji.startsWith("ch")) {
                        sb.append('t')
                    } else {
                        val firstChar = nextRomaji[0]
                        if (firstChar in 'a'..'z') {
                            sb.append(firstChar)
                        }
                    }
                }
                i++
                continue
            }

            // Check 2-character digraph
            if (i + 1 < len) {
                val pair = hiragana.substring(i, i + 2)
                val romajiPair = DIGRAPHS[pair]
                if (romajiPair != null) {
                    sb.append(romajiPair)
                    i += 2
                    continue
                }
            }

            // Check 1-character monograph
            val single = char.toString()
            val romajiSingle = MONOGRAPHS[single]
            if (romajiSingle != null) {
                sb.append(romajiSingle)
                i++
                continue
            }

            // Chōonpu 'ー'
            if (char == 'ー') {
                if (sb.isNotEmpty()) {
                    val lastChar = sb.last()
                    if (lastChar in listOf('a', 'e', 'i', 'o', 'u')) {
                        sb.append(lastChar)
                    } else {
                        sb.append('-')
                    }
                } else {
                    sb.append('-')
                }
                i++
                continue
            }

            // Fallback for symbols/punctuation/latin
            sb.append(char)
            i++
        }

        return sb.toString()
    }

    private fun getNextRomaji(text: String, startIndex: Int): String? {
        if (startIndex >= text.length) return null
        if (startIndex + 1 < text.length) {
            val pair = text.substring(startIndex, startIndex + 2)
            if (DIGRAPHS.containsKey(pair)) return DIGRAPHS[pair]
        }
        val single = text[startIndex].toString()
        return MONOGRAPHS[single]
    }

    private fun String.containsKanji(): Boolean {
        return any { char ->
            char in '\u4E00'..'\u9FFF' ||
            char in '\u3400'..'\u4DBF' ||
            char == '\u3005' ||
            char in '\uFA0E'..'\uFA29'
        }
    }
}

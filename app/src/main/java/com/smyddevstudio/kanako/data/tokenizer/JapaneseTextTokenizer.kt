package com.smyddevstudio.kanako.data.tokenizer

import com.atilika.kuromoji.ipadic.Tokenizer
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.components.FuriganaSegment

class JapaneseTextTokenizer(
    private val tokenizerSupplier: () -> Tokenizer = { defaultTokenizer }
) {
    private val tokenizer: Tokenizer get() = tokenizerSupplier()

    companion object {
        private val defaultTokenizer by lazy { Tokenizer() }

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
        return tokens.map { token ->
            val surface = token.surface ?: ""
            val rawReading = token.reading

            val reading = if (surface.containsKanji() && !rawReading.isNullOrEmpty() && rawReading != "*") {
                formatReading(rawReading, scriptType)
            } else {
                null
            }

            FuriganaSegment(
                text = surface,
                reading = reading
            )
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

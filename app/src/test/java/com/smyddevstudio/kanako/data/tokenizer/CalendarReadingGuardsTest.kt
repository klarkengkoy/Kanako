package com.smyddevstudio.kanako.data.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarReadingGuardsTest {
    private fun token(surface: String, position: Int, reading: String?, person: Boolean = false) =
        JapaneseToken(surface, position, reading, listOf("名詞") + if (person) listOf("人名") else emptyList())

    private fun read(input: String, tokens: List<JapaneseToken>) =
        JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }.tokenize(input)

    @Test fun splitCorrectionKeepsNamesHonorificsGapsAndOtherReadings() {
        val cases = listOf(
            "一昨日" to listOf(token("一昨", 0, "イッサク", true), token("日", 2, "ニチ")),
            "一昨日" to listOf(token("一昨", 0, "イッサク"), token("日", 2, "ニチ", true)),
            "一昨日さん" to listOf(token("一昨", 0, "イッサク"), token("日", 2, "ニチ"), token("さん", 3, "サン")),
            "一昨 日" to listOf(token("一昨", 0, "イッサク"), token("日", 3, "ニチ")),
            "一昨日" to listOf(token("一昨", 0, "イッサク"), token("日", 2, "ジツ")),
            "一昨日" to listOf(token("一昨", 0, null), token("日", 2, "ニチ"))
        )
        cases.forEach { (input, tokens) ->
            val result = read(input, tokens)
            assertEquals(input, result.joinToString("") { it.text })
            assertEquals(tokens.map { it.surface }.filter { it.isNotBlank() },
                result.map { it.text }.filter { it.isNotBlank() })
        }
    }

    @Test fun wholeWordDictionaryChoicesRemainUnchanged() {
        listOf("オトトイ", "イッサクジツ").forEach { reading ->
            val result = read("一昨日", listOf(token("一昨日", 0, reading)))
            assertEquals(JapaneseTextTokenizer { error("No dictionary needed") }.katakanaToHiragana(reading),
                result.single().reading)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun mergingCannotSkipTheSecondSourceSpanCheck() {
        read("一昨月", listOf(token("一昨", 0, "イッサク"), token("日", 2, "ニチ")))
    }
}

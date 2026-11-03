package com.smyddevstudio.kanako.data.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterReadingGuardsTest {
    private fun number(text: String, position: Int, reading: String?, person: Boolean = false) =
        JapaneseToken(text, position, reading, listOf("名詞", "数詞") + if (person) listOf("人名") else emptyList())

    private fun counter(reading: String = "ポン", suffix: Boolean = true, person: Boolean = false) =
        JapaneseToken("本", 1, reading, (if (suffix) listOf("接尾辞", "助数詞") else listOf("名詞", "一般")) +
            if (person) listOf("人名") else emptyList())

    private fun project(input: String, tokens: List<JapaneseToken>) =
        JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }.tokenize(input)

    @Test fun nameTagsAndNonCounterPartsOfSpeechPreventMerging() {
        listOf(
            listOf(number("一", 0, "イチ", person = true), counter()),
            listOf(number("一", 0, "イチ"), counter(person = true)),
            listOf(number("一", 0, "イチ"), counter(suffix = false)),
            listOf(number("一", 0, "イチ"), counter(reading = "モト"))
        ).forEach { tokens ->
            val result = project("一本", tokens)
            assertEquals(listOf("一", "本"), result.map { it.text })
        }
    }

    @Test fun incompleteNumericReadingsCannotProduceAPartialCombinedReading() {
        val result = project("二十一杯", listOf(
            number("二", 0, null), number("十", 1, "ジュウ"), number("一", 2, "イチ"),
            JapaneseToken("杯", 3, "ハイ", listOf("接尾辞", "助数詞"))
        ))
        assertEquals(listOf("二", "十", "一", "杯"), result.map { it.text })
    }

    @Test(expected = IllegalStateException::class)
    fun mergingCannotBypassOriginalTokenSurfaceValidation() {
        project("一冊", listOf(number("一", 0, "イチ"), counter()))
    }

    @Test fun anHonorificAfterTheCounterKeepsTheOriginalAnalysis() {
        val result = project("一本さん", listOf(number("一", 0, "イチ"), counter(),
            JapaneseToken("さん", 2, "サン", listOf("接尾辞"))))
        assertEquals(listOf("一", "本", "さん"), result.map { it.text })
    }
}

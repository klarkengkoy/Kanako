package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CounterPronunciationTest(private val engineName: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun engines() = listOf(arrayOf("sudachi"), arrayOf("kuromoji"))
    }
    private val engine get() = if (engineName == "sudachi") ReadingEngineFixtures.sudachi else ReadingEngineFixtures.kuromoji
    private val tokenizer = JapaneseTextTokenizer { engine }

    @Test fun quantityContextsUseTheCounterSoundChanges() {
        val numbers = listOf("一", "二", "三", "四", "六", "八", "十")
        val readings = listOf(
            listOf("いっぽん", "にほん", "さんぼん", "よんほん", "ろっぽん", "はっぽん", "じゅっぽん"),
            listOf("いっぴき", "にひき", "さんびき", "よんひき", "ろっぴき", "はっぴき", "じゅっぴき"),
            listOf("いっぱい", "にはい", "さんばい", "よんはい", "ろっぱい", "はっぱい", "じゅっぱい")
        )
        listOf("本", "匹", "杯").forEachIndexed { counterIndex, counter ->
            numbers.forEachIndexed { numberIndex, number ->
                val input = when (counter) {
                    "本" -> "鉛筆を${number}${counter}買う。"
                    "匹" -> "猫が${number}${counter}いる。"
                    else -> "水を${number}${counter}飲む。"
                }
                val count = readings[counterIndex][numberIndex]
                val expected = when (counter) {
                    "本" -> "えんぴつを${count}かう。"
                    "匹" -> "ねこが${count}いる。"
                    else -> "みずを${count}のむ。"
                }
                val result = tokenizer.tokenize(input)
                assertEquals(input, result.joinToString("") { it.text })
                assertEquals(input, expected, result.joinToString("") { it.reading ?: it.text })
            }
        }
    }

    @Test fun largerNumbersUseTheSameEndingRule() {
        mapOf("鉛筆を二十一本買う。" to "えんぴつをにじゅういっぽんかう。",
            "猫が十三匹いる。" to "ねこがじゅうさんびきいる。",
            "水を二十杯飲む。" to "みずをにじゅっぱいのむ。").forEach { (input, reading) ->
            val result = tokenizer.tokenize(input)
            assertEquals(input, result.joinToString("") { it.text })
            assertEquals(input, reading, result.joinToString("") { it.reading ?: it.text })
        }
    }

    @Test fun namesAndSeparatedNumbersKeepTheEngineChoice() {
        listOf("三本さん", "八本さん", "本田さん", "一 本").forEach { input ->
            val expected = engine.tokenize(input).joinToString("") { token ->
                if (token.surface.any { it in '\u4e00'..'\u9fff' }) {
                    token.reading?.takeUnless { it == "*" || it.any { ch -> ch in '\u4e00'..'\u9fff' } }
                        ?.let(tokenizer::katakanaToHiragana) ?: token.surface
                } else token.surface
            }
            val result = tokenizer.tokenize(input)
            assertEquals(input, result.joinToString("") { it.text })
            assertEquals(input, expected, result.joinToString("") { it.reading ?: it.text })
        }
    }

    @Test fun correctedCounterSupportsRomajiWithoutChangingTheSource() {
        val result = tokenizer.tokenize("鉛筆を一本買う。", ScriptType.ROMAJI)
        assertEquals("鉛筆を一本買う。", result.joinToString("") { it.text })
        assertTrue(result.toString(), result.any { it.text == "一本" && it.reading == "ippon" })
    }
}

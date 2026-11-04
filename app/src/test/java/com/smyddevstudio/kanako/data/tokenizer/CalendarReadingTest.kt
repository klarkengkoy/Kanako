package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CalendarReadingTest(private val engineName: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun engines() = listOf(arrayOf("sudachi"), arrayOf("kuromoji"))
    }
    private val tokenizer = JapaneseTextTokenizer {
        if (engineName == "sudachi") ReadingEngineFixtures.sudachi else ReadingEngineFixtures.kuromoji
    }

    @Test fun dayBeforeYesterdayUsesAnEstablishedWholeWordReading() {
        listOf("一昨日", "一昨日は雨だった。").forEach { input ->
            val result = tokenizer.tokenize(input)
            assertEquals(input, result.joinToString("") { it.text })
            val reading = result.joinToString("") { it.reading ?: it.text }
            val suffix = if (input == "一昨日") "" else "はあめだった。"
            assertTrue(result.toString(), reading in setOf("おととい$suffix", "いっさくじつ$suffix"))
        }
    }

    @Test fun calendarReadingSupportsRomaji() {
        val result = tokenizer.tokenize("一昨日", ScriptType.ROMAJI)
        assertEquals("一昨日", result.single().text)
        assertTrue(result.toString(), result.single().reading in setOf("ototoi", "issakujitsu"))
    }

    @Test fun wrappedWholeWordDoesNotInventPerCharacterReadings() {
        val lines = listOf("一昨", "日")
        val result = tokenizer.tokenizeLines(lines)
        assertEquals(lines, result.map { row -> row.joinToString("") { it.text } })
        assertTrue(result.flatten().toString(), result.flatten().all { it.reading == null })
    }
}

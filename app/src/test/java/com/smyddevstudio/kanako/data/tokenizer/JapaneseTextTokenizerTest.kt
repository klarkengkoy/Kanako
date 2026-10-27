package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseTextTokenizerTest {

    private val tokenizer = JapaneseTextTokenizer()

    @Test
    fun testTokenizeSingleWords() {
        val gakkouSegments = tokenizer.tokenize("学校")
        assertEquals(1, gakkouSegments.size)
        assertEquals("学校", gakkouSegments[0].text)
        assertEquals("がっこう", gakkouSegments[0].reading)

        val nihongoSegments = tokenizer.tokenize("日本語")
        assertEquals(1, nihongoSegments.size)
        assertEquals("日本語", nihongoSegments[0].text)
        assertEquals("にほんご", nihongoSegments[0].reading)

        val nekoSegments = tokenizer.tokenize("猫")
        assertEquals(1, nekoSegments.size)
        assertEquals("猫", nekoSegments[0].text)
        assertEquals("ねこ", nekoSegments[0].reading)
    }

    @Test
    fun testTokenizeSentence() {
        val sentence = "吾輩は猫である"
        val segments = tokenizer.tokenize(sentence)

        val surfaces = segments.map { it.text }
        assertTrue(surfaces.contains("吾輩"))
        assertTrue(surfaces.contains("は"))
        assertTrue(surfaces.contains("猫"))

        val wagahaiSegment = segments.first { it.text == "吾輩" }
        assertEquals("わがはい", wagahaiSegment.reading)

        val haSegment = segments.first { it.text == "は" }
        assertNull(haSegment.reading)

        val nekoSegment = segments.first { it.text == "猫" }
        assertEquals("ねこ", nekoSegment.reading)
    }

    @Test
    fun testScriptTypeConversions() {
        val word = "学校"

        val kanaSeg = tokenizer.tokenize(word, ScriptType.KANA).first()
        assertEquals("がっこう", kanaSeg.reading)

        val romajiSeg = tokenizer.tokenize(word, ScriptType.ROMAJI).first()
        assertEquals("gakkou", romajiSeg.reading)
    }

    @Test
    fun testKanjiDetectionAndNonKanjiReadings() {
        val hiraganaText = "はでのです"
        val hiraganaSegments = tokenizer.tokenize(hiraganaText)
        hiraganaSegments.forEach { seg ->
            assertNull("Pure Hiragana token '${seg.text}' should not have reading annotation", seg.reading)
        }

        val katakanaText = "テスト"
        val katakanaSegments = tokenizer.tokenize(katakanaText)
        katakanaSegments.forEach { seg ->
            assertNull("Pure Katakana token '${seg.text}' should not have reading annotation", seg.reading)
        }

        val punctuationText = "。！？"
        val punctuationSegments = tokenizer.tokenize(punctuationText)
        punctuationSegments.forEach { seg ->
            assertNull("Punctuation token '${seg.text}' should not have reading annotation", seg.reading)
        }
    }

    @Test
    fun formattingPreviewReadingsDoesNotLoadTheDictionary() {
        val formatter = JapaneseTextTokenizer { error("Formatting must not request the dictionary") }
        assertEquals("にほんご", formatter.formatReading("ニホンゴ", ScriptType.KANA))
        assertEquals("nihongo", formatter.formatReading("ニホンゴ", ScriptType.ROMAJI))
        assertEquals("yomu", formatter.formatReading("ヨム", ScriptType.ROMAJI))
    }

    @Test
    fun testHelperConversions() {
        assertEquals("がっこう", tokenizer.katakanaToHiragana("ガッコウ"))
        assertEquals("ガッコウ", tokenizer.hiraganaToKatakana("がっこう"))
        assertEquals("gakkou", tokenizer.kanaToRomaji("がっこう"))
        assertEquals("nihongo", tokenizer.kanaToRomaji("にほんご"))
        assertEquals("kissaten", tokenizer.kanaToRomaji("きっさてん"))
        assertEquals("kin'en", tokenizer.kanaToRomaji("きんえん"))
    }
}

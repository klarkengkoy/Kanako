package com.smyddevstudio.kanako.data.tokenizer

import com.smyddevstudio.kanako.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@RunWith(Parameterized::class)
class JapaneseTextTokenizerTest(private val engineName: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines() = listOf(arrayOf("sudachi"), arrayOf("kuromoji"))
    }

    private val tokenizer = JapaneseTextTokenizer {
        if (engineName == "sudachi") ReadingEngineFixtures.sudachi else ReadingEngineFixtures.kuromoji
    }

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

        // Full Sudachi recognizes the book title as one named compound; Kuromoji splits it.
        assertEquals(sentence, segments.joinToString("") { it.text })
        assertEquals("わがはいはねこである", segments.joinToString("") { it.reading ?: it.text })
        assertTrue(segments.filter { it.text == "は" }.all { it.reading == null })
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
    fun storyUsesTheTreeLocationReading() {
        val tree = tokenizer.tokenize("すると、大きな木の下で、泣いている子どもを見つけました。")
        assertTrue(tree.toString(), tree.any { it.text == "木" && it.reading == "き" })
    }

    @Test
    fun storyUsesTheIrregularPeopleCounter() {
        val people = tokenizer.tokenize("二人は公園の中を探しました。")
        assertTrue(people.toString(), people.any { it.text == "二人" && it.reading == "ふたり" })
    }

    @Test
    fun correctlyRecognizedHeardUsesItsVerbReading() {
        val heard = tokenizer.tokenize("とミミは聞きました。")
        assertTrue(heard.toString(), heard.any { it.text == "聞き" && it.reading == "きき" })
    }

    @Test
    fun wrappedVerbKeepsContextAndProjectsItsReadingToTheKanji() {
        val lines = listOf("子どもは「大切なボールをなくしてしまったんだ」と言", "いました。")
        val result = tokenizer.tokenizeLines(lines)
        assertEquals(lines, result.map { it.joinToString("") { segment -> segment.text } })
        assertEquals("い", result[0].last().reading)
        assertTrue(result[1].all { it.reading == null })
    }

    @Test
    fun countersNamesAndWhitespaceKeepTheirMeaning() {
        listOf("十二人", "二人称", "一人前", "木下さん").forEach { text ->
            val result = tokenizer.tokenize(text)
            assertEquals(text, result.joinToString("") { it.text })
            assertFalse(result.toString(), result.any { it.reading == "ふたり" || it.reading == "き" })
        }
        val text = "  二人 は 公園 にいました。 "
        assertEquals(text, tokenizer.tokenize(text).joinToString("") { it.text })
        assertEquals("futari", tokenizer.tokenize("二人は", ScriptType.ROMAJI).first().reading)
    }

    @Test
    fun splitKanjiCompoundDoesNotInventReadingsForEachHalf() {
        val result = tokenizer.tokenizeLines(listOf("学", "校に行く"))
        assertNull(result[0].single().reading)
        assertNull(result[1].first().reading)
    }

    @Test
    fun spatialReadingsPreserveNamesAndAbstractExpressions() {
        listOf("木の下で休む。", "机の下で眠る。", "机の下に猫がいる。").forEach { text ->
            val result = tokenizer.tokenize(text)
            assertEquals(text, result.joinToString("") { it.text })
            assertTrue(result.toString(), result.any { it.text == "下" && it.reading == "した" })
        }
        val abstractText = "先生の指導の下で学ぶ。"
        val engine = if (engineName == "sudachi") ReadingEngineFixtures.sudachi else ReadingEngineFixtures.kuromoji
        val originalBelow = engine.tokenize(abstractText).first { it.surface == "下" }.reading!!
        val abstract = tokenizer.tokenize(abstractText)
        assertEquals(tokenizer.formatReading(originalBelow, ScriptType.KANA), abstract.first { it.text == "下" }.reading)
        val uncommonName = tokenizer.tokenize("木の下さん")
        assertEquals(if (engineName == "sudachi") "きのしたさん" else "このしたさん",
            uncommonName.joinToString("") { it.reading ?: it.text })
        val name = tokenizer.tokenize("木下さんが来た。")
        assertTrue(name.toString(), name.any { it.text == "木下" && it.reading == "きのした" })
    }

    @Test
    fun bookCountersContractTheNumberWithoutChangingTheOriginalText() {
        mapOf("一冊" to "いっさつ", "八冊" to "はっさつ", "十冊" to "じゅっさつ",
            "十一冊" to "じゅういっさつ", "二十冊" to "にじゅっさつ").forEach { (text, reading) ->
            val result = tokenizer.tokenize(text)
            assertEquals(text, result.joinToString("") { it.text })
            assertEquals(result.toString(), reading, result.joinToString("") { it.reading ?: it.text })
        }
        assertTrue(tokenizer.tokenize("冊子を読む").none { it.reading == "いっさつ" })
    }

    @Test
    fun normalizationAndLookalikesPreserveOriginalSurfaces() {
        listOf("ニコニコと二人で笑う。", "ロボットと入口。", "猫🐈とＡＢＣ。",
            "  学校\nへ行く。 ", "㍿と㍻と㈱", "漢字（かんじ）を読む。", "ニ人と入ロ").forEach { text ->
            assertEquals(text, tokenizer.tokenize(text).joinToString("") { it.text })
        }
        listOf("ニコニコ", "ロボット", "エアコン", "カメラ", "タクシー", "ハンカチ").forEach { text ->
            assertTrue(tokenizer.tokenize(text).all { it.reading == null })
        }
    }

    @Test
    fun unknownKanjiDoNotBecomeTheirOwnReading() {
        val text = "\u9fff"
        val result = tokenizer.tokenize(text)
        assertEquals(text, result.joinToString("") { it.text })
        assertTrue(result.toString(), result.all { it.reading == null })
    }

    @Test
    fun sharedEngineReturnsStableSnapshotsAcrossWorkers() {
        val texts = listOf("二人で木の下へ行く。", "日本語を勉強する。", "そう言いました。", "一冊買う。")
        val expected = texts.associateWith { tokenizer.tokenize(it) }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val jobs = List(24) { index -> Callable {
                val text = texts[index % texts.size]
                assertEquals(expected.getValue(text), tokenizer.tokenize(text))
            } }
            workers.invokeAll(jobs).forEach { it.get() }
        } finally {
            workers.shutdownNow()
        }
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

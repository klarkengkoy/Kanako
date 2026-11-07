package com.smyddevstudio.kanako.service

import com.smyddevstudio.kanako.data.tokenizer.JapaneseReadingEngine
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.data.tokenizer.JapaneseToken
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ReadingWordTest {
    private fun tokens(vararg parts: Triple<String, String, String>): List<JapaneseToken> {
        var position = 0
        return parts.map { (surface, reading, partOfSpeech) ->
            JapaneseToken(surface, position, reading, listOf(partOfSpeech)).also { position += surface.length }
        }
    }

    @Test fun verbAuxiliariesJoinTheHighlightWithoutAddingKanaAnnotations() {
        val tokens = tokens(Triple("読み", "ヨミ", "動詞"), Triple("まし", "マシ", "助動詞"),
            Triple("た", "タ", "助動詞"), Triple("を", "ヲ", "助詞"), Triple("本", "ホン", "名詞"))
        val tokenizer = JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }
        val segments = tokenizer.tokenize("読みましたを本")
        assertEquals(3, segments.first().highlightSuffixLength)
        assertTrue(segments.filter { it.text in listOf("まし", "た", "を") }.all { it.reading == null })
        val word = readingWords("読みましたを本", segments).first()
        assertEquals(ReadingWord(0, 5, 0, 1, "よ", "読みました"), word)
    }

    @Test fun nounsParticlesWhitespaceAndNeighboringWordsAreNotSwallowed() {
        val tokens = tokens(Triple("本", "ホン", "名詞"), Triple("です", "デス", "助動詞"),
            Triple("を", "ヲ", "助詞"), Triple("読む", "ヨム", "動詞"))
        val tokenizer = JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }
        val words = readingWords("本ですを読む", tokenizer.tokenize("本ですを読む"))
        assertEquals(listOf("本", "読む"), words.map { it.source })
        assertEquals(0, tokenizer.tokenize("本ですを読む").first().highlightSuffixLength)
        val separated = listOf(JapaneseToken("読み", 0, "ヨミ", listOf("動詞")),
            JapaneseToken("ます", 3, "マス", listOf("助動詞")))
        val spaced = JapaneseTextTokenizer { JapaneseReadingEngine { separated } }.tokenize("読み ます")
        assertEquals("読み", readingWords("読み ます", spaced).single().source)
    }

    @Test fun wrappingClampsTheWordHighlightToItsOwnRow() {
        val tokens = tokens(Triple("読み", "ヨミ", "動詞"), Triple("ます", "マス", "助動詞"))
        val tokenizer = JapaneseTextTokenizer { JapaneseReadingEngine { tokens } }
        val rows = tokenizer.tokenizeLines(listOf("読みま", "す"))
        assertEquals("読みま", readingWords("読みま", rows[0]).single().source)
        assertTrue(readingWords("す", rows[1]).isEmpty())
    }

    @Test fun kanaEndingTapsUseTheWordBoundsWhileTheReadingStaysOnKanji() {
        val kanji = ReadingBounds(100, 100, 120, 120)
        val word = ReadingBounds(100, 100, 180, 120)
        val group = ReadingGroup(kanji, false, "よ", "word", "読みます", word)
        assertEquals("word", selectReadingGroup(listOf(group), 170f, 110f, null))
        assertNull(selectReadingGroup(listOf(group), 190f, 110f, null))
        assertNull(selectReadingGroup(listOf(group), 170f, 110f, "word"))
        assertEquals(kanji, group.anchor)
    }

    @Test fun unrecognizedOrMismatchedSegmentsDoNotInventAHighlight() {
        assertTrue(readingWords("別の文章", listOf(FuriganaSegment("読み", "よみ"))).isEmpty())
        assertTrue(readingWords("かな", listOf(FuriganaSegment("かな", "かな"))).isEmpty())
    }
}

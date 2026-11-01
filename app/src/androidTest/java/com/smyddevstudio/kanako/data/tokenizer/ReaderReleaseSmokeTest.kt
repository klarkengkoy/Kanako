package com.smyddevstudio.kanako.data.tokenizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Public API only, so the same test can also smoke-test an optimized release APK. */
@RunWith(AndroidJUnit4::class)
class ReaderReleaseSmokeTest {
    @Test
    fun packagedReaderLoadsPluginsAndReadsOriginalJapaneseText() {
        val tokenizer = JapaneseTextTokenizer()
        val text = "二人で木の下へ行き、一冊買いました。"
        val segments = tokenizer.tokenize(text)
        assertEquals(text, segments.joinToString("") { it.text })
        assertTrue(segments.toString(), segments.any { it.text == "二人" && it.reading == "ふたり" })
        assertTrue(segments.toString(), segments.any { it.text == "一冊" && it.reading == "いっさつ" })
    }
}

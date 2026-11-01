package com.smyddevstudio.kanako.data.tokenizer

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.BuildConfig
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated app-local tests: no activity, camera, accessibility capture, or source app access. */
@RunWith(AndroidJUnit4::class)
class ReadingEngineIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun configuredEngineReadsOfflineAndPreservesSourceText() {
        val engine = ActiveJapaneseReadingEngine.engine
        if (BuildConfig.READER_ENGINE == "sudachi") assertTrue(engine is SudachiReadingEngine)
        else assertTrue(engine is KuromojiReadingEngine)
        val tokenizer = JapaneseTextTokenizer()
        listOf("二人で木の下へ行く。", "机の下で眠る。", "木下さんが来た。",
            "本を一冊買う。", "猫🐈とＡＢＣ。", "ニ人と入ロ", "\u9fff").forEach { text ->
            val segments = tokenizer.tokenize(text)
            assertEquals(text, segments.joinToString("") { it.text })
        }
        assertEquals("ふたり", tokenizer.tokenize("二人で").first().reading)
        assertTrue(tokenizer.tokenize("机の下で").any { it.text == "下" && it.reading == "した" })
        assertTrue(tokenizer.tokenize("一冊買う。").any { it.text == "一冊" && it.reading == "いっさつ" })
        if (BuildConfig.READER_ENGINE == "sudachi") {
            assertTrue(tokenizer.tokenize("二人組が来た。").any { it.text == "二人組" && it.reading == "ふたりぐみ" })
        }
    }

    @Test
    fun wrappedVerbRetainsReadingAndOffsets() {
        val lines = listOf("子どもが言", "いました。")
        val result = JapaneseTextTokenizer().tokenizeLines(lines)
        assertEquals(lines, result.map { it.joinToString("") { segment -> segment.text } })
        assertEquals("い", result[0].last().reading)
        assertTrue(result[1].all { it.reading == null })
    }

    @Test
    fun activeEngineCanServeConcurrentWorkers() {
        val tokenizer = JapaneseTextTokenizer()
        val expected = tokenizer.tokenize("二人で日本語を読む。")
        val workers = Executors.newFixedThreadPool(2)
        try {
            workers.invokeAll(List(16) { Callable {
                assertEquals(expected, tokenizer.tokenize("二人で日本語を読む。"))
            } }).forEach { it.get() }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun dictionaryInstallationRepairsTruncationAndReusesVerifiedCopy() {
        if (BuildConfig.READER_ENGINE != "sudachi") return
        val ownedDirectory = File(context.cacheDir, "sudachi-installation-test")
        val isolatedContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir(): File = ownedDirectory
        }
        try {
            val folder = File(ownedDirectory, "sudachi").apply { mkdirs() }
            val damaged = File(folder, "system-${BuildConfig.SUDACHI_DICTIONARY_VERSION}.dic")
            damaged.writeText("interrupted installation")
            val installed = SudachiDictionaryFile.prepare(isolatedContext)
            assertEquals(BuildConfig.SUDACHI_DICTIONARY_BYTES, installed.length())
            val modified = installed.lastModified()
            assertEquals(installed, SudachiDictionaryFile.prepare(isolatedContext))
            assertEquals(modified, installed.lastModified())
            assertFalse(File(folder, installed.name + ".tmp").exists())
        } finally {
            ownedDirectory.deleteRecursively()
        }
    }
}

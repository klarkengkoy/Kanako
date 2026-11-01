package com.smyddevstudio.kanako.data.tokenizer

import android.content.Context
import com.atilika.kuromoji.ipadic.Tokenizer as KuromojiTokenizer
import com.smyddevstudio.kanako.BuildConfig
import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.Tokenizer as SudachiTokenizer

internal data class JapaneseToken(
    val surface: String,
    val position: Int,
    val reading: String?,
    val partOfSpeech: List<String>
) {
    val partOfSpeechLevel1: String get() = partOfSpeech.firstOrNull().orEmpty()
    val isNumber: Boolean get() = "数詞" in partOfSpeech || "数" in partOfSpeech
    val isPersonName: Boolean get() = "人名" in partOfSpeech
}

internal fun interface JapaneseReadingEngine {
    fun tokenize(text: String): List<JapaneseToken>
}

internal class SudachiReadingEngine(dictionary: Dictionary) : JapaneseReadingEngine {
    // Dictionary is shared for the process lifetime; tokenizers have mutable state per worker.
    private val workers = ThreadLocal.withInitial { dictionary.tokenizer() }

    override fun tokenize(text: String): List<JapaneseToken> =
        requireNotNull(workers.get()).tokenize(SudachiTokenizer.SplitMode.C, text).mapNotNull {
            // Normalization expansions can yield zero-length original spans. Do not annotate them.
            if (it.begin() == it.end()) null
            else JapaneseToken(it.surface(), it.begin(), it.readingForm(), it.partOfSpeech().toList())
        }
}

internal class KuromojiReadingEngine(
    private val tokenizer: KuromojiTokenizer = KuromojiTokenizer()
) : JapaneseReadingEngine {
    @Synchronized
    override fun tokenize(text: String): List<JapaneseToken> = tokenizer.tokenize(text).map {
        JapaneseToken(it.surface, it.position, it.reading,
            listOf(it.partOfSpeechLevel1, it.partOfSpeechLevel2, it.partOfSpeechLevel3))
    }
}

internal object ActiveJapaneseReadingEngine {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    // Resolving the engine is deferred until the first worker actually requests tokenization.
    val engine: JapaneseReadingEngine by lazy {
        when (BuildConfig.READER_ENGINE) {
            "sudachi" -> {
                val context = checkNotNull(applicationContext) { "Reader application is not initialized" }
                val dictionary = SudachiDictionaryFile.prepare(context)
                SudachiReadingEngine(Dictionary.load(Config.defaultConfig().systemDictionary(dictionary.toPath())))
            }
            "kuromoji" -> KuromojiReadingEngine()
            else -> error("Unsupported reader engine")
        }
    }
}

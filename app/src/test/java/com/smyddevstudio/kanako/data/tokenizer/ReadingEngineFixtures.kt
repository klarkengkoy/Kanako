package com.smyddevstudio.kanako.data.tokenizer

import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import java.io.File

internal object ReadingEngineFixtures {
    val sudachi: JapaneseReadingEngine by lazy {
        val file = File(requireNotNull(System.getProperty("sudachi.dictionary")))
        check(file.isFile) { "Run tests through Gradle to prepare the pinned dictionary" }
        SudachiReadingEngine(Dictionary.load(Config.defaultConfig().systemDictionary(file.toPath())))
    }
    val kuromoji: JapaneseReadingEngine by lazy { KuromojiReadingEngine() }
}

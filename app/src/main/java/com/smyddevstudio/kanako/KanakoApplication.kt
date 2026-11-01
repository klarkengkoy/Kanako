package com.smyddevstudio.kanako

import android.app.Application
import com.smyddevstudio.kanako.data.tokenizer.ActiveJapaneseReadingEngine

class KanakoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ActiveJapaneseReadingEngine.initialize(this)
    }
}

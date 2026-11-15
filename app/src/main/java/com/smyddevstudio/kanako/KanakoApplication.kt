package com.smyddevstudio.kanako

import android.app.Application
import com.smyddevstudio.kanako.data.tokenizer.ActiveJapaneseReadingEngine

class KanakoApplication : Application() {
    internal val accounts by lazy { com.smyddevstudio.kanako.data.account.AccountCoordinator(this) }
    override fun onCreate() {
        super.onCreate()
        com.smyddevstudio.kanako.data.account.ReadingAccess.initialize { accounts.canReadNow() }
        ActiveJapaneseReadingEngine.initialize(this)
    }
}

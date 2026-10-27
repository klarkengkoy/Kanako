package com.smyddevstudio.kanako

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smyddevstudio.kanako.ui.navigation.KanakoApp
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_CAPTURE_FROM_TILE =
            "com.smyddevstudio.kanako.extra.START_CAPTURE_FROM_TILE"
    }

    private var startCaptureRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeTileStartRequest(intent)
        enableEdgeToEdge()
        setContent {
            KanakoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KanakoApp(startCaptureRequest = startCaptureRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTileStartRequest(intent)
    }

    private fun consumeTileStartRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_CAPTURE_FROM_TILE, false) == true) {
            startCaptureRequest++
            intent.removeExtra(EXTRA_START_CAPTURE_FROM_TILE)
        }
    }
}

package com.smyddevstudio.kanako.service

import android.graphics.Rect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingVisibilityTest {
    @get:Rule val compose = createComposeRule()

    private val readable = RecognizedTextLine("本", Rect(100, 200, 200, 240), 1,
        ReadingBounds(0, 0, 1000, 2000), segments = listOf(FuriganaSegment("本", "ほん")))
    private val unknown = readable.copy(text = "\u9fff",
        segments = listOf(FuriganaSegment("\u9fff")))

    @Test fun recognizedUnknownKanjiWithNoPronunciationReportsNoVisibleReading() {
        val segments = JapaneseTextTokenizer().tokenize("\u9fff")
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { it.reading == null })
        var visible: Boolean? = null
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(listOf(unknown.copy(segments = segments)),
                    KanakoPreferences(), IntOffset.Zero, Modifier.testTag("readings"),
                    onReadingsVisibleChanged = { visible = it })
            }
        }
        compose.onNodeWithTag("readings").assertTextEquals("")
        compose.runOnIdle { assertEquals(false, visible) }
    }

    @Test fun removingTheLastReadingClearsSuccessEvenWhenRecognizedTextRemains() {
        val lines = mutableStateOf(listOf(readable, unknown))
        var visible: Boolean? = null
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(lines.value, KanakoPreferences(), IntOffset.Zero,
                    Modifier.testTag("readings"), onReadingsVisibleChanged = { visible = it })
            }
        }
        compose.onNodeWithTag("readings").assertTextEquals("ほん")
        compose.runOnIdle {
            assertEquals(true, visible)
            lines.value = listOf(unknown)
        }
        compose.onNodeWithTag("readings").assertTextEquals("")
        compose.runOnIdle {
            assertEquals(false, visible)
            lines.value = emptyList()
        }
        compose.runOnIdle { assertEquals(false, visible) }
    }

    @Test fun clippingTheOnlyReadableWordClearsSuccessWhileUnknownTextRemains() {
        val lines = mutableStateOf(listOf(readable, unknown))
        var visible: Boolean? = null
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(lines.value, KanakoPreferences(), IntOffset.Zero,
                    Modifier.testTag("readings"), onReadingsVisibleChanged = { visible = it })
            }
        }
        compose.runOnIdle {
            assertEquals(true, visible)
            lines.value = listOf(readable.copy(bounds = Rect(1200, 200, 1300, 240)), unknown)
        }
        compose.onNodeWithTag("readings").assertTextEquals("")
        compose.runOnIdle { assertEquals(false, visible) }
    }

    @Test fun aReadingThatCannotFitTheViewportDoesNotReportSuccess() {
        var visible: Boolean? = null
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(listOf(readable.copy(
                    bounds = Rect(0, 0, 1, 1), clip = ReadingBounds(0, 0, 1, 1))),
                    KanakoPreferences(), IntOffset.Zero, Modifier.testTag("readings"),
                    onReadingsVisibleChanged = { visible = it })
            }
        }
        compose.onNodeWithTag("readings").assertTextEquals("")
        compose.runOnIdle { assertEquals(false, visible) }
    }

    @Test fun fallbackTokenizationReportsSuccessOnlyAfterAReadingIsPlaced() {
        var visible: Boolean? = null
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(listOf(readable.copy(segments = null)),
                    KanakoPreferences(), IntOffset.Zero, Modifier.testTag("readings"),
                    onReadingsVisibleChanged = { visible = it })
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { visible == true }
        compose.onNodeWithTag("readings").assertTextEquals("ほん")
    }

    @Test fun aNewResultCallbackReceivesVisibilityWhenTheOcrLinesAreUnchanged() {
        val result = mutableStateOf(0)
        val lines = listOf(readable)
        var reported: Pair<Int, Boolean>? = null
        compose.setContent {
            val currentResult = result.value
            KanakoTheme {
                RecognizedTextOverlayContent(lines, KanakoPreferences(), IntOffset.Zero,
                    onReadingsVisibleChanged = { reported = currentResult to it })
            }
        }
        compose.runOnIdle {
            assertEquals(0 to true, reported)
            result.value++
        }
        compose.runOnIdle { assertEquals(1 to true, reported) }
    }
}

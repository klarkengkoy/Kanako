package com.smyddevstudio.kanako.service

import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.FuriganaSegment
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingOverlayInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val line = RecognizedTextLine("日本語本", Rect(100, 200, 200, 240), 1,
        ReadingBounds(0, 0, 1000, 2000), segments = listOf(
            FuriganaSegment("日本語", "にほんご"), FuriganaSegment("本", "ほん")))

    @Test fun kanjiTapsSelectTheirPartnerUsingTheActualOverlayOrigin() {
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(listOf(line), KanakoPreferences(touchPassthrough = false),
                    IntOffset(50, 100), Modifier.testTag("readings"))
            }
        }
        val readings = compose.onNodeWithTag("readings")
        readings.performTouchInput { click(Offset(75f, 120f)) }
        readings.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "にほんご"))
        readings.performTouchInput { click(Offset(135f, 120f)) }
        readings.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "ほん"))
    }

    @Test fun touchThroughPreferenceKeepsSelectionDisabled() {
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(listOf(line), KanakoPreferences(touchPassthrough = true),
                    IntOffset(50, 100), Modifier.testTag("readings"))
            }
        }
        compose.onNodeWithTag("readings").performTouchInput { click(Offset(75f, 120f)) }
        compose.onNodeWithTag("readings").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
    }
}

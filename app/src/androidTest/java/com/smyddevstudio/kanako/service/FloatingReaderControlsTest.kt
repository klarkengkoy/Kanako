package com.smyddevstudio.kanako.service

import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.mutableStateOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import org.junit.Assert.assertFalse
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingReaderControlsTest {
    @get:Rule val compose = createComposeRule()

    private val description get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.floating_control_description)

    private fun content(onRead: () -> Unit = {}, onDrag: (Float, Float) -> Unit = { _, _ -> }) {
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(
                    preferences = KanakoPreferences(), showFurigana = false,
                    onToggleFurigana = {}, onDrag = onDrag, onDragEnd = {},
                    onToggleScriptType = {}, onOpenApp = {}, onStopService = {},
                    onReadOrClear = onRead
                )
            }
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 28)
    @Test fun loadingGifLoopsAndStopsWhenReadingEndsOrControlIsRemoved() {
        val reading = mutableStateOf(true)
        val visible = mutableStateOf(true)
        var reads = 0
        compose.setContent {
            KanakoTheme {
                if (visible.value) FloatingOverlayContent(
                    preferences = KanakoPreferences(), showFurigana = false,
                    onToggleFurigana = {}, onDrag = { _, _ -> }, onDragEnd = {},
                    onToggleScriptType = {}, onOpenApp = {}, onStopService = {},
                    onReadOrClear = { reads++ }, isReading = reading.value,
                    statusMessage = if (reading.value) R.string.reading_working else R.string.reading_ready
                )
            }
        }
        fun awaitAnimation(): AnimatedImageDrawable {
            var animation: AnimatedImageDrawable? = null
            compose.waitUntil(timeoutMillis = 5_000) {
                onView(isAssignableFrom(ImageView::class.java)).check { view, _ ->
                    animation = (view as ImageView).drawable as? AnimatedImageDrawable
                }
                animation?.isRunning == true
            }
            return requireNotNull(animation)
        }
        val firstAnimation = awaitAnimation()
        assertEquals(AnimatedImageDrawable.REPEAT_INFINITE, firstAnimation.repeatCount)
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertEquals(1, reads)
            reading.value = false
        }
        compose.runOnIdle { assertFalse(firstAnimation.isRunning) }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.runOnIdle { reading.value = true }
        val nextAnimation = awaitAnimation()
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(nextAnimation.isRunning) }
    }

    @Test fun readingsRemainAvailableToAccessibilityServices() {
        compose.setContent {
            KanakoTheme {
                RecognizedTextOverlayContent(
                    listOf(RecognizedTextLine("漢字", Rect(100, 200, 200, 250), 1, ReadingBounds(0, 0, 800, 800))),
                    KanakoPreferences(), IntOffset.Zero
                )
            }
        }
        compose.onNodeWithText("かんじ", substring = true).assertExists()
    }

    @Test fun singleTapReadsExactlyOnce() {
        var reads = 0
        content(onRead = { reads++ })
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun doubleTapOpensControlsWithoutReading() {
        var reads = 0
        content(onRead = { reads++ })
        compose.onNodeWithContentDescription(description).performTouchInput { doubleClick() }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.controls_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, reads) }
    }

    @Test fun longPressOpensControlsWithoutReading() {
        var reads = 0
        content(onRead = { reads++ })
        compose.onNodeWithContentDescription(description).performTouchInput { longClick() }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.controls_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, reads) }
    }

    @Test fun draggingMovesWithoutReading() {
        var reads = 0
        var movement = 0f
        content(onRead = { reads++ }, onDrag = { x, y -> movement += kotlin.math.abs(x) + kotlin.math.abs(y) })
        compose.onNodeWithContentDescription(description).performTouchInput {
            swipe(center, center + Offset(120f, 80f), 200)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertTrue(movement > 0f)
            assertEquals(0, reads)
        }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 28)
    @Test fun disablingMotionStopsTheNativeAnimationAndShowsTheStaticMascot() {
        val motion = mutableStateOf(true)
        compose.setContent { LoadingMascot(size = 56.dp, animationsEnabled = motion.value) }
        var animation: AnimatedImageDrawable? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            onView(isAssignableFrom(ImageView::class.java)).check { view, _ ->
                animation = (view as ImageView).drawable as? AnimatedImageDrawable
            }
            animation?.isRunning == true
        }
        compose.runOnIdle { motion.value = false }
        compose.runOnIdle { assertFalse(requireNotNull(animation).isRunning) }
        onView(isAssignableFrom(ImageView::class.java)).check { view, _ ->
            assertFalse((view as ImageView).drawable is AnimatedImageDrawable)
        }
    }

    @Test fun failedReadingExplainsTheProblemWithoutOpeningControlsOrReadingAgain() {
        val status = mutableStateOf(R.string.reading_ready)
        var reads = 0
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(
                    preferences = KanakoPreferences(), showFurigana = false,
                    onToggleFurigana = {}, onDrag = { _, _ -> }, onDragEnd = {},
                    onToggleScriptType = {}, onOpenApp = {}, onStopService = {},
                    onReadOrClear = { reads++ }, statusMessage = status.value
                )
            }
        }
        compose.runOnIdle { status.value = R.string.reading_protected }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.reading_protected)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.controls_title)).performClick()
        compose.onNodeWithText(context.getString(R.string.controls_stop)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, reads) }
    }

    @Test fun defaultFloatingControlUsesTheMedium56DpTouchTarget() {
        content()
        compose.onNodeWithContentDescription(description)
            .assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
    }

}

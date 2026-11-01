package com.smyddevstudio.kanako.service

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso.pressBack
import android.view.View
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.test.onAllNodesWithText
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
                    onDrag = onDrag, onDragEnd = {},
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
                    onDrag = { _, _ -> }, onDragEnd = {},
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
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("かんじ", substring = true).fetchSemanticsNodes().isNotEmpty()
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
                    onDrag = { _, _ -> }, onDragEnd = {},
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

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun gestureExclusionTracksOnlyTheMascotAndIsRemovedWithIt() {
        var host: View? = null
        val visible = mutableStateOf(true)
        compose.setContent {
            host = LocalView.current
            KanakoTheme {
                if (visible.value) FloatingOverlayContent(
                    preferences = KanakoPreferences(), showFurigana = true,
                    onDrag = { _, _ -> }, onDragEnd = {},
                    onToggleScriptType = {}, onOpenApp = {}, onStopService = {}
                )
            }
        }
        fun assertMascotExclusion() {
            val mascot = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                val rect = requireNotNull(host).systemGestureExclusionRects.single()
                assertTrue(kotlin.math.abs(rect.left - mascot.left) <= 1)
                assertTrue(kotlin.math.abs(rect.top - mascot.top) <= 1)
                assertTrue(kotlin.math.abs(rect.right - mascot.right) <= 1)
                assertTrue(kotlin.math.abs(rect.bottom - mascot.bottom) <= 1)
            }
        }
        assertMascotExclusion()
        compose.onNodeWithContentDescription(description).performTouchInput { doubleClick() }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.controls_title)).assertIsDisplayed()
        assertMascotExclusion()
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertTrue(requireNotNull(host).systemGestureExclusionRects.isEmpty()) }
    }

    @Test fun defaultFloatingControlUsesTheMedium56DpTouchTarget() {
        content()
        compose.onNodeWithContentDescription(description)
            .assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
    }


    @Test fun outsideTapAndBackDismissTheMenuWithoutMovingTheMascot() {
        var host: View? = null
        val sizes = mutableListOf<androidx.compose.ui.unit.IntSize>()
        var reads = 0
        compose.setContent {
            host = LocalView.current
            KanakoTheme {
                Box(Modifier.fillMaxSize()) {
                    FloatingOverlayContent(KanakoPreferences(), true, { _, _ -> }, {}, {}, {}, {},
                        Modifier.align(Alignment.Center), onReadOrClear = { reads++ },
                        onLayoutSizeChanged = { sizes.add(it) })
                }
            }
        }
        val mascot = compose.onNodeWithContentDescription(description)
        val before = mascot.fetchSemanticsNode().boundsInRoot
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.controls_title)
        mascot.performTouchInput { longClick() }
        compose.onNodeWithText(title).assertIsDisplayed()
        val location = IntArray(2)
        compose.runOnIdle { requireNotNull(host).getLocationOnScreen(location) }
        val x = location[0] + requireNotNull(host).width - 12f
        val y = location[1] + requireNotNull(host).height - 12f
        val now = SystemClock.uptimeMillis()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 60 else 0,
                action, x, y, 0).apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        compose.onNodeWithText(title).assertDoesNotExist()
        repeat(12) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals(before, mascot.fetchSemanticsNode().boundsInRoot)
        }
        mascot.performTouchInput { doubleClick() }
        compose.onNodeWithText(title).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, reads)
            assertTrue(sizes.isNotEmpty())
            assertTrue(sizes.all { it == sizes.first() })
        }
    }

    @Test fun menuOmitsClearAndStopStaysOneLineAndClosesBeforeStopping() {
        var stops = 0
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(KanakoPreferences(), true, { _, _ -> }, {}, {}, {}, { stops++ })
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithContentDescription(description).performTouchInput { doubleClick() }
        compose.onNodeWithText(context.getString(R.string.clear_annotations)).assertDoesNotExist()
        val stop = compose.onNodeWithText(context.getString(R.string.controls_stop))
        val layouts = mutableListOf<TextLayoutResult>()
        stop.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        stop.performClick()
        compose.onNodeWithText(context.getString(R.string.controls_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    @Test fun hidingReaderDismissesPopupAndDoesNotRestoreItOnReturn() {
        val visible = mutableStateOf(true)
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(KanakoPreferences(), true, { _, _ -> }, {}, {}, {}, {},
                    controlsVisible = visible.value)
            }
        }
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.controls_title)
        compose.onNodeWithContentDescription(description).performTouchInput { doubleClick() }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText(title).assertDoesNotExist()
    }
}

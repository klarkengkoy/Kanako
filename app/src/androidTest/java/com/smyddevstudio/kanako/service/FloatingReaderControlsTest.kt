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
import android.view.ViewGroup
import androidx.compose.ui.platform.LocalView
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import com.smyddevstudio.kanako.model.ScriptType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import org.junit.Assert.assertFalse
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
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


    @Test fun tutorialDoubleTapKeepsReadingsAndAllowsRepeatedScriptChanges() {
        val preferences = mutableStateOf(KanakoPreferences())
        var clears = 0
        var switches = 0
        GuidedReaderSession.begin()
        GuidedReaderSession.setSampleVisible(true)
        try {
            compose.setContent {
                KanakoTheme {
                    FloatingOverlayContent(
                        preferences = preferences.value, showFurigana = true,
                        guided = true,
                        onReadOrClear = { clears++ },
                        onDrag = { _, _ -> }, onDragEnd = {},
                        onToggleScriptType = {
                            switches++
                            preferences.value = preferences.value.copy(
                                scriptType = if (preferences.value.scriptType == ScriptType.KANA)
                                    ScriptType.ROMAJI else ScriptType.KANA
                            )
                        },
                        onOpenApp = {}, onStopService = {}
                    )
                }
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fun scriptLabel(id: Int) = context.getString(R.string.controls_script, context.getString(id))
            compose.onNodeWithContentDescription(description).performTouchInput { doubleClick() }
            compose.onNodeWithText(scriptLabel(R.string.script_kana)).assertIsDisplayed().performClick()
            compose.onNodeWithText(scriptLabel(R.string.script_romaji)).assertIsDisplayed().performClick()
            compose.onNodeWithText(scriptLabel(R.string.script_kana)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.controls_stop)).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, clears)
                assertEquals(2, switches)
                assertTrue(GuidedReaderSession.active)
            }
        } finally {
            GuidedReaderSession.end()
        }
    }

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
    @Test fun shortReadingFinishesAWholeLoopBeforeTheOutroAndRemovalStopsPlayback() {
        val reading = mutableStateOf(false)
        val visible = mutableStateOf(true)
        compose.setContent {
            KanakoTheme {
                if (visible.value) FloatingOverlayContent(
                    preferences = KanakoPreferences(), showFurigana = false,
                    onDrag = { _, _ -> }, onDragEnd = {},
                    onToggleScriptType = {}, onOpenApp = {}, onStopService = {},
                    isReading = reading.value,
                    statusMessage = if (reading.value) R.string.reading_working else R.string.reading_ready
                )
            }
        }
        fun currentAnimation(): AnimatedImageDrawable? {
            var animation: AnimatedImageDrawable? = null
            onView(isAssignableFrom(ImageView::class.java)).check { view, _ ->
                animation = (view as ImageView).drawable as? AnimatedImageDrawable
            }
            return animation
        }
        fun awaitDifferent(previous: AnimatedImageDrawable?, timeout: Long = 5_000): AnimatedImageDrawable {
            var animation: AnimatedImageDrawable? = null
            compose.waitUntil(timeoutMillis = timeout) {
                animation = currentAnimation()
                animation != null && animation !== previous && animation?.isRunning == true
            }
            return requireNotNull(animation)
        }
        awaitDifferent(null)
        compose.waitUntil(timeoutMillis = 5_000) { currentAnimation() == null }
        compose.runOnIdle { reading.value = true }
        val intro = awaitDifferent(null)
        compose.runOnIdle { reading.value = false }
        val loop = awaitDifferent(intro)
        assertEquals(0, loop.repeatCount)
        val loopObservedAt = SystemClock.elapsedRealtime()
        val outro = awaitDifferent(loop, timeout = 7_000)
        val observedDuration = SystemClock.elapsedRealtime() - loopObservedAt
        println("KANAKO_READING_LOOP_OBSERVED_MS=$observedDuration")
        assertTrue("Reading loop cut short: $observedDuration ms", observedDuration >= 3_900)
        assertEquals(0, outro.repeatCount)
        compose.waitUntil(timeoutMillis = 5_000) { currentAnimation() == null }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.runOnIdle { reading.value = true }
        val interrupted = awaitDifferent(null)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(interrupted.isRunning) }
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
        var host: View? = null
        compose.setContent {
            host = LocalView.current
            LoadingMascot(size = 56.dp, animationsEnabled = motion.value)
        }
        fun imageIn(view: View): ImageView? {
            if (view is ImageView) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    imageIn(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }
        var animation: AnimatedImageDrawable? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle {
                val image = requireNotNull(imageIn(requireNotNull(host)))
                assertTrue(image.isAttachedToWindow && image.isShown)
                animation = image.drawable as? AnimatedImageDrawable
            }
            animation?.isRunning == true
        }
        compose.runOnIdle { motion.value = false }
        compose.runOnIdle {
            assertFalse(requireNotNull(animation).isRunning)
            assertFalse(requireNotNull(imageIn(requireNotNull(host))).drawable is AnimatedImageDrawable)
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

    @Test fun errorPopupKeepsFocusOnTheSourceAndSurvivesReadyUntilItsTimeout() {
        val status = mutableStateOf(R.string.reading_ready)
        var host: View? = null
        compose.setContent {
            host = LocalView.current
            CompositionLocalProvider(LocalAccessibilityManager provides null) {
                KanakoTheme {
                    FloatingOverlayContent(KanakoPreferences(), false, { _, _ -> }, {}, {}, {}, {},
                        statusMessage = status.value)
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(R.string.reading_protected)
        compose.runOnIdle { status.value = R.string.reading_protected }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { assertTrue(requireNotNull(host).hasWindowFocus()) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { status.value = R.string.reading_ready }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.controls_stop)).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(6_100)
        compose.onNodeWithText(message).assertDoesNotExist()
    }

    @Test fun tappingAfterAnErrorStartsOneReadAndDismissesTheStatusPopup() {
        val reading = mutableStateOf(false)
        var reads = 0
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(KanakoPreferences(), false, { _, _ -> }, {}, {}, {}, {},
                    onReadOrClear = { reads++; reading.value = true },
                    isReading = reading.value,
                    statusMessage = if (reading.value) R.string.reading_working else R.string.reading_protected)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.reading_protected)).assertIsDisplayed()
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(context.getString(R.string.reading_protected)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.controls_title)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, reads) }
    }

    @Test fun repeatedImmediateNoSourceFailuresShowFeedbackOnEveryTap() {
        assertRepeatedImmediateFailureFeedback(R.string.reading_no_source)
    }

    @Test fun repeatedImmediateNoReadingResultsShowFeedbackOnEveryTap() {
        assertRepeatedImmediateFailureFeedback(R.string.reading_no_text)
    }

    private fun assertRepeatedImmediateFailureFeedback(messageRes: Int) {
        val reading = mutableStateOf(false)
        val status = mutableStateOf(R.string.reading_ready)
        var reads = 0
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides null) {
                KanakoTheme {
                    FloatingOverlayContent(KanakoPreferences(), false, { _, _ -> }, {}, {}, {}, {},
                        onReadOrClear = {
                            reads++
                            reading.value = true
                            status.value = R.string.reading_working
                            status.value = messageRes
                            reading.value = false
                        },
                        isReading = reading.value, statusMessage = status.value)
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = context.getString(messageRes)
        val mascot = compose.onNodeWithContentDescription(description)
        mascot.assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        repeat(3) { attempt ->
            mascot.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.controls_stop)).assertDoesNotExist()
            compose.runOnIdle { assertEquals(attempt + 1, reads) }
            if (attempt == 1) {
                compose.mainClock.advanceTimeBy(6_100)
                compose.onNodeWithText(message).assertDoesNotExist()
            }
        }
    }

    @Test fun hidingReaderDismissesAnErrorWithoutRestoringItOnReturn() {
        val visible = mutableStateOf(true)
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(KanakoPreferences(), false, { _, _ -> }, {}, {}, {}, {},
                    statusMessage = R.string.reading_protected, controlsVisible = visible.value)
            }
        }
        val message = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.reading_protected)
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText(message).assertDoesNotExist()
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
    @Test fun floatingMenuRequiresConfirmationAndCanTurnAutoReadBackOff() {
        val preferences = mutableStateOf(KanakoPreferences())
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(preferences.value, false, { _, _ -> }, {}, {}, {}, {},
                    onUpdateAutoReadAfterScroll = {
                        changes.add(it)
                        preferences.value = preferences.value.copy(autoReadAfterScroll = it)
                    })
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun mode(id: Int) = context.getString(R.string.controls_mode, context.getString(id))
        fun openMenu() { compose.onNodeWithContentDescription(description).performTouchInput { longClick() } }
        openMenu()
        compose.onNodeWithText(mode(R.string.mode_tap_to_read)).performClick()
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        compose.onNodeWithText(context.getString(R.string.snapshot_disclosure_cancel))
            .performScrollTo().performClick()
        compose.runOnIdle { assertFalse(preferences.value.autoReadAfterScroll) }
        compose.onNodeWithText(mode(R.string.mode_tap_to_read)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.auto_read_enable)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
        openMenu()
        compose.onNodeWithText(mode(R.string.mode_auto_reader)).assertIsDisplayed().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(true, false), changes) }
    }

    @Test fun photoControlsHaveNoAutoReadSetting() {
        compose.setContent {
            KanakoTheme {
                FloatingOverlayContent(KanakoPreferences(), true, { _, _ -> }, {}, {}, {}, {},
                    showServiceActions = false)
            }
        }
        compose.onNodeWithContentDescription(description).performTouchInput { longClick() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.controls_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.controls_mode,
            context.getString(R.string.mode_tap_to_read))).assertDoesNotExist()
    }

}

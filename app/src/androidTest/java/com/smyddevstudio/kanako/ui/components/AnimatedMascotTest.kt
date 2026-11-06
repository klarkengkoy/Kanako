package com.smyddevstudio.kanako.ui.components

import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.smyddevstudio.kanako.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 28)
@RunWith(AndroidJUnit4::class)
class AnimatedMascotTest {
    @get:Rule val compose = createComposeRule()

    private fun imageView(): ImageView {
        var result: ImageView? = null
        onView(isAssignableFrom(ImageView::class.java)).check { view, _ -> result = view as ImageView }
        return requireNotNull(result)
    }

    private fun awaitAnimation(): AnimatedImageDrawable {
        var animation: AnimatedImageDrawable? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            animation = imageView().drawable as? AnimatedImageDrawable
            animation?.isRunning == true
        }
        return requireNotNull(animation)
    }

    @Test fun oneShotReturnsToStaticArtworkAndDoesNotReplayOnRecomposition() {
        val label = mutableIntStateOf(0)
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_greeting_wave, 56.dp,
                contentDescription = "Mascot ${label.intValue}", animationsEnabled = true)
        }
        val animation = awaitAnimation()
        assertEquals(0, animation.repeatCount)
        compose.waitUntil(timeoutMillis = 5_000) { imageView().drawable !is AnimatedImageDrawable }
        compose.runOnIdle {
            assertFalse(animation.isRunning)
            label.intValue++
        }
        compose.onNodeWithContentDescription("Mascot 1").assertIsDisplayed()
        assertFalse(imageView().drawable is AnimatedImageDrawable)
    }

    @Test fun aNewPlaybackKeyRestartsOnceAndRemovalStopsTheAnimation() {
        val event = mutableIntStateOf(0)
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) AnimatedMascot(R.drawable.kanako_success, 56.dp,
                playbackKey = event.intValue, animationsEnabled = true)
        }
        val first = awaitAnimation()
        compose.runOnIdle { event.intValue++ }
        val second = awaitAnimation()
        assertNotSame(first, second)
        compose.runOnIdle {
            assertFalse(first.isRunning)
            visible.value = false
        }
        compose.runOnIdle { assertFalse(second.isRunning) }
    }

    @Test fun disablingMotionStopsPlaybackAndKeepsTheAccessibleStaticMascot() {
        val motion = mutableStateOf(true)
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_curious_tilt, 56.dp,
                contentDescription = "Preview control", animationsEnabled = motion.value)
        }
        val animation = awaitAnimation()
        compose.runOnIdle { motion.value = false }
        compose.onNodeWithContentDescription("Preview control").assertIsDisplayed()
        compose.runOnIdle { assertFalse(animation.isRunning) }
        assertFalse(imageView().drawable is AnimatedImageDrawable)
    }

    @Test fun leavingTheScreenStopsAReactionWithoutReplayingItOnReturn() {
        lateinit var owner: TestOwner
        compose.runOnIdle {
            owner = TestOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AnimatedMascot(R.drawable.kanako_error_reaction, 56.dp, animationsEnabled = true)
            }
        }
        val animation = awaitAnimation()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { assertFalse(animation.isRunning) }
        assertFalse(imageView().drawable is AnimatedImageDrawable)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        assertFalse(imageView().drawable is AnimatedImageDrawable)
    }

    @Test fun aLoadingLoopPausesWithTheScreenAndRestartsOnReturn() {
        lateinit var owner: TestOwner
        compose.runOnIdle {
            owner = TestOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AnimatedMascot(R.drawable.kanako_peek_blink, 56.dp,
                    loop = true, animationsEnabled = true)
            }
        }
        val animation = awaitAnimation()
        assertEquals(AnimatedImageDrawable.REPEAT_INFINITE, animation.repeatCount)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { assertFalse(animation.isRunning) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        val resumed = awaitAnimation()
        assertSame(animation, resumed)
        compose.runOnIdle { assertTrue(animation.isRunning) }
    }

    @Test fun welcomeHoldsTheFinalFrameAndNotifiesOnlyOnce() {
        val label = mutableIntStateOf(0)
        var completions = 0
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_welcome_wave, 56.dp,
                contentDescription = "Welcome ${label.intValue}", animationsEnabled = true,
                holdLastFrame = true, onFinished = { completions++ })
        }
        val animation = awaitAnimation()
        assertEquals(0, animation.repeatCount)
        compose.waitUntil(timeoutMillis = 6_000) { completions == 1 }
        val view = imageView()
        compose.runOnIdle {
            assertFalse(animation.isRunning)
            assertSame(animation, view.drawable)
            label.intValue++
        }
        compose.onNodeWithContentDescription("Welcome 1").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertSame(animation, view.drawable)
        }
    }

    @Test fun interruptedButtonHandoffCompletesOnceOnReturnWithoutReplaying() {
        lateinit var owner: TestOwner
        var completions = 0
        compose.runOnIdle {
            owner = TestOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AnimatedMascot(R.drawable.kanako_peek_button, 56.dp,
                    animationsEnabled = true, holdLastFrame = true, durationMillis = 4_000,
                    onFinished = { completions++ })
            }
        }
        val animation = awaitAnimation()
        val view = imageView()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle {
            assertFalse(animation.isRunning)
            assertEquals(0, completions)
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle {
            assertEquals(1, completions)
            assertFalse(animation.isRunning)
            assertSame(animation, view.drawable)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test fun reducedMotionCompletesARequestedHandoffOnceWithoutPlaying() {
        val label = mutableIntStateOf(0)
        var completions = 0
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_peek_button, 56.dp,
                contentDescription = "Button ${label.intValue}", animationsEnabled = false,
                onFinished = { completions++ })
        }
        compose.runOnIdle {
            assertEquals(1, completions)
            label.intValue++
        }
        compose.onNodeWithContentDescription("Button 1").assertIsDisplayed()
        val view = imageView()
        compose.runOnIdle {
            assertEquals(1, completions)
            assertFalse(view.drawable is AnimatedImageDrawable)
        }
    }

    @Test fun fastDeparturePlaysOnceInAbout700msAndHoldsTheEmptyEndFrame() {
        var finishedAt = 0L
        val requestedAt = SystemClock.elapsedRealtime()
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_peek_button, 24.dp,
                durationMillis = 700, animationsEnabled = true, holdLastFrame = true,
                onFinished = { finishedAt = SystemClock.elapsedRealtime() })
        }
        val animation = awaitAnimation()
        assertEquals(0, animation.repeatCount)
        compose.waitUntil(timeoutMillis = 3_000) { finishedAt > 0 }
        val elapsed = finishedAt - requestedAt
        println("KANAKO_FAST_DEPARTURE_MS=$elapsed")
        val view = imageView()
        assertTrue("Departure duration: $elapsed ms", elapsed in 550L..1_400L)
        compose.runOnIdle {
            assertFalse(animation.isRunning)
            assertSame(animation, view.drawable)
        }
    }

    @Test fun endingALoopKeepsTheSameDrawableUntilItsCurrentPassFinishes() {
        val looping = mutableStateOf(true)
        var completions = 0
        compose.setContent {
            AnimatedMascot(R.drawable.kanako_loading_2_reading_loop, 56.dp,
                loop = looping.value, animationsEnabled = true, holdLastFrame = true,
                onFinished = { completions++ })
        }
        val animation = awaitAnimation()
        val view = imageView()
        compose.runOnIdle { looping.value = false }
        compose.runOnIdle {
            assertSame(animation, view.drawable)
            assertTrue(animation.isRunning)
            assertEquals(0, animation.repeatCount)
            assertEquals(0, completions)
        }
        compose.waitUntil(timeoutMillis = 7_000) { completions == 1 }
        compose.runOnIdle { assertFalse(animation.isRunning) }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}

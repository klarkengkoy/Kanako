package com.smyddevstudio.kanako.ui.components

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smyddevstudio.kanako.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against native WebP playback on API 28+ and the GIF fallback on API 26–27. */
@RunWith(AndroidJUnit4::class)
class AnimatedMascotLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private fun awaitPlayback(): Drawable {
        var result: Drawable? = null
        compose.waitUntil(timeoutMillis = 5_000) {
            onView(isAssignableFrom(ImageView::class.java)).check { view, _ ->
                result = (view as ImageView).drawable
            }
            (result as? Animatable)?.isRunning == true
        }
        return requireNotNull(result)
    }

    @Test fun interruptedOneShotAdvancesItsParentToTheNextAnimationOnReturn() {
        lateinit var owner: TestOwner
        val completions = mutableIntStateOf(0)
        compose.runOnIdle {
            owner = TestOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val nextPhase = completions.intValue > 0
                AnimatedMascot(
                    if (nextPhase) R.drawable.kanako_peek_blink else R.drawable.kanako_peek_button,
                    56.dp, playbackKey = nextPhase, loop = nextPhase,
                    animationsEnabled = true, holdLastFrame = true,
                    durationMillis = if (nextPhase) null else 4_000,
                    onFinished = { completions.intValue++ }
                )
            }
        }
        val interrupted = awaitPlayback()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle {
            assertFalse((interrupted as Animatable).isRunning)
            assertEquals(0, completions.intValue)
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        val next = awaitPlayback()
        compose.runOnIdle {
            assertEquals(1, completions.intValue)
            assertNotSame(interrupted, next)
            assertFalse((interrupted as Animatable).isRunning)
        }
    }

    @Test fun replacingAnInterruptedRequestWhileHiddenDoesNotCompleteTheNewRequest() {
        lateinit var owner: TestOwner
        val request = mutableIntStateOf(0)
        var completions = 0
        var starts = 0
        compose.runOnIdle {
            owner = TestOwner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AnimatedMascot(
                    R.drawable.kanako_peek_button, 56.dp, playbackKey = request.intValue,
                    animationsEnabled = true, holdLastFrame = true, durationMillis = 4_000,
                    onPlaybackStarted = { starts++ },
                    onFinished = { completions++ }
                )
            }
        }
        val interrupted = awaitPlayback()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { request.intValue++ }
        compose.runOnIdle {
            assertEquals(0, completions)
            assertEquals(1, starts)
        }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        val replacement = awaitPlayback()
        compose.runOnIdle {
            assertNotSame(interrupted, replacement)
            assertFalse((interrupted as Animatable).isRunning)
            assertEquals(0, completions)
            assertEquals(2, starts)
            owner.registry.currentState = Lifecycle.State.RESUMED
            assertEquals(2, starts)
        }
        compose.waitUntil(timeoutMillis = 6_000) { completions == 1 }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}

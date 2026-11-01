package com.smyddevstudio.kanako.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingOverlayWindowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun popupWorksFromAnOverlayWindowAndDismissalKeepsItsScreenAnchor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(Settings.canDrawOverlays(context))
        var host: View? = null
        compose.setContent { host = LocalView.current; Box(Modifier.fillMaxSize()) }
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dimensions = context.resources.displayMetrics
        val width = (56 * dimensions.density).toInt()
        val screen = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds
            else android.graphics.Rect(0, 0, dimensions.widthPixels, dimensions.heightPixels)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screen.right - width - (16 * dimensions.density).toInt()
            y = screen.bottom - width - (80 * dimensions.density).toInt()
        }
        var overlay: ComposeView? = null
        try {
            compose.runOnIdle {
                val owner = requireNotNull(host)
                overlay = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(requireNotNull(owner.findViewTreeLifecycleOwner()))
                    setViewTreeSavedStateRegistryOwner(requireNotNull(owner.findViewTreeSavedStateRegistryOwner()))
                    setViewTreeViewModelStoreOwner(requireNotNull(owner.findViewTreeViewModelStoreOwner()))
                    setContent {
                        KanakoTheme {
                            FloatingOverlayContent(KanakoPreferences(), true, { _, _ -> }, {}, {}, {}, {})
                        }
                    }
                }
                manager.addView(overlay, params)
            }
            val description = context.getString(R.string.floating_control_description)
            val title = context.getString(R.string.controls_title)
            val mascot = compose.onNodeWithContentDescription(description)
            mascot.assertIsDisplayed()
            val original = IntArray(2)
            compose.runOnIdle { requireNotNull(overlay).getLocationOnScreen(original) }
            mascot.performTouchInput { doubleClick() }
            compose.onNodeWithText(title).assertIsDisplayed()
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 60 else 0,
                    action, screen.left + 80f, screen.top + 200f, 0)
                    .apply { source = android.view.InputDevice.SOURCE_TOUCHSCREEN }
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            compose.onNodeWithText(title).assertDoesNotExist()
            repeat(12) {
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val current = IntArray(2)
                    requireNotNull(overlay).getLocationOnScreen(current)
                    assertArrayEquals(original, current)
                    assertEquals(width, requireNotNull(overlay).width)
                    assertEquals(width, requireNotNull(overlay).height)
                }
            }
        } finally {
            compose.runOnIdle {
                overlay?.let { manager.removeViewImmediate(it); it.disposeComposition() }
            }
        }
    }
}

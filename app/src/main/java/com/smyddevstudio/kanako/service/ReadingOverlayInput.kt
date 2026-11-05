package com.smyddevstudio.kanako.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout

internal data class ReadingOverlayInteraction(
    val regions: List<ReadingBounds>,
    val selective: Boolean,
    val selected: Boolean,
    val onTap: (Float, Float) -> Unit,
    val onDismiss: () -> Unit
)

internal class ReadingOverlayHost(context: Context) : FrameLayout(context) {
    var onOutsideTouch: (Float, Float) -> Unit = { _, _ -> }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch(event.rawX, event.rawY)
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}

internal class ReadingOverlayInput(
    private val context: Context,
    private val manager: WindowManager,
    private val host: ReadingOverlayHost,
    private val params: WindowManager.LayoutParams
) : AutoCloseable {
    private var interaction: ReadingOverlayInteraction? = null
    private var exclusion: ReadingBounds? = null
    private var hidden = false
    private var closed = false
    private var region = Region()
    private var appliedRegion: Region? = null
    private var appliedSurface: Any? = null
    private val legacyWindows = mutableListOf<Pair<ReadingTapView, WindowManager.LayoutParams>>()
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) applySurfaceRegion()
        true
    }

    init {
        host.onOutsideTouch = ::outside
        host.viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    fun update(value: ReadingOverlayInteraction) {
        if (closed) return
        interaction = value
        applyInput()
    }

    fun setExclusion(bounds: ReadingBounds?) {
        if (closed || exclusion == bounds) return
        exclusion = bounds
        applyInput()
    }

    fun setHidden(value: Boolean) {
        if (closed || hidden == value) return
        hidden = value
        applyInput()
    }

    fun clear() {
        interaction = null
        applyInput()
    }

    private fun outside(x: Float, y: Float) {
        if (!hidden && interaction?.selective == true && interaction?.selected == true &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || (x == 0f && y == 0f) ||
                !region.contains(x.toInt(), y.toInt()))) {
            interaction?.onDismiss?.invoke()
        }
    }

    private fun applyInput() {
        val current = interaction
        region = Region().apply {
            if (!hidden) current?.regions?.forEach {
                if (!it.isEmpty) op(Rect(it.left, it.top, it.right, it.bottom), Region.Op.UNION)
            }
            exclusion?.let { op(Rect(it.left, it.top, it.right, it.bottom), Region.Op.DIFFERENCE) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clearLegacyWindows()
            applySurfaceRegion()
            host.invalidate()
        } else {
            setHostTouchable(!hidden && current != null && !current.selective && !region.isEmpty)
            updateLegacyWindows(if (!hidden && current?.selective == true) rectangles(region) else emptyList())
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applySurfaceRegion() {
        if (closed || !host.isAttachedToWindow) return
        val surface = host.rootSurfaceControl ?: run {
            setHostTouchable(false)
            return
        }
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        val surfaceLocation = IntArray(2)
        host.getLocationInSurface(surfaceLocation)
        val localRegion = if (interaction?.selective == false && !hidden && !region.isEmpty) null
            else Region(region).apply { translate(surfaceLocation[0] - location[0], surfaceLocation[1] - location[1]) }
        if (surface !== appliedSurface || localRegion != appliedRegion) {
            surface.setTouchableRegion(localRegion)
            appliedSurface = surface
            appliedRegion = localRegion?.let { Region(it) }
        }
        setHostTouchable(!hidden && !region.isEmpty)
    }

    private fun setHostTouchable(touchable: Boolean) {
        val flags = if (touchable) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (flags == params.flags) return
        params.flags = flags
        if (host.isAttachedToWindow) runCatching { manager.updateViewLayout(host, params) }
    }

    private fun updateLegacyWindows(bounds: List<Rect>) {
        while (legacyWindows.size > bounds.size) {
            val (view, _) = legacyWindows.removeAt(legacyWindows.lastIndex)
            runCatching { manager.removeViewImmediate(view) }
        }
        bounds.forEachIndexed { index, rect ->
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (index == 0) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else 0)
            if (index < legacyWindows.size) {
                val (view, layout) = legacyWindows[index]
                if (layout.x != rect.left || layout.y != rect.top || layout.width != rect.width() ||
                    layout.height != rect.height() || layout.flags != flags) {
                    layout.x = rect.left
                    layout.y = rect.top
                    layout.width = rect.width()
                    layout.height = rect.height()
                    layout.flags = flags
                    if (runCatching { manager.updateViewLayout(view, layout) }.isFailure) {
                        clearLegacyWindows()
                        return
                    }
                }
            } else {
                val view = ReadingTapView(context).apply {
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    onOutsideTouch = ::outside
                    onTap = { x, y ->
                        if (!hidden && region.contains(x.toInt(), y.toInt())) interaction?.onTap?.invoke(x, y)
                    }
                }
                val layout = WindowManager.LayoutParams(rect.width(), rect.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = rect.left
                    y = rect.top
                    alpha = params.alpha
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) layout.setFitInsetsTypes(0)
                if (runCatching { manager.addView(view, layout) }.isSuccess) legacyWindows += view to layout
                else {
                    clearLegacyWindows()
                    return
                }
            }
        }
    }

    private fun clearLegacyWindows() {
        legacyWindows.forEach { (view, _) -> runCatching { manager.removeViewImmediate(view) } }
        legacyWindows.clear()
    }

    override fun close() {
        if (closed) return
        clear()
        closed = true
        host.onOutsideTouch = { _, _ -> }
        if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnPreDrawListener(preDraw)
        clearLegacyWindows()
        appliedSurface = null
    }
}

private fun rectangles(region: Region): List<Rect> = buildList {
    val iterator = RegionIterator(region)
    val rect = Rect()
    while (iterator.next(rect)) add(Rect(rect))
}

private class ReadingTapView(context: Context) : View(context) {
    var onOutsideTouch: (Float, Float) -> Unit = { _, _ -> }
    var onTap: (Float, Float) -> Unit = { _, _ -> }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tapX = 0f
    private var tapY = 0f
    private var candidate = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> onOutsideTouch(event.rawX, event.rawY)
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                candidate = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop) {
                    candidate = false
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> candidate = false
            MotionEvent.ACTION_UP -> {
                tapX = event.rawX
                tapY = event.rawY
                if (candidate && event.x >= 0 && event.x < width && event.y >= 0 && event.y < height) performClick()
                candidate = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap(tapX, tapY)
        return true
    }
}

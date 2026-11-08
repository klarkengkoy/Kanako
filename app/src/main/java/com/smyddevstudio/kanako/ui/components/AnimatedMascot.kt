package com.smyddevstudio.kanako.ui.components

import android.animation.ValueAnimator
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.graphics.withTranslation
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AnimatedMascot(
    @DrawableRes animationRes: Int?,
    size: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes staticRes: Int = R.drawable.kanako_floating,
    playbackKey: Any = Unit,
    loop: Boolean = false,
    contentDescription: String? = null,
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
    holdLastFrame: Boolean = false,
    showStaticWhileLoading: Boolean = true,
    durationMillis: Int? = null,
    onPlaybackStarted: () -> Unit = {},
    onFinished: () -> Unit = {}
) {
    val imageModifier = modifier.size(size)
    if (LocalInspectionMode.current) {
        Image(painterResource(staticRes), contentDescription, imageModifier)
    } else {
        MascotPlayer(animationRes, staticRes, playbackKey, loop, animationsEnabled,
            holdLastFrame, showStaticWhileLoading, durationMillis, onPlaybackStarted, onFinished,
            imageModifier.then(if (contentDescription == null) Modifier else Modifier.semantics {
                this.contentDescription = contentDescription
            }))
    }
}

private data class MascotRequest(val resource: Int?, val key: Any, val durationMillis: Int?)
private data class DecodedMascot(val request: MascotRequest, val playback: MascotPlayback?)

private interface MascotPlayback {
    val drawable: Drawable
    fun isRunning(): Boolean
    fun start()
    fun stop()
    fun setLoop(loop: Boolean)
    fun onEnd(callback: (() -> Unit)?)
}

@Composable
private fun MascotPlayer(
    @DrawableRes animationRes: Int?,
    @DrawableRes staticRes: Int,
    playbackKey: Any,
    loop: Boolean,
    animationsEnabled: Boolean,
    holdLastFrame: Boolean,
    showStaticWhileLoading: Boolean,
    durationMillis: Int?,
    onPlaybackStarted: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    val request = remember(animationRes, playbackKey, durationMillis) {
        MascotRequest(animationRes, playbackKey, durationMillis)
    }
    val decoded by produceState<DecodedMascot?>(null, request, resources, animationsEnabled) {
        value = null
        if (!animationsEnabled || animationRes == null) return@produceState
        value = DecodedMascot(request, withContext(Dispatchers.IO) {
            runCatching { decodeMascot(resources, animationRes, loop, durationMillis) }.getOrNull()
        })
    }
    val current = decoded?.takeIf { it.request == request }
    val player = current?.playback
    var finished by remember(request) { mutableStateOf(false) }
    var notified by remember(request) { mutableStateOf(false) }
    var started by remember(request) { mutableStateOf(false) }
    val latestPlaybackStarted by rememberUpdatedState(onPlaybackStarted)
    val latestFinished by rememberUpdatedState(onFinished)
    val latestLoop by rememberUpdatedState(loop)
    SideEffect { player?.setLoop(loop) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle, animationsEnabled, request, current != null) {
        var disposed = false
        var stopping = false
        fun complete() {
            if (!disposed && !notified) {
                finished = true
                notified = true
                latestFinished()
            }
        }
        player?.onEnd {
            if (!stopping && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) complete()
        }
        fun updatePlayback() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                player?.setLoop(latestLoop)
                // An interrupted one-shot stays stopped, but its parent can advance once visible.
                if (finished && !latestLoop) complete()
                if (!finished && animationsEnabled) {
                    player?.start()
                    if (player != null && !started) {
                        started = true
                        latestPlaybackStarted()
                    }
                }
                if (animationRes != null && !latestLoop &&
                    (!animationsEnabled || (current != null && player == null))) complete()
            } else {
                stopping = true
                if (!latestLoop && player?.isRunning() == true) finished = true
                player?.stop()
                stopping = false
            }
        }
        val observer = LifecycleEventObserver { _, _ -> updatePlayback() }
        lifecycle.addObserver(observer)
        updatePlayback()
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            player?.onEnd(null)
            player?.stop()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if (showStaticWhileLoading || !animationsEnabled || animationRes == null) {
                    setImageResource(staticRes)
                    tag = staticRes
                }
            }
        },
        onRelease = { view ->
            (view.drawable as? Animatable)?.stop()
            view.setImageDrawable(null)
        },
        update = { view ->
            when {
                !animationsEnabled || animationRes == null || (finished && !holdLastFrame) ||
                    (current != null && player == null) -> {
                    if (view.tag != staticRes || view.drawable is Animatable) {
                        view.setImageResource(staticRes)
                        view.tag = staticRes
                    }
                }
                player != null -> {
                    if (view.drawable !== player.drawable) {
                        view.setImageDrawable(player.drawable)
                        view.tag = null
                    }
                }
            }
        }
    )
}

private fun decodeMascot(
    resources: Resources, resource: Int, loop: Boolean, durationMillis: Int?
): MascotPlayback {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = if (durationMillis == null) ImageDecoder.createSource(resources, resource)
        else {
            val bytes = resources.openRawResource(resource).use { it.readBytes() }
            ImageDecoder.createSource(ByteBuffer.wrap(retimeAnimatedWebp(bytes, durationMillis)))
        }
        NativeMascotPlayback(ImageDecoder.decodeDrawable(source) as AnimatedImageDrawable, loop)
    } else {
        @Suppress("DEPRECATION")
        val movie = resources.openRawResource(resource).use { requireNotNull(Movie.decodeStream(it)) }
        GifMascotPlayback(movie, loop, durationMillis)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private class NativeMascotPlayback(
    override val drawable: AnimatedImageDrawable,
    loop: Boolean
) : MascotPlayback {
    private var onFinished: (() -> Unit)? = null
    private var callbackRegistered = false
    init { setLoop(loop) }
    override fun setLoop(loop: Boolean) {
        val repeats = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
        if (drawable.repeatCount != repeats) drawable.repeatCount = repeats
    }
    override fun isRunning() = drawable.isRunning
    override fun start() = drawable.start()
    override fun stop() = drawable.stop()
    override fun onEnd(callback: (() -> Unit)?) {
        onFinished = callback
        if (callback != null && !callbackRegistered) {
            // Register on the UI thread, and retain the framework callback for this drawable's
            // lifetime: unregistering its last callback can race a queued platform end event.
            drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable: Drawable?) { onFinished?.invoke() }
            })
            callbackRegistered = true
        }
    }
}

@Suppress("DEPRECATION")
private class GifMascotPlayback(private val movie: Movie, loop: Boolean, durationMillis: Int?) :
    Drawable(), Animatable, MascotPlayback {
    override val drawable: Drawable get() = this
    private val naturalDuration = movie.duration().coerceAtLeast(1)
    private val duration = (durationMillis ?: naturalDuration).coerceAtLeast(1)
    private var repeating = loop
    private var endAt = if (loop) null else duration.toLong()
    private var running = false
    private var startedAt = 0L
    private var elapsed = 0L
    private var callback: (() -> Unit)? = null
    private val tick = Runnable {
        if (running) {
            elapsed = SystemClock.uptimeMillis() - startedAt
            val stopAt = endAt
            if (stopAt != null && elapsed >= stopAt) {
                elapsed = stopAt - 1
                running = false
                invalidateSelf()
                callback?.invoke()
            } else {
                invalidateSelf()
                scheduleSelf(tickNext, SystemClock.uptimeMillis() + 16L)
            }
        }
    }
    private val tickNext: Runnable get() = tick

    override fun draw(canvas: Canvas) {
        val time = (elapsed % duration) * naturalDuration / duration
        movie.setTime(time.toInt())
        canvas.withTranslation(bounds.left.toFloat(), bounds.top.toFloat()) {
            scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
            movie.draw(this, 0f, 0f)
        }
    }
    override fun getIntrinsicWidth() = movie.width()
    override fun getIntrinsicHeight() = movie.height()
    override fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.uptimeMillis() - elapsed
        scheduleSelf(tick, SystemClock.uptimeMillis())
        invalidateSelf()
    }
    override fun stop() {
        if (running) elapsed = SystemClock.uptimeMillis() - startedAt
        running = false
        unscheduleSelf(tick)
    }
    override fun isRunning() = running
    override fun setLoop(loop: Boolean) {
        if (repeating == loop) return
        repeating = loop
        val position = if (running) SystemClock.uptimeMillis() - startedAt else elapsed
        endAt = if (loop) null else (position / duration + 1) * duration
    }
    override fun onEnd(callback: (() -> Unit)?) { this.callback = callback }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        enabled = ValueAnimator.areAnimatorsEnabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

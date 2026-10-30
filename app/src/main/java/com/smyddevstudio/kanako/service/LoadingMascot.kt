package com.smyddevstudio.kanako.service

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.P)
@Composable
internal fun LoadingMascot(
    size: Dp,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = rememberAnimationsEnabled()
) {
    val resources = LocalResources.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val drawable by produceState<AnimatedImageDrawable?>(null, resources, sizePx, animationsEnabled) {
        value = null
        if (!animationsEnabled) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(
                    ImageDecoder.createSource(resources, R.drawable.kanako_peek_blink)
                ) { decoder, info, _ ->
                    val scale = sizePx.toFloat() / maxOf(info.size.width, info.size.height)
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }.let { it as AnimatedImageDrawable }.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                }
            }.getOrNull()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(drawable, lifecycle, animationsEnabled) {
        val animation = drawable
        fun updatePlayback() {
            if (animationsEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) animation?.start()
            else animation?.stop()
        }
        val observer = LifecycleEventObserver { _, _ -> updatePlayback() }
        lifecycle.addObserver(observer)
        updatePlayback()
        onDispose {
            lifecycle.removeObserver(observer)
            animation?.stop()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageResource(R.drawable.kanako_floating)
            }
        },
        onRelease = { view ->
            (view.drawable as? AnimatedImageDrawable)?.stop()
            view.setImageDrawable(null)
        },
        update = { view ->
            if (!animationsEnabled) view.setImageResource(R.drawable.kanako_floating)
            else drawable?.let { animation ->
                if (view.drawable !== animation) view.setImageDrawable(animation)
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun rememberAnimationsEnabled(): Boolean {
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

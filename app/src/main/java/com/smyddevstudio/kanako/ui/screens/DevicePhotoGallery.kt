package com.smyddevstudio.kanako.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.view.SurfaceView
import android.widget.photopicker.EmbeddedPhotoPickerClient
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import android.widget.photopicker.EmbeddedPhotoPickerProviderFactory
import android.widget.photopicker.EmbeddedPhotoPickerSession
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.components.AnimatedMascot
import com.smyddevstudio.kanako.ui.components.rememberAnimationsEnabled
import com.smyddevstudio.kanako.ui.theme.KanakoTheme

internal fun isEmbeddedPhotoPickerAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicePhotoGallery(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    onUseSystemPicker: () -> Unit = onBack
) {
    val preview = LocalInspectionMode.current
    val colors = MaterialTheme.colorScheme
    val accentColor = Color.pack(colors.primary.toArgb())
    val nightMode = if (colors.surface.luminance() < 0.5f) Configuration.UI_MODE_NIGHT_YES
        else Configuration.UI_MODE_NIGHT_NO
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnOpen by rememberUpdatedState(onOpen)
    val latestFallback by rememberUpdatedState(onUseSystemPicker)
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val embedded = isEmbeddedPhotoPickerAvailable()
    if (!embedded && !preview) LaunchedEffect(Unit) { latestFallback() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        CenterAlignedTopAppBar(
            title = { GalleryKanakoTitle() },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_back))
            } }
        )
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (embedded && foreground && !preview) {
                key(accentColor, nightMode) {
                    AndroidView(
                        factory = { context -> EmbeddedPickerHost(context,
                            accentColor = accentColor,
                            nightMode = nightMode,
                            onOpen = { latestOnOpen(it) },
                            onUnavailable = { latestFallback() }) },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.dispose() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryKanakoTitle(modifier: Modifier = Modifier) {
    var greetingVersion by remember { mutableIntStateOf(0) }
    val animationsEnabled = rememberAnimationsEnabled()
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = { greetingVersion++ }, modifier = Modifier.size(48.dp)) {
            AnimatedMascot(
                animationRes = R.drawable.kanako_welcome_wave,
                size = 48.dp,
                staticRes = R.drawable.kanako_mascot,
                playbackKey = greetingVersion,
                contentDescription = stringResource(R.string.replay_mascot_greeting),
                animationsEnabled = animationsEnabled,
                holdLastFrame = true
            )
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/** Owns one remote picker session; never retains a session while another screen is active. */
@RequiresApi(34)
@RequiresExtension(extension = 34, version = 15)
private class EmbeddedPickerHost(
    context: Context,
    private val accentColor: Long,
    private val nightMode: Int,
    private val onOpen: (Uri) -> Unit,
    private val onUnavailable: () -> Unit
) : SurfaceView(context) {
    private var session: EmbeddedPhotoPickerSession? = null
    private var opening = false
    private var disposed = false
    private var failed = false
    private var delivered = false
    private var generation = 0

    init {
        // The picker owns a secure remote surface and requires this Z order.
        setZOrderOnTop(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { openIfReady() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            try {
                session?.notifyResized(w, h)
                openIfReady()
            } catch (_: RuntimeException) { unavailable() }
        }
    }

    private fun openIfReady() {
        if (disposed || failed || opening || session != null || !isAttachedToWindow ||
            width <= 0 || height <= 0) return
        @Suppress("DEPRECATION")
        val token = hostToken ?: return
        opening = true
        val request = ++generation
        val client = object : EmbeddedPhotoPickerClient {
            override fun onSessionOpened(opened: EmbeddedPhotoPickerSession) {
                if (disposed || failed || generation != request || !isAttachedToWindow) {
                    opened.close()
                    return
                }
                opening = false
                session = opened
                try {
                    setChildSurfacePackage(opened.surfacePackage)
                    opened.notifyPhotoPickerExpanded(true)
                    opened.notifyVisibilityChanged(windowVisibility == VISIBLE)
                    opened.notifyResized(width, height)
                } catch (_: RuntimeException) { unavailable() }
            }

            override fun onSessionError(error: Throwable) {
                if (!disposed && generation == request) unavailable()
            }

            override fun onUriPermissionGranted(uris: List<Uri>) {
                if (!disposed && !failed && !delivered && generation == request) {
                    uris.firstOrNull()?.let {
                        delivered = true
                        onOpen(it)
                    }
                }
            }

            override fun onUriPermissionRevoked(uris: List<Uri>) = Unit
            override fun onSelectionComplete() = Unit
        }
        try {
            val features = EmbeddedPhotoPickerFeatureInfo.Builder()
                .setMaxSelectionLimit(1)
                .setMimeTypes(listOf("image/*"))
                .setAccentColor(accentColor)
                .setThemeNightMode(nightMode)
                .build()
            EmbeddedPhotoPickerProviderFactory.create(context).openSession(
                token, display.displayId, width, height, features, context.mainExecutor, client
            )
        } catch (_: RuntimeException) { unavailable() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try { session?.notifyConfigurationChanged(newConfig) }
        catch (_: RuntimeException) { unavailable() }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        try { session?.notifyVisibilityChanged(visibility == VISIBLE) }
        catch (_: RuntimeException) { unavailable() }
    }

    override fun onDetachedFromWindow() {
        closeSession()
        super.onDetachedFromWindow()
    }

    fun dispose() {
        disposed = true
        closeSession()
    }

    private fun closeSession() {
        generation++
        opening = false
        val previous = session
        session = null
        try { previous?.close() } catch (_: RuntimeException) { }
    }

    private fun unavailable() {
        if (disposed || failed) return
        failed = true
        closeSession()
        // Navigation must not remove this view from inside its layout callback.
        post { if (!disposed) onUnavailable() }
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicePhotoGalleryPreview() {
    KanakoTheme {
        DevicePhotoGallery(onBack = {}, onOpen = {})
    }
}

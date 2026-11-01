package com.smyddevstudio.kanako.service

import android.app.Notification
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.smyddevstudio.kanako.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.smyddevstudio.kanako.MainActivity
import com.smyddevstudio.kanako.data.repository.UserPreferencesRepository
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class KanakoOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var annotationOverlayView: ComposeView? = null
    private var screenCaptureEngine: ScreenCaptureEngine? = null
    private val recognizedTextLines = mutableStateOf<List<RecognizedTextLine>>(emptyList())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var layoutParams: WindowManager.LayoutParams? = null
    private var annotationLayoutParams: WindowManager.LayoutParams? = null
    private val annotationOrigin = mutableStateOf(IntOffset.Zero)
    private var floatingAnchor: FloatingPoint? = null
    private var floatingContentSize = androidx.compose.ui.unit.IntSize.Zero
    private val floatingControlOffset = mutableStateOf(IntOffset.Zero)
    private val floatingMenuAbove = mutableStateOf(false)
    private val readingStatus = mutableIntStateOf(R.string.reading_ready)
    private val isReading = mutableStateOf(false)
    private val requestGate = SnapshotRequestGate()
    private var lastScreenRevision = KanakoScreenContext.revision.value
    private var unsubscribeScreenUpdates: (() -> Unit)? = null
    private val floatingOnLeft = mutableStateOf(false)
    private var floatingControlWidth = 0
    private var automaticReadPending = false
    private var activeRequestIsAutomatic = false
    private var autoReadPausedUntilMovement = false
    private val autoReader = AutoReadDebouncer(serviceScope, { requestReading(automatic = true) })
    private var inputHidden = false
    private var screenCapturePaused = false
    private var screenStateReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenCapturePaused = true
                    clearReading()
                    setOverlayHidden(true)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenCapturePaused = getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    clearReading()
                    setOverlayHidden(inputHidden || screenCapturePaused)
                    scheduleAutomaticRead()
                }
            }
        }
    }

    private lateinit var preferencesRepository: UserPreferencesRepository

    companion object {
        const val CHANNEL_ID = "kanako_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.smyddevstudio.kanako.service.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.smyddevstudio.kanako.service.ACTION_STOP"
        fun canDrawOverlays(context: Context): Boolean =
            Settings.canDrawOverlays(context)

        fun createOverlayPermissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )

        fun startService(context: Context) {
            val intent = Intent(context, KanakoOverlayService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, KanakoOverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun setOverlayHidden(hidden: Boolean) {
        val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        overlayView?.visibility = visibility
        annotationOverlayView?.visibility = visibility
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        preferencesRepository = UserPreferencesRepository(applicationContext)
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenStateReceiverRegistered = true
        screenCapturePaused = !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }

        if (!canDrawOverlays(this) || !KanakoAccessibilityService.connected.value ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        ) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenCaptureEngine = ScreenCaptureEngine()
        serviceScope.launch {
            val initialPreferences = preferencesRepository.userPreferencesFlow.first()
            showRecognizedTextOverlay()
            showOverlayView(initialPreferences)
        }

        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesRepository.userPreferencesFlow,
                KanakoInputVisibility.hidden
            ) { prefs, inputVisible ->
                prefs.autoHideOnKeyboard && inputVisible
            }.collect { hidden ->
                inputHidden = hidden
                setOverlayHidden(hidden || screenCapturePaused)
            }
        }
        lastScreenRevision = KanakoScreenContext.revision.value
        unsubscribeScreenUpdates = KanakoScreenContext.updates.subscribe(::handleScreenUpdate)
        serviceScope.launch {
            KanakoInputVisibility.hidden.collect { hidden ->
                if (hidden) clearReading(R.string.reading_sensitive)
            }
        }
        serviceScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                if (autoReader.enabled != prefs.autoReadAfterScroll) {
                    autoReader.enabled = prefs.autoReadAfterScroll
                    automaticReadPending = false
                    autoReadPausedUntilMovement = false
                    if (prefs.autoReadAfterScroll) {
                        scheduleAutomaticRead()
                    } else if (activeRequestIsAutomatic) {
                        requestGate.invalidate()
                        activeRequestIsAutomatic = false
                        isReading.value = false
                        setOverlayHidden(inputHidden || screenCapturePaused)
                    }
                }
            }
        }
        serviceScope.launch {
            KanakoAccessibilityService.connected.collect { connected ->
                if (!connected) {
                    clearReading()
                    KanakoOverlaySession.setActive(false)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP_SERVICE) {
            KanakoOverlaySession.setActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START_SERVICE ||
            !canDrawOverlays(this) || !KanakoAccessibilityService.connected.value ||
            screenCaptureEngine == null
        ) {
            stopSelf()
        } else {
            KanakoOverlaySession.setActive(true)
            scheduleAutomaticRead()
        }
        return START_NOT_STICKY
    }

    private fun clearReading(message: Int = R.string.reading_ready) {
        autoReader.cancel()
        automaticReadPending = false
        requestGate.invalidate()
        recognizedTextLines.value = emptyList()
        isReading.value = false
        readingStatus.intValue = message
    }

    private fun readOrClear() {
        autoReader.cancel()
        automaticReadPending = false
        if (isReading.value || recognizedTextLines.value.isNotEmpty()) {
            clearReading()
            autoReadPausedUntilMovement = true
        } else {
            autoReadPausedUntilMovement = false
            requestReading(automatic = false)
        }
    }

    private fun scheduleAutomaticRead() {
        autoReader.onMovement(
            !autoReadPausedUntilMovement && !screenCapturePaused &&
                !KanakoInputVisibility.hidden.value && KanakoOverlaySession.isActive.value &&
                KanakoAccessibilityService.connected.value
        )
    }

    private fun handleScreenUpdate(update: ScreenUpdate) {
        val skippedEvents = update.revision != lastScreenRevision + 1
        lastScreenRevision = update.revision
        requestGate.invalidate()
        isReading.value = false
        automaticReadPending = false
        if (update.kind == ScreenUpdate.Kind.RESET || screenCapturePaused ||
            KanakoInputVisibility.hidden.value
        ) {
            clearReading()
            return
        }
        if (update.kind == ScreenUpdate.Kind.CONTENT_PENDING) {
            autoReader.cancel()
            return
        }
        val movement = update.movement
        recognizedTextLines.value = if (
            update.kind == ScreenUpdate.Kind.SCROLL && movement != null && !skippedEvents
        ) {
            recognizedTextLines.value.mapNotNull { line ->
                if (line.windowId != movement.windowId) return@mapNotNull null
                val bounds = ReadingBounds(line.bounds.left, line.bounds.top, line.bounds.right, line.bounds.bottom)
                followScroll(bounds, line.clip, movement)?.let { tracked ->
                    line.copy(
                        bounds = Rect(tracked.bounds.left, tracked.bounds.top, tracked.bounds.right, tracked.bounds.bottom),
                        clip = tracked.clip,
                        spans = line.spans.map { span ->
                            span.copy(bounds = span.bounds.moved(
                                tracked.bounds.left - bounds.left, tracked.bounds.top - bounds.top))
                        }
                    )
                }
            }
        } else emptyList()
        readingStatus.intValue = if (recognizedTextLines.value.isEmpty()) R.string.reading_ready
            else R.string.reading_shown
        autoReadPausedUntilMovement = false
        if (update.canAutoRead) scheduleAutomaticRead() else autoReader.cancel()
    }

    private fun requestReading(automatic: Boolean) {
        if (!canDrawOverlays(this) || !KanakoAccessibilityService.connected.value) {
            stopSelf()
            return
        }
        if (automatic && (!autoReader.enabled || autoReadPausedUntilMovement ||
                !KanakoOverlaySession.isActive.value)) return
        if (screenCapturePaused || KanakoInputVisibility.hidden.value) {
            clearReading(R.string.reading_sensitive)
            return
        }
        val engine = screenCaptureEngine ?: return
        activeRequestIsAutomatic = automatic
        val token = requestGate.begin()
        val revision = KanakoScreenContext.revision.value
        isReading.value = true
        readingStatus.intValue = R.string.reading_working
        val hideForCapture = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (hideForCapture) setOverlayHidden(true)
        mainHandler.postDelayed({
            if (!requestGate.isCurrent(token) || revision != KanakoScreenContext.revision.value) {
                setOverlayHidden(inputHidden || screenCapturePaused)
                return@postDelayed
            }
            val accepted = engine.readOnce(
                shouldProcess = {
                    requestGate.isCurrent(token) &&
                        revision == KanakoScreenContext.revision.value &&
                        !KanakoInputVisibility.hidden.value && !screenCapturePaused
                },
                onCaptured = { setOverlayHidden(inputHidden || screenCapturePaused) },
                onFinished = {
                    if (automaticReadPending) {
                        automaticReadPending = false
                        scheduleAutomaticRead()
                    }
                }
            ) { result ->
                setOverlayHidden(inputHidden || screenCapturePaused)
                if (!requestGate.isCurrent(token) ||
                    revision != KanakoScreenContext.revision.value ||
                    KanakoInputVisibility.hidden.value || screenCapturePaused
                ) return@readOnce
                isReading.value = false
                result.fold(
                    onSuccess = { lines ->
                        recognizedTextLines.value = lines
                        readingStatus.intValue = if (lines.any { line ->
                            line.text.any { it in '\u3400'..'\u9FFF' || it == '々' }
                        }) R.string.reading_shown else R.string.reading_no_text
                    },
                    onFailure = { error ->
                        recognizedTextLines.value = emptyList()
                        readingStatus.intValue = when ((error as? SnapshotFailure)?.reason) {
                            SnapshotFailure.Reason.SERVICE_UNAVAILABLE -> R.string.reading_service_missing
                            SnapshotFailure.Reason.UNSUPPORTED -> R.string.reading_unsupported
                            SnapshotFailure.Reason.SENSITIVE_SCREEN -> R.string.reading_sensitive
                            SnapshotFailure.Reason.NO_SOURCE -> R.string.reading_no_source
                            SnapshotFailure.Reason.PROTECTED_CONTENT -> R.string.reading_protected
                            SnapshotFailure.Reason.TOO_SOON -> R.string.reading_wait
                            SnapshotFailure.Reason.MODEL_UNAVAILABLE -> R.string.reading_model_missing
                            SnapshotFailure.Reason.OCR_FAILED -> R.string.reading_ocr_failed
                            else -> R.string.reading_capture_failed
                        }
                    }
                )
            }
            if (!accepted) {
                automaticReadPending = automatic
                isReading.value = false
                readingStatus.intValue = R.string.reading_wait
                setOverlayHidden(inputHidden || screenCapturePaused)
            }
        }, if (hideForCapture) 120L else 0L)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        clearReading()
        overlayView?.post { positionFloatingOverlay() }
    }

    private fun showRecognizedTextOverlay() {
        if (annotationOverlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.8f
        }

        annotationLayoutParams = params
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@KanakoOverlayService)
            setViewTreeSavedStateRegistryOwner(this@KanakoOverlayService)
            setViewTreeViewModelStoreOwner(this@KanakoOverlayService)
            setContent {
                KanakoTheme {
                    val preferences by preferencesRepository.userPreferencesFlow
                        .collectAsStateWithLifecycle(initialValue = KanakoPreferences())
                    val hasAnnotations = recognizedTextLines.value.isNotEmpty()
                    LaunchedEffect(preferences.touchPassthrough, hasAnnotations) {
                        val currentParams = annotationLayoutParams ?: return@LaunchedEffect
                        currentParams.flags = if (preferences.touchPassthrough || !hasAnnotations) {
                            currentParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        } else {
                            currentParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        }
                        runCatching { windowManager?.updateViewLayout(this@apply, currentParams) }
                    }
                    RecognizedTextOverlayContent(
                        lines = recognizedTextLines.value,
                        preferences = preferences,
                        origin = annotationOrigin.value
                    )
                }
            }
        }

        view.addOnLayoutChangeListener { current, _, _, _, _, _, _, _, _ ->
            val location = IntArray(2)
            current.getLocationOnScreen(location)
            annotationOrigin.value = IntOffset(location[0], location[1])
        }
        annotationOverlayView = view
        windowManager?.addView(view, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, KanakoOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_description))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.controls_stop),
                pendingStop
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showOverlayView(initialPreferences: KanakoPreferences) {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val control = (when (initialPreferences.fabSize) {
                FabSize.COMPACT -> 48
                FabSize.MEDIUM -> 56
                FabSize.LARGE -> 68
            } * resources.displayMetrics.density).roundToInt()
            floatingControlWidth = control
            val saved = if (initialPreferences.hasSavedFloatingPosition)
                FloatingPoint(initialPreferences.fabOffsetX, initialPreferences.fabOffsetY) else null
            floatingAnchor = floatingAnchor(usableFloatingArea(), control, saved)
            x = floatingAnchor!!.x
            y = floatingAnchor!!.y
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@KanakoOverlayService)
            setViewTreeSavedStateRegistryOwner(this@KanakoOverlayService)
            setViewTreeViewModelStoreOwner(this@KanakoOverlayService)

            setContent {
                KanakoTheme {
                    val preferences by preferencesRepository.userPreferencesFlow
                        .collectAsStateWithLifecycle(initialValue = initialPreferences)

                    FloatingOverlayContent(
                        preferences = preferences,
                        mirrorMascot = floatingOnLeft.value,
                        controlOffset = { floatingControlOffset.value },
                        menuAbove = { floatingMenuAbove.value },
                        isReading = isReading.value,
                        statusMessage = readingStatus.intValue,
                        onControlSizeChanged = { width ->
                            floatingControlWidth = width
                            positionFloatingOverlay()
                        },
                        showFurigana = recognizedTextLines.value.isNotEmpty(),
                        onReadOrClear = ::readOrClear,
                        onToggleFurigana = { clearReading() },
                        onLayoutSizeChanged = { size ->
                            floatingContentSize = size
                            positionFloatingOverlay()
                        },
                        onDrag = { dx, dy ->
                            val anchor = floatingAnchor ?: return@FloatingOverlayContent
                            floatingAnchor = floatingAnchor(
                                usableFloatingArea(), floatingControlWidth,
                                FloatingPoint((anchor.x + dx).roundToInt(), (anchor.y + dy).roundToInt())
                            )
                            positionFloatingOverlay()
                        },
                        onDragEnd = {
                            val anchor = floatingAnchor
                            if (anchor != null) serviceScope.launch {
                                preferencesRepository.updateFabOffset(anchor.x, anchor.y)
                            }
                        },
                        onToggleScriptType = {
                            val nextScript = when (preferences.scriptType) {
                                ScriptType.KANA -> ScriptType.ROMAJI
                                ScriptType.ROMAJI -> ScriptType.KANA
                            }
                            serviceScope.launch {
                                preferencesRepository.updateScriptType(nextScript)
                            }
                        },
                        onOpenApp = {
                            val appIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(appIntent)
                        },
                        onStopService = {
                            KanakoOverlaySession.setActive(false)
                            stopSelf()
                        }
                    )
                }
            }
        }

        overlayView = view
        view.visibility = View.INVISIBLE
        windowManager?.addView(view, params)
    }

    private fun usableFloatingArea(): ReadingBounds {
        val manager = windowManager ?: return ReadingBounds(0, 0, 1, 1)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            manager.currentWindowMetrics.bounds else Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            Rect(bars.left, bars.top, bars.right, bars.bottom)
        } else Rect()
        val margin = (8 * resources.displayMetrics.density).roundToInt()
        return ReadingBounds(
            bounds.left + insets.left + margin,
            bounds.top + insets.top + margin,
            bounds.right - insets.right - margin,
            bounds.bottom - insets.bottom - margin
        )
    }

    private fun positionFloatingOverlay() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val anchor = floatingAnchor ?: return
        if (floatingContentSize.width == 0 || floatingControlWidth == 0) return
        val frame = floatingFrame(
            usableFloatingArea(), anchor, floatingControlWidth,
            floatingContentSize.width, floatingContentSize.height
        )
        floatingAnchor = frame.anchor
        floatingControlOffset.value = IntOffset(frame.control.x, frame.control.y)
        floatingMenuAbove.value = frame.menuAbove
        floatingOnLeft.value = frame.onLeft
        params.x = frame.origin.x
        params.y = frame.origin.y
        runCatching { windowManager?.updateViewLayout(view, params) }
        setOverlayHidden(inputHidden || screenCapturePaused)
    }

    override fun onDestroy() {
        unsubscribeScreenUpdates?.invoke()
        unsubscribeScreenUpdates = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        if (screenStateReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenStateReceiverRegistered = false
        }
        KanakoOverlaySession.setActive(false)
        clearReading()
        autoReader.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        screenCaptureEngine?.close()
        screenCaptureEngine = null
        annotationOverlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        annotationOverlayView = null
        annotationLayoutParams = null
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        store.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}


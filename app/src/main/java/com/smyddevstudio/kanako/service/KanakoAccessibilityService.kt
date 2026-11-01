package com.smyddevstudio.kanako.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.PowerManager
import android.text.InputType
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityManager
import com.smyddevstudio.kanako.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object KanakoInputVisibility {
    val hidden = MutableStateFlow(false)
}

internal object KanakoScreenContext {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    val updates = ScreenUpdateDispatcher()

    fun publish(kind: ScreenUpdate.Kind, movement: ScrollMovement? = null, canAutoRead: Boolean = false) {
        mutableRevision.value++
        updates.publish(ScreenUpdate(mutableRevision.value, kind, movement, canAutoRead))
    }

    fun invalidate() = publish(ScreenUpdate.Kind.RESET)
}

internal data class ScreenshotFrame(
    val bitmap: Bitmap,
    val left: Int,
    val top: Int,
    val windowId: Int,
    val viewport: ReadingBounds
)

internal fun isSensitiveInputType(isPassword: Boolean, inputType: Int): Boolean {
    if (isPassword) return true

    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

class KanakoAccessibilityService : AccessibilityService() {
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotConsentAccepted = false
    private var activeWindowId: Int? = null
    private val scrollOffsets = ScrollOffsetTracker()
    private val scrollContent = ScrollContentCorrelation()
    private var pendingContent: Job? = null
    private var pendingContentBounds: ReadingBounds? = null

    companion object {
        private var instance: KanakoAccessibilityService? = null
        private val mutableConnected = MutableStateFlow(false)
        val connected = mutableConnected.asStateFlow()

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo.serviceInfo
                    ComponentName(service.packageName, service.name) ==
                        ComponentName(context, KanakoAccessibilityService::class.java)
                }

        internal fun requestScreenshot(callback: (Result<ScreenshotFrame>) -> Unit) {
            val service = instance
            if (service == null) {
                callback(Result.failure(SnapshotFailure(SnapshotFailure.Reason.SERVICE_UNAVAILABLE)))
            } else {
                service.captureOnce(callback)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        instance = this
        mutableConnected.value = true
        updateHiddenState()
        serviceScope.launch {
            UserPreferencesRepository(applicationContext).userPreferencesFlow.collect { preferences ->
                snapshotConsentAccepted = preferences.hasAcceptedSnapshotDisclosure
                if (!snapshotConsentAccepted) KanakoScreenContext.invalidate()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val root = applicationRoot()
        val currentWindow = root?.windowId
        val currentPackage = root?.packageName?.toString()
        root?.let(::recycle)
        if (isReaderOverlayEvent(
                packageName, event.packageName?.toString(), event.windowId, currentPackage, currentWindow
            )) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            updateHiddenState(event.source)
        } else {
            updateHiddenState()
        }
        val eligible = currentPackage != null && currentPackage != packageName &&
            !KanakoInputVisibility.hidden.value
        if (currentWindow != activeWindowId) {
            activeWindowId = currentWindow
            pendingContent?.cancel()
            scrollContent.clear()
            scrollOffsets.clear()
            KanakoScreenContext.publish(ScreenUpdate.Kind.WINDOW, canAutoRead = eligible)
            return
        }
        if (!eligible || event.windowId != currentWindow) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = try { event.source } catch (_: SecurityException) { null }
                val movement = source?.let { node ->
                    try {
                        val viewport = Rect()
                        node.getBoundsInScreen(viewport)
                        val delta = scrollOffsets.delta(
                            event.windowId, node.hashCode(), event.scrollX, event.scrollY,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaX else 0,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
                        )
                        if (delta == null || viewport.isEmpty) null else ScrollMovement(
                            event.windowId,
                            ReadingBounds(viewport.left, viewport.top, viewport.right, viewport.bottom),
                            delta.first, delta.second
                        )
                    } finally { recycle(node) }
                }
                scrollContent.onScroll(movement, event.eventTime)
                if (scrollContent.matches(event.windowId, pendingContentBounds, event.eventTime)) {
                    pendingContent?.cancel()
                    pendingContentBounds = null
                }
                KanakoScreenContext.publish(ScreenUpdate.Kind.SCROLL, movement, canAutoRead = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val subtreeOnly = event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                val source = if (subtreeOnly) try { event.source } catch (_: SecurityException) { null } else null
                val bounds = source?.let { node ->
                    try {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        ReadingBounds(rect.left, rect.top, rect.right, rect.bottom).takeUnless { it.isEmpty }
                    } finally { recycle(node) }
                }
                if (subtreeOnly && scrollContent.matches(event.windowId, bounds, event.eventTime)) return
                pendingContent?.cancel()
                pendingContentBounds = bounds
                if (subtreeOnly && bounds != null) {
                    KanakoScreenContext.publish(ScreenUpdate.Kind.CONTENT_PENDING)
                    pendingContent = serviceScope.launch {
                        delay(100)
                        pendingContentBounds = null
                        KanakoScreenContext.publish(ScreenUpdate.Kind.CONTENT, canAutoRead = true)
                    }
                } else {
                    scrollContent.clear()
                    KanakoScreenContext.publish(ScreenUpdate.Kind.CONTENT, canAutoRead = true)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                pendingContent?.cancel()
                scrollContent.clear()
                scrollOffsets.clear()
                KanakoScreenContext.publish(ScreenUpdate.Kind.CONTENT, canAutoRead = true)
            }
        }
    }

    override fun onInterrupt() {
        KanakoScreenContext.invalidate()
        KanakoInputVisibility.hidden.value = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        KanakoScreenContext.invalidate()
    }

    private fun disconnect() {
        if (instance === this) {
            instance = null
            mutableConnected.value = false
            KanakoInputVisibility.hidden.value = true
            KanakoScreenContext.invalidate()
        }
        snapshotConsentAccepted = false
        serviceScope.cancel()
    }

    private fun captureOnce(callback: (Result<ScreenshotFrame>) -> Unit) {
        fun fail(reason: SnapshotFailure.Reason) {
            callback(Result.failure(SnapshotFailure(reason)))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            fail(SnapshotFailure.Reason.UNSUPPORTED)
            return
        }
        if (!snapshotConsentAccepted || !KanakoOverlaySession.isActive.value) {
            fail(SnapshotFailure.Reason.SERVICE_UNAVAILABLE)
            return
        }
        updateHiddenState()
        if (KanakoInputVisibility.hidden.value ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ) {
            fail(SnapshotFailure.Reason.SENSITIVE_SCREEN)
            return
        }
        val root = applicationRoot()
        if (root == null) {
            fail(SnapshotFailure.Reason.NO_SOURCE)
            return
        }
        val sourcePackage: String?
        val sourceWindow: Int
        try {
            sourcePackage = root.packageName?.toString()
            sourceWindow = root.windowId
        } finally {
            recycle(root)
        }
        if (sourcePackage == null || sourcePackage == packageName) {
            fail(SnapshotFailure.Reason.NO_SOURCE)
            return
        }
        val sourceBounds = Rect()
        run {
            var found = false
            val visibleWindows = try { windows } catch (_: SecurityException) { emptyList() }
            visibleWindows.forEach { window ->
                try {
                    if (window.id == sourceWindow && window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        window.getBoundsInScreen(sourceBounds)
                        found = true
                    }
                } finally { recycle(window) }
            }
            if (!found) {
                fail(SnapshotFailure.Reason.NO_SOURCE)
                return
            }
        }
        val screenshotCallback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = try {
                    Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { hardware ->
                        try { hardware.copy(Bitmap.Config.ARGB_8888, false) }
                        finally { hardware.recycle() }
                    }
                } catch (_: RuntimeException) {
                    null
                } finally {
                    buffer.close()
                }
                if (bitmap == null) {
                    fail(SnapshotFailure.Reason.CAPTURE_FAILED)
                } else {
                    val windowCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    callback(Result.success(ScreenshotFrame(
                        bitmap,
                        if (windowCapture) sourceBounds.left else 0,
                        if (windowCapture) sourceBounds.top else 0,
                        sourceWindow,
                        ReadingBounds(sourceBounds.left, sourceBounds.top, sourceBounds.right, sourceBounds.bottom)
                    )))
                }
            }

            override fun onFailure(errorCode: Int) {
                fail(when (errorCode) {
                    ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> SnapshotFailure.Reason.PROTECTED_CONTENT
                    ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> SnapshotFailure.Reason.TOO_SOON
                    ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> SnapshotFailure.Reason.SERVICE_UNAVAILABLE
                    else -> SnapshotFailure.Reason.CAPTURE_FAILED
                })
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                takeScreenshotOfWindow(sourceWindow, mainExecutor, screenshotCallback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, screenshotCallback)
            }
        } catch (_: SecurityException) {
            fail(SnapshotFailure.Reason.SERVICE_UNAVAILABLE)
        } catch (_: RuntimeException) {
            fail(SnapshotFailure.Reason.CAPTURE_FAILED)
        }
    }

    private fun updateHiddenState(focusedNode: AccessibilityNodeInfo? = null) {
        val focusedFieldSensitive = focusedNode?.let { node ->
            try { isSensitiveField(node) } finally { recycle(node) }
        } ?: isFocusedFieldSensitive()
        val hidden = isInputMethodVisible() || focusedFieldSensitive
        if (KanakoInputVisibility.hidden.value != hidden) {
            KanakoInputVisibility.hidden.value = hidden
            KanakoScreenContext.invalidate()
        }
    }

    private fun isInputMethodVisible(): Boolean {
        val visibleWindows = try {
            windows
        } catch (_: SecurityException) {
            return true
        }
        var inputMethodVisible = false
        visibleWindows.forEach { window ->
            try {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    inputMethodVisible = true
                }
            } finally {
                recycle(window)
            }
        }
        return inputMethodVisible
    }

    private fun isFocusedFieldSensitive(): Boolean {
        val root = applicationRoot() ?: return false

        return try {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            try {
                isSensitiveField(focusedNode)
            } finally {
                if (focusedNode !== root) recycle(focusedNode)
            }
        } catch (_: SecurityException) {
            false
        } finally {
            recycle(root)
        }
    }

    private fun applicationRoot(): AccessibilityNodeInfo? {
        val visibleWindows = try { windows } catch (_: SecurityException) { return null }
        var selected: AccessibilityNodeInfo? = null
        var selectedPriority = 0
        visibleWindows.forEach { window ->
            try {
                val priority = applicationWindowPriority(
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                    window.isFocused, window.isActive
                )
                if (priority > selectedPriority) {
                    val root = try { window.root } catch (_: SecurityException) { null }
                    if (root != null) {
                        selected?.let(::recycle)
                        selected = root
                        selectedPriority = priority
                    }
                }
            } finally { recycle(window) }
        }
        return selected
    }

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean =
        isSensitiveInputType(
            isPassword = node.isPassword,
            inputType = node.inputType
        )

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    @Suppress("DEPRECATION")
    private fun recycle(window: AccessibilityWindowInfo) {
        window.recycle()
    }

}

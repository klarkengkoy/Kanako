package com.smyddevstudio.kanako.ui.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.service.KanakoAccessibilityService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.smyddevstudio.kanako.data.repository.UserPreferencesRepository
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.service.KanakoOverlayService
import com.smyddevstudio.kanako.service.KanakoOverlaySession
import com.smyddevstudio.kanako.ui.screens.HomeScreen
import com.smyddevstudio.kanako.ui.screens.HowItWorksScreen
import com.smyddevstudio.kanako.ui.screens.PreferencesScreen
import com.smyddevstudio.kanako.ui.screens.CameraReaderScreen
import com.smyddevstudio.kanako.ui.screens.CameraResultScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
fun KanakoApp(
    modifier: Modifier = Modifier,
    startCaptureRequest: Int = 0
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val repository = remember(context) { UserPreferencesRepository(context) }
    val preferencesFlow = remember(repository) {
        combine(repository.userPreferencesFlow, KanakoOverlaySession.isActive) { preferences, isActive ->
            preferences.copy(isOverlayActive = isActive)
        }
    }
    val preferences by preferencesFlow.collectAsStateWithLifecycle(initialValue = KanakoPreferences())
    val coroutineScope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(Screen.Home)
    LaunchedEffect(repository) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        repository.initializeAutoReadOffer(packageInfo.firstInstallTime == packageInfo.lastUpdateTime)
    }
    val cameraReader: CameraReaderViewModel = viewModel()
    val hasCameraEntry = Screen.Camera in backStack
    LaunchedEffect(hasCameraEntry) {
        if (!hasCameraEntry) cameraReader.returnToLive()
    }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(KanakoOverlayService.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(KanakoAccessibilityService.isEnabled(context)) }
    val accessibilityConnected by KanakoAccessibilityService.connected.collectAsStateWithLifecycle()
    var resumeEpoch by remember { mutableIntStateOf(0) }
    var pendingActivation by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    if (preferences.offerAutoRead && backStack.lastOrNull() == Screen.Home && !showDisclosure) {
        com.smyddevstudio.kanako.ui.components.AutoReadDisclosure(
            onConfirm = { coroutineScope.launch { repository.updateAutoReadAfterScroll(true) } },
            onDismiss = { coroutineScope.launch { repository.updateAutoReadAfterScroll(false) } }
        )
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = KanakoOverlayService.canDrawOverlays(context)
                accessibilityEnabled = KanakoAccessibilityService.isEnabled(context)
                resumeEpoch++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun feedback(message: Int) {
        coroutineScope.launch { snackbar.showSnackbar(resources.getString(message)) }
    }
    fun openHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    fun startReader() {
        showDisclosure = false
        pendingActivation = false
        try {
            KanakoOverlayService.startService(context)
        } catch (_: RuntimeException) {
            feedback(R.string.setup_start_failed)
        }
    }
    val handleToggleOverlay: (Boolean) -> Unit = { active ->
        if (!active) {
            pendingActivation = false
            KanakoOverlayService.stopService(context)
            feedback(R.string.setup_stopped)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            openHome()
            feedback(R.string.reading_unsupported)
        } else if (!KanakoOverlayService.canDrawOverlays(context)) {
            pendingActivation = true
            openHome()
            context.startActivity(KanakoOverlayService.createOverlayPermissionIntent(context))
        } else if (!preferences.hasAcceptedSnapshotDisclosure ||
            !KanakoAccessibilityService.isEnabled(context)
        ) {
            pendingActivation = true
            openHome()
            showDisclosure = true
        } else if (!accessibilityConnected) {
            openHome()
            feedback(R.string.setup_connecting)
        } else {
            startReader()
        }
    }
    LaunchedEffect(resumeEpoch, accessibilityConnected, preferences.hasAcceptedSnapshotDisclosure) {
        if (pendingActivation && overlayGranted) {
            if (!preferences.hasAcceptedSnapshotDisclosure || !accessibilityEnabled) {
                showDisclosure = true
            } else if (accessibilityConnected) {
                startReader()
            }
        }
    }
    LaunchedEffect(startCaptureRequest) {
        if (startCaptureRequest > 0) {
            openHome()
            feedback(R.string.setup_incomplete)
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false; pendingActivation = false },
            title = { Text(stringResource(R.string.snapshot_disclosure_title)) },
            text = {
                Text(
                    stringResource(R.string.snapshot_disclosure_body),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    coroutineScope.launch {
                        repository.acceptSnapshotDisclosure()
                        if (!pendingActivation || !KanakoAccessibilityService.isEnabled(context)) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                }) { Text(stringResource(R.string.snapshot_disclosure_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    pendingActivation = false
                }) { Text(stringResource(R.string.snapshot_disclosure_cancel)) }
            }
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier,
        entryProvider = entryProvider {
            entry<Screen.Home> {
                HomeScreen(
                    preferences = preferences,
                    onUpdateOverlayActive = handleToggleOverlay,
                    onNavigateToHowItWorks = { backStack.add(Screen.HowItWorks) },
                    onNavigateToCamera = { backStack.add(Screen.Camera) },
                    supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    overlayGranted = overlayGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    accessibilityConnected = accessibilityConnected,
                    onRequestOverlay = {
                        context.startActivity(KanakoOverlayService.createOverlayPermissionIntent(context))
                    },
                    onRequestAccessibility = { showDisclosure = true },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.SetupPermissions> {
                LaunchedEffect(Unit) { openHome() }
            }
            entry<Screen.HowItWorks> {
                HowItWorksScreen(
                    preferences = preferences,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    },
                    onNavigateToHome = {
                        openHome()
                    }
                )
            }
            entry<Screen.Camera> {
                CameraReaderScreen(
                    viewModel = cameraReader,
                    preferences = preferences,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onCaptured = { if (backStack.lastOrNull() == Screen.Camera) backStack.add(Screen.CameraResult) },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.CameraResult> {
                CameraResultScreen(
                    viewModel = cameraReader,
                    preferences = preferences,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onRetake = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.Preferences> {
                PreferencesScreen(
                    preferences = preferences,
                    onUpdateFuriganaSize = { size ->
                        coroutineScope.launch { repository.updateFuriganaSize(size) }
                    },
                    onUpdateFuriganaColor = { color ->
                        coroutineScope.launch { repository.updateFuriganaColor(color) }
                    },
                    onUpdateScriptType = { scriptType ->
                        coroutineScope.launch { repository.updateScriptType(scriptType) }
                    },
                    onUpdateAnnotationPlateStyle = { style ->
                        coroutineScope.launch { repository.updateAnnotationPlateStyle(style) }
                    },
                    onUpdateTouchPassthrough = { enabled ->
                        coroutineScope.launch { repository.updateTouchPassthrough(enabled) }
                    },
                    onUpdateFabSize = { size ->
                        coroutineScope.launch { repository.updateFabSize(size) }
                    },
                    onUpdateAutoHideOnKeyboard = { enabled ->
                        coroutineScope.launch { repository.updateAutoHideOnKeyboard(enabled) }
                    },
                    onUpdateAutoReadAfterScroll = { enabled ->
                        coroutineScope.launch { repository.updateAutoReadAfterScroll(enabled) }
                    },
                    onUpdateOverlayActive = handleToggleOverlay,
                    onRequestAccessibility = { openHome() },
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    }
                )
            }
        }
    )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

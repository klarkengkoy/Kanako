package com.smyddevstudio.kanako.ui.navigation

import com.smyddevstudio.kanako.service.GuidedReaderSession
import com.smyddevstudio.kanako.service.GuidedReaderStep
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import com.smyddevstudio.kanako.ui.screens.CameraGalleryScreen
import com.smyddevstudio.kanako.ui.screens.SavedCameraImageScreen
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.smyddevstudio.kanako.ui.screens.cameraActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smyddevstudio.kanako.camera.CameraReaderViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.smyddevstudio.kanako.data.repository.UserPreferencesRepository
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.service.KanakoOverlayService
import com.smyddevstudio.kanako.service.KanakoOverlaySession
import com.smyddevstudio.kanako.ui.screens.TutorialScreen
import com.smyddevstudio.kanako.model.TutorialPath
import com.smyddevstudio.kanako.ui.screens.HomeScreen
import com.smyddevstudio.kanako.ui.screens.HowItWorksScreen
import com.smyddevstudio.kanako.ui.screens.PreferencesScreen
import com.smyddevstudio.kanako.ui.screens.CameraReaderScreen
import com.smyddevstudio.kanako.ui.screens.CameraResultScreen
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
        repository.userPreferencesFlow
    }
    val savedPreferences by preferencesFlow.collectAsStateWithLifecycle(initialValue = KanakoPreferences())
    val readerActive by KanakoOverlaySession.isActive.collectAsStateWithLifecycle()
    val preferences = savedPreferences.copy(isOverlayActive = readerActive)
    val coroutineScope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(Screen.Home)
    var onboardingInitialized by remember { mutableStateOf(false) }
    var guidedSetup by rememberSaveable { mutableStateOf(false) }
    var lessonOwnsReader by rememberSaveable { mutableStateOf(false) }
    var cameraIntroduction by rememberSaveable { mutableStateOf(false) }
    var cameraGuide by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(repository) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        repository.initializeAutoReadOffer(packageInfo.firstInstallTime == packageInfo.lastUpdateTime)
        onboardingInitialized = true
    }
    val cameraReader: CameraReaderViewModel = viewModel()
    val hasCameraEntry = Screen.Camera in backStack
    val cameraForeground = backStack.lastOrNull() == Screen.CameraResult ||
        (backStack.lastOrNull() == Screen.Camera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    val hostView = LocalView.current
    val cameraWindow = context.cameraActivity()?.window
    DisposableEffect(cameraForeground, cameraWindow, hostView) {
        if (cameraForeground && cameraWindow != null) {
            val controller = WindowCompat.getInsetsController(cameraWindow, hostView)
            val previousLightStatus = controller.isAppearanceLightStatusBars
            val previousLightNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatus
                controller.isAppearanceLightNavigationBars = previousLightNavigation
            }
        } else onDispose {}
    }
    LaunchedEffect(hasCameraEntry) {
        if (!hasCameraEntry) { cameraReader.returnToLive(); cameraReader.clearCaptureThumbnail(); cameraGuide = false }
    }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(KanakoOverlayService.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(KanakoAccessibilityService.isEnabled(context)) }
    val accessibilityConnected by KanakoAccessibilityService.connected.collectAsStateWithLifecycle()
    LaunchedEffect(accessibilityConnected) {
        accessibilityEnabled = KanakoAccessibilityService.isEnabled(context)
    }
    var resumeEpoch by remember { mutableIntStateOf(0) }
    var pendingActivation by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    if (preferences.offerAutoRead && !preferences.offerGuidedSetup && !guidedSetup && backStack.lastOrNull() == Screen.Home && !showDisclosure) {
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
    fun openCamera(guided: Boolean) {
        val existing = backStack.indexOfLast { it == Screen.Camera || it == Screen.CameraResult }
        if (existing >= 0) {
            val reviewingPhoto = backStack[existing] == Screen.CameraResult
            cameraGuide = guided && !reviewingPhoto
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
            if (guided && reviewingPhoto) feedback(R.string.guided_camera_review_first)
        } else {
            cameraGuide = guided
            backStack.add(Screen.Camera)
        }
    }
    fun beginGuide() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            coroutineScope.launch { repository.dismissGuidedSetup(); repository.dismissTutorialInvitation() }
            feedback(R.string.reading_unsupported)
            cameraIntroduction = true
        } else if (preferences.isOverlayActive) {
            feedback(R.string.setup_stop)
        } else {
            openHome()
            GuidedReaderSession.begin()
            guidedSetup = true
            lessonOwnsReader = false
            coroutineScope.launch {
                repository.dismissGuidedSetup()
                repository.dismissTutorialInvitation()
            }
        }
    }
    fun exitGuide(completed: Boolean = false) {
        if (guidedSetup) KanakoOverlayService.stopService(context)
        lessonOwnsReader = false
        GuidedReaderSession.end()
        guidedSetup = false
        openHome()
        if (completed) coroutineScope.launch { repository.completeTutorial(TutorialPath.FLOATING) }
    }
    LaunchedEffect(guidedSetup) {
        if (guidedSetup && !GuidedReaderSession.active) GuidedReaderSession.begin()
        else if (!guidedSetup) GuidedReaderSession.end()
    }
    val guidedReaderStep by GuidedReaderSession.step.collectAsStateWithLifecycle()
    fun returnToLesson() {
        if (backStack.lastOrNull() == Screen.Preferences) backStack.removeAt(backStack.lastIndex)
        GuidedReaderSession.returnFromPreferences()
        if (guidedSetup && backStack.lastOrNull() == Screen.Home) backStack.add(Screen.Tutorial(TutorialPath.FLOATING))
    }
    fun handleBack() {
        if (guidedSetup && backStack.lastOrNull() == Screen.Preferences) returnToLesson()
        else if (guidedSetup) exitGuide()
        else if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    LaunchedEffect(guidedReaderStep) {
        if (guidedSetup && guidedReaderStep == GuidedReaderStep.PREFERENCES) openHome()
    }
    BackHandler(enabled = guidedSetup) { handleBack() }
    if (guidedSetup && guidedReaderStep == GuidedReaderStep.RETURN &&
        backStack.lastOrNull() == Screen.Preferences) {
        AlertDialog(
            onDismissRequest = { returnToLesson() },
            title = { Text(stringResource(R.string.preferences_title)) },
            text = { Text(stringResource(R.string.guided_preferences_visit)) },
            confirmButton = { TextButton(onClick = { returnToLesson() }) {
                Text(stringResource(R.string.guided_return))
            } }
        )
    }
    LaunchedEffect(guidedSetup, preferences.isOverlayActive) {
        if (guidedSetup && preferences.isOverlayActive && backStack.lastOrNull() == Screen.Home &&
            GuidedReaderSession.step.value !in listOf(GuidedReaderStep.PREFERENCES, GuidedReaderStep.RETURN)) {
            lessonOwnsReader = true
            backStack.add(Screen.Tutorial(TutorialPath.FLOATING))
        } else if (guidedSetup && lessonOwnsReader && !preferences.isOverlayActive &&
            GuidedReaderSession.step.value != GuidedReaderStep.COMPLETE) exitGuide()
    }
    DisposableEffect(Unit) {
        onDispose { GuidedReaderSession.setSampleVisible(false) }
    }
    if (onboardingInitialized && preferences.offerGuidedSetup && !guidedSetup) {
        AlertDialog(
            onDismissRequest = { coroutineScope.launch { repository.dismissGuidedSetup() } },
            title = { Text(stringResource(R.string.guided_intro_title)) },
            text = { Text(stringResource(R.string.guided_intro_body)) },
            confirmButton = { TextButton(onClick = { beginGuide() }) { Text(stringResource(R.string.guided_start)) } },
            dismissButton = { TextButton(onClick = {
                coroutineScope.launch { repository.dismissGuidedSetup() }
            }) { Text(stringResource(R.string.guided_skip)) } }
        )
    }
    if (cameraIntroduction) {
        AlertDialog(
            onDismissRequest = { cameraIntroduction = false },
            title = { Text(stringResource(R.string.camera_title)) },
            text = { Text(stringResource(R.string.guided_camera_intro)) },
            confirmButton = { TextButton(onClick = {
                cameraIntroduction = false
                coroutineScope.launch { repository.dismissCameraGuide() }
                openCamera(guided = true)
            }) { Text(stringResource(R.string.guided_camera_begin)) } },
            dismissButton = { TextButton(onClick = {
                cameraIntroduction = false
                coroutineScope.launch { repository.dismissCameraGuide() }
                openCamera(guided = false)
            }) { Text(stringResource(R.string.guided_skip)) } }
        )
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
        onBack = { handleBack() },
        modifier = Modifier,
        entryProvider = entryProvider {
            entry<Screen.Home> {
                HomeScreen(
                    preferences = preferences,
                    onUpdateOverlayActive = handleToggleOverlay,
                    onNavigateToHowItWorks = { backStack.add(Screen.HowItWorks) },
                    onNavigateToCamera = {
                        if (!preferences.hasSeenCameraGuide) cameraIntroduction = true
                        else openCamera(guided = false)
                    },
                    guidedSetup = guidedSetup,
                    guidePreferences = guidedReaderStep == GuidedReaderStep.PREFERENCES,
                    onSkipGuidedSetup = { exitGuide() },
                    supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    overlayGranted = overlayGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    accessibilityConnected = accessibilityConnected,
                    onRequestOverlay = {
                        context.startActivity(KanakoOverlayService.createOverlayPermissionIntent(context))
                    },
                    onRequestAccessibility = { showDisclosure = true },
                    onNavigateToPreferences = {
                        if (guidedSetup) GuidedReaderSession.openPreferencesFromHome()
                        backStack.add(Screen.Preferences)
                    },
                    showTutorialInvitation = false,
                    onNavigateToTutorial = { beginGuide() },
                    onDismissTutorialInvitation = { coroutineScope.launch { repository.dismissTutorialInvitation() } }
                )
            }
            entry<Screen.SetupPermissions> {
                LaunchedEffect(Unit) { openHome() }
            }
            entry<Screen.HowItWorks> {
                HowItWorksScreen(
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    },
                    onNavigateToHome = {
                        openHome()
                    },
                    onNavigateToTutorial = { beginGuide() },
                    onNavigateToCameraTutorial = { cameraIntroduction = true }
                )
            }
            entry<Screen.Help> { route ->
                HowItWorksScreen(
                    initialTopic = route.topic,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onNavigateToHome = { openHome() },
                    onNavigateToTutorial = { beginGuide() },
                    onNavigateToCameraTutorial = { cameraIntroduction = true }
                )
            }
            entry<Screen.Tutorial> {
                if (!guidedSetup) LaunchedEffect(Unit) { openHome() }
                else TutorialScreen(
                    onBack = { exitGuide() },
                    onComplete = { exitGuide(completed = true) }
                )
            }
            entry<Screen.Camera> {
                CameraReaderScreen(
                    viewModel = cameraReader,
                    preferences = preferences,
                    guidedTutorial = cameraGuide,
                    onSkipTutorial = { cameraGuide = false },
                    onCompleteTutorial = {
                        if (cameraGuide) coroutineScope.launch { repository.completeTutorial(TutorialPath.CAMERA) }
                        cameraGuide = false
                    },
                    onPermissionDenied = {
                        coroutineScope.launch {
                            repository.recordCameraPermissionDenial()
                        }
                    },
                    onBack = { cameraGuide = false; if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onCaptured = { if (backStack.lastOrNull() == Screen.Camera) backStack.add(Screen.CameraResult) },
                    onNavigateToGallery = { backStack.add(Screen.Gallery) },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.CameraResult> {
                CameraResultScreen(
                    viewModel = cameraReader,
                    preferences = preferences,
                    onToggleScriptType = {
                        coroutineScope.launch {
                            repository.updateScriptType(if (preferences.scriptType == ScriptType.KANA)
                                ScriptType.ROMAJI else ScriptType.KANA)
                        }
                    },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.Gallery> {
                CameraGalleryScreen(
                    onBack = { handleBack() },
                    onOpen = { image -> backStack.add(Screen.SavedImage(image.uri.toString(), image.name)) }
                )
            }
            entry<Screen.SavedImage> { image ->
                SavedCameraImageScreen(Uri.parse(image.uri), image.name, onBack = { handleBack() })
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
                    onUpdateFabSize = { size ->
                        coroutineScope.launch { repository.updateFabSize(size) }
                    },
                    onUpdateAutoHideOnKeyboard = { enabled ->
                        coroutineScope.launch { repository.updateAutoHideOnKeyboard(enabled) }
                    },
                    onUpdateAutoReadAfterScroll = { enabled ->
                        coroutineScope.launch { repository.updateAutoReadAfterScroll(enabled) }
                    },
                    onRequestAccessibility = { openHome() },
                    onNavigateToPrivacy = { backStack.add(Screen.Help(HelpTopic.PRIVACY)) },
                    onBack = { handleBack() }
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

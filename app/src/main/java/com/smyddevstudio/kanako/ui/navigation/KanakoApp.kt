package com.smyddevstudio.kanako.ui.navigation

import com.smyddevstudio.kanako.service.GuidedReaderSession
import androidx.compose.runtime.CompositionLocalProvider
import com.smyddevstudio.kanako.ui.components.LocalTutorialFocus
import com.smyddevstudio.kanako.ui.components.TutorialFocus
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.smyddevstudio.kanako.ui.screens.isEmbeddedPhotoPickerAvailable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
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
import com.smyddevstudio.kanako.ui.screens.TutorialLayout
import com.smyddevstudio.kanako.ui.screens.tutorialPrompts
import com.smyddevstudio.kanako.ui.screens.tutorialCanAdvance
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
    var tutorialStep by rememberSaveable { mutableIntStateOf(0) }
    var finishedFloating by rememberSaveable { mutableStateOf(false) }
    var finishedCamera by rememberSaveable { mutableStateOf(false) }
    var tutorialPath by rememberSaveable {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) TutorialPath.FLOATING else TutorialPath.CAMERA)
    }
    var tutorialPrompt by rememberSaveable { mutableIntStateOf(0) }
    fun setTutorialPosition(step: Int, prompt: Int = 0) {
        tutorialStep = step
        tutorialPrompt = prompt
    }
    LaunchedEffect(guidedSetup, tutorialStep, tutorialPath) {
        if (guidedSetup) GuidedReaderSession.actions.collect { action ->
            val expected = when (tutorialStep) {
                1 -> if (tutorialPath == TutorialPath.FLOATING) listOf("readings", "selected", "dismissed")
                    else listOf("photo", "selected", "dismissed")
                // Script and read/clear practice wait for Next, allowing repeated changes.
                2 -> listOf("menu")
                else -> emptyList()
            }
            if (expected.getOrNull(tutorialPrompt) == action) {
                if (tutorialPrompt < tutorialPrompts(tutorialStep, tutorialPath).lastIndex) tutorialPrompt++
                else if (tutorialStep == 1) setTutorialPosition(2)
            }
        }
    }
    LaunchedEffect(repository) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        repository.initializeAutoReadOffer(packageInfo.firstInstallTime == packageInfo.lastUpdateTime)
        onboardingInitialized = true
    }
    val cameraReader: CameraReaderViewModel = viewModel()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && backStack.lastOrNull() == Screen.Camera) {
            cameraReader.openPhoto(context, uri)
            backStack.add(Screen.CameraResult)
            if (guidedSetup && tutorialPath == TutorialPath.CAMERA && tutorialStep in 1..2) setTutorialPosition(1, 1)
        }
    }
    fun launchPhotoPicker() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val hasCameraEntry = Screen.Camera in backStack
    val cameraForeground = backStack.lastOrNull() == Screen.CameraResult ||
        (backStack.lastOrNull() == Screen.Camera && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    val hostView = LocalView.current
    val cameraWindow = context.cameraActivity()?.window
    val lightTutorialNavigation = guidedSetup && MaterialTheme.colorScheme.surface.luminance() > 0.5f
    DisposableEffect(cameraForeground, cameraWindow, hostView, lightTutorialNavigation) {
        if (cameraForeground && cameraWindow != null) {
            val controller = WindowCompat.getInsetsController(cameraWindow, hostView)
            val previousLightStatus = controller.isAppearanceLightStatusBars
            val previousLightNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = lightTutorialNavigation
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatus
                controller.isAppearanceLightNavigationBars = previousLightNavigation
            }
        } else onDispose {}
    }
    LaunchedEffect(hasCameraEntry) {
        if (!hasCameraEntry) { cameraReader.returnToLive(); cameraReader.clearCaptureThumbnail() }
    }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayGranted by remember { mutableStateOf(KanakoOverlayService.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(KanakoAccessibilityService.isEnabled(context)) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
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
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
            if (guidedSetup && !readerActive) lessonOwnsReader = true
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
    fun openCamera() {
        val existing = backStack.indexOfLast { it == Screen.Camera || it == Screen.CameraResult }
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        } else backStack.add(Screen.Camera)
    }
    fun showTutorial() {
        val existing = backStack.indexOfLast { it is Screen.Tutorial }
        if (existing >= 0) {
            while (backStack.lastIndex > existing) backStack.removeAt(backStack.lastIndex)
        } else backStack.add(Screen.Tutorial())
    }
    fun beginGuide() {
        pendingActivation = false
        setTutorialPosition(0)
        finishedFloating = false
        finishedCamera = false
        tutorialPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) TutorialPath.FLOATING else TutorialPath.CAMERA
        lessonOwnsReader = false
        GuidedReaderSession.begin()
        guidedSetup = true
        openHome()
        coroutineScope.launch {
            repository.dismissGuidedSetup()
            repository.dismissTutorialInvitation()
        }
    }
    fun exitGuide(completed: Boolean = false) {
        pendingActivation = false
        showDisclosure = false
        if (lessonOwnsReader) KanakoOverlayService.stopService(context)
        lessonOwnsReader = false
        GuidedReaderSession.end()
        guidedSetup = false
        openHome()
        if (completed) coroutineScope.launch { repository.completeTutorial() }
    }
    fun practiceReader(path: TutorialPath, startImmediately: Boolean = false) {
        if ((path == TutorialPath.FLOATING && finishedFloating) ||
            (path == TutorialPath.CAMERA && finishedCamera)) return
        tutorialPath = path
        setTutorialPosition(-1)
        val floatingReady = overlayGranted && preferences.hasAcceptedSnapshotDisclosure &&
            accessibilityEnabled && accessibilityConnected
        if (path == TutorialPath.CAMERA && startImmediately) {
            setTutorialPosition(1)
            openCamera()
        } else if (path == TutorialPath.FLOATING && floatingReady && readerActive) {
            setTutorialPosition(1)
            showTutorial()
        } else {
            openHome()
            if (path == TutorialPath.FLOATING && floatingReady && startImmediately) handleToggleOverlay(true)
        }
    }
    fun finishRoute(completed: Boolean = true) {
        pendingActivation = false
        if (tutorialPath == TutorialPath.FLOATING) {
            if (completed) finishedFloating = true
            if (lessonOwnsReader) KanakoOverlayService.stopService(context)
            lessonOwnsReader = false
        } else if (completed) finishedCamera = true
        if (!completed) {
            setTutorialPosition(0)
            openHome()
        } else when {
            !finishedFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> practiceReader(TutorialPath.FLOATING)
            !finishedCamera -> practiceReader(TutorialPath.CAMERA)
            else -> { setTutorialPosition(5); openHome() }
        }
    }
    fun returnToTutorialCamera() {
        cameraReader.returnToLive()
        while (backStack.size > 1 && backStack.lastOrNull() != Screen.Camera) backStack.removeAt(backStack.lastIndex)
        if (backStack.lastOrNull() != Screen.Camera) backStack.add(Screen.Camera)
        setTutorialPosition(1)
    }
    fun openTutorialPreferences() {
        setTutorialPosition(3)
        backStack.add(Screen.Preferences)
    }
    fun previousTutorial() {
        pendingActivation = false
        if (tutorialStep == 1 && tutorialPath == TutorialPath.CAMERA &&
            backStack.lastOrNull() == Screen.CameraResult && tutorialPrompt <= 1) {
            returnToTutorialCamera()
        } else if (tutorialStep == 2 && tutorialPath == TutorialPath.CAMERA && backStack.lastOrNull() != Screen.CameraResult) {
            setTutorialPosition(1)
            openCamera()
        } else if (tutorialPrompt > 0) {
            tutorialPrompt--
        } else when (tutorialStep) {
            -1, 1 -> finishRoute(completed = false)
            2 -> setTutorialPosition(1, tutorialPrompts(1, tutorialPath).lastIndex)
            6 -> setTutorialPosition(2, tutorialPrompts(2, tutorialPath).lastIndex)
            3 -> { setTutorialPosition(5); openHome() }
            4 -> setTutorialPosition(3)
            5 -> {
                if (tutorialPath == TutorialPath.FLOATING) finishedFloating = false else finishedCamera = false
                practiceReader(tutorialPath)
            }
        }
    }
    fun handleBack() {
        if (guidedSetup) {
            if ((tutorialStep in -1..2 && tutorialStep != 0) || tutorialStep == 6) finishRoute(completed = false)
            else if (backStack.lastOrNull() == Screen.Home) exitGuide()
            else if (backStack.lastOrNull() == Screen.CameraResult || backStack.lastOrNull() == Screen.Gallery) {
                backStack.removeAt(backStack.lastIndex)
            } else { setTutorialPosition(5); openHome() }
        } else if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    LaunchedEffect(guidedSetup) {
        if (guidedSetup && !GuidedReaderSession.active) GuidedReaderSession.begin()
        else if (!guidedSetup) GuidedReaderSession.end()
    }
    LaunchedEffect(guidedSetup, finishedFloating, finishedCamera) {
        if (guidedSetup && tutorialStep == 0 && finishedCamera &&
            (finishedFloating || Build.VERSION.SDK_INT < Build.VERSION_CODES.R)) setTutorialPosition(5)
    }
    LaunchedEffect(guidedSetup, tutorialStep) {
        if (guidedSetup) {
            GuidedReaderSession.setHighlightPractice(tutorialStep in 1..2 || tutorialStep == 6)
            GuidedReaderSession.setAutomaticPractice(tutorialStep == 6 && tutorialPath == TutorialPath.FLOATING)
        }
    }
    LaunchedEffect(guidedSetup, tutorialStep, preferences.autoReadAfterScroll) {
        if (guidedSetup && tutorialStep == 6 && tutorialPrompt == 0 && preferences.autoReadAfterScroll) tutorialPrompt = 1
    }
    LaunchedEffect(guidedSetup, readerActive, tutorialStep, tutorialPath, overlayGranted,
        preferences.hasAcceptedSnapshotDisclosure, accessibilityEnabled, accessibilityConnected) {
        if (guidedSetup && readerActive && overlayGranted && preferences.hasAcceptedSnapshotDisclosure &&
            accessibilityEnabled && accessibilityConnected && tutorialPath == TutorialPath.FLOATING &&
            tutorialStep in -1..2 && tutorialStep != 0 && backStack.lastOrNull() == Screen.Home) {
            if (tutorialStep == -1) setTutorialPosition(1)
            showTutorial()
        }
    }
    BackHandler(enabled = guidedSetup) { handleBack() }
    DisposableEffect(Unit) {
        onDispose { GuidedReaderSession.end() }
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
    val targetKeys = when (tutorialStep) {
        0 -> buildSet {
            if (!finishedFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("floating_card")
            if (!finishedCamera) add("camera_card")
        }
        -1 -> when {
            tutorialPath == TutorialPath.CAMERA -> setOf("camera_card")
            !overlayGranted -> setOf("overlay_access")
            !preferences.hasAcceptedSnapshotDisclosure || !accessibilityEnabled || !accessibilityConnected -> setOf("screen_access")
            else -> setOf("start_reader")
        }
        1 -> if (tutorialPath == TutorialPath.FLOATING) setOf("sample", "sample_start")
            else setOf("camera_permission", "camera_capture", "camera_gallery", "photo_readings", "gallery_picker")
        2 -> if (tutorialPath == TutorialPath.FLOATING) setOf("sample", "sample_start")
            else setOf("camera_permission", "camera_capture", "camera_gallery", "photo_kanako", "photo_readings", "gallery_picker")
        6 -> setOf("sample", "sample_start")
        3 -> setOf("appearance_preview", "appearance_script", "appearance_size", "appearance_color", "appearance_plate")
        5 -> setOf("preferences")
        else -> setOf("tile_guide")
    }
    val setupActionLabel = if (accessibilityEnabled && preferences.hasAcceptedSnapshotDisclosure)
        R.string.setup_manage_accessibility else R.string.setup_open_accessibility
    val focus = remember(guidedSetup, backStack.lastOrNull()) {
        if (guidedSetup) TutorialFocus(tutorialStep, targetKeys, setupActionLabel) else null
    }
    focus?.let {
        it.step = tutorialStep
        it.allowed = targetKeys
        it.actionLabel = setupActionLabel
    }
    val canAdvance = tutorialCanAdvance(
        tutorialStep,
        tutorialPath,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && overlayGranted && preferences.hasAcceptedSnapshotDisclosure &&
            accessibilityEnabled && accessibilityConnected,
        cameraGranted || backStack.lastOrNull() == Screen.CameraResult || backStack.lastOrNull() == Screen.Gallery
    )
    CompositionLocalProvider(LocalTutorialFocus provides focus) {
    TutorialLayout(
        step = if (guidedSetup) tutorialStep else null,
        path = tutorialPath,
        prompt = tutorialPrompt,
        onPrevious = ::previousTutorial,
        canPrevious = tutorialStep != 0 || tutorialPrompt > 0,
        canNext = canAdvance,
        onNext = {
            if (canAdvance) {
                if (tutorialStep == 1 && tutorialPath == TutorialPath.CAMERA &&
                    backStack.lastOrNull() != Screen.CameraResult) {
                    finishRoute()
                } else if (tutorialPrompt < tutorialPrompts(tutorialStep, tutorialPath).lastIndex) tutorialPrompt++
                else when (tutorialStep) {
                    -1 -> {
                        setTutorialPosition(1)
                        if (tutorialPath == TutorialPath.CAMERA) openCamera() else showTutorial()
                    }
                    0 -> { setTutorialPosition(5); openHome() }
                    1 -> setTutorialPosition(2)
                    2 -> if (tutorialPath == TutorialPath.FLOATING) setTutorialPosition(6) else finishRoute()
                    6 -> finishRoute()
                    3 -> setTutorialPosition(4)
                    5 -> openTutorialPreferences()
                    else -> exitGuide(completed = true)
                }
            }
        },
        onSkip = { exitGuide() }
    ) { contentModifier ->
    NavDisplay(
        backStack = backStack,
        onBack = { handleBack() },
        modifier = contentModifier,
        entryProvider = entryProvider {
            entry<Screen.Home> {
                HomeScreen(
                    preferences = preferences,
                    tutorialActive = guidedSetup,
                    onSelectTutorialReader = { practiceReader(it) },
                    onStartTutorialReader = { practiceReader(it, startImmediately = true) },
                    onUpdateOverlayActive = handleToggleOverlay,
                    onNavigateToHowItWorks = { backStack.add(Screen.HowItWorks) },
                    onNavigateToCamera = { if (guidedSetup) setTutorialPosition(1); openCamera() },
                    supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    overlayGranted = overlayGranted,
                    accessibilityEnabled = accessibilityEnabled,
                    accessibilityConnected = accessibilityConnected,
                    onRequestOverlay = {
                        context.startActivity(KanakoOverlayService.createOverlayPermissionIntent(context))
                    },
                    onRequestAccessibility = {
                        if (preferences.hasAcceptedSnapshotDisclosure) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } else showDisclosure = true
                    },
                    onNavigateToPreferences = {
                        if (guidedSetup) openTutorialPreferences() else backStack.add(Screen.Preferences)
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
                    onNavigateToTutorial = { beginGuide() }
                )
            }
            entry<Screen.Help> { route ->
                HowItWorksScreen(
                    initialTopic = route.topic,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onNavigateToHome = { openHome() },
                    onNavigateToTutorial = { beginGuide() }
                )
            }
            entry<Screen.Tutorial> {
                if (!guidedSetup) LaunchedEffect(Unit) { openHome() }
                else if (tutorialStep !in 1..2 && tutorialStep != 6) LaunchedEffect(Unit) { openHome() }
                else TutorialScreen(
                    step = tutorialStep,
                    path = tutorialPath,
                    onChooseReader = { practiceReader(it) },
                    onPreferences = { backStack.add(Screen.Preferences) },
                    floatingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    floatingReaderActive = readerActive
                )
            }
            entry<Screen.Camera> {
                CameraReaderScreen(
                    viewModel = cameraReader,
                    onCameraPermissionChanged = { cameraGranted = it },
                    preferences = preferences,
                    onPermissionDenied = {
                        coroutineScope.launch {
                            repository.recordCameraPermissionDenial()
                        }
                    },
                    onBack = { handleBack() },
                    onCaptured = {
                        if (backStack.lastOrNull() == Screen.Camera) {
                            backStack.add(Screen.CameraResult)
                            if (guidedSetup && tutorialPath == TutorialPath.CAMERA && tutorialStep in 1..2) setTutorialPosition(1, 1)
                        }
                    },
                    onNavigateToGallery = {
                        if (backStack.lastOrNull() == Screen.Camera) {
                            if (isEmbeddedPhotoPickerAvailable()) backStack.add(Screen.Gallery)
                            else launchPhotoPicker()
                        }
                    },
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
                    onBack = { handleBack() },
                    onNavigateToPreferences = { backStack.add(Screen.Preferences) }
                )
            }
            entry<Screen.Gallery> {
                CameraGalleryScreen(
                    onBack = { handleBack() },
                    onUseSystemPicker = {
                        if (backStack.lastOrNull() == Screen.Gallery) {
                            backStack.removeAt(backStack.lastIndex)
                            if (backStack.lastOrNull() == Screen.Camera) launchPhotoPicker()
                        }
                    },
                    onOpen = { uri ->
                        if (backStack.lastOrNull() == Screen.Gallery) {
                            cameraReader.openPhoto(context, uri)
                            backStack.add(Screen.CameraResult)
                            if (guidedSetup && tutorialPath == TutorialPath.CAMERA && tutorialStep in 1..2) setTutorialPosition(1, 1)
                        }
                    }
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
                    onRequestAccessibility = { openHome() },
                    onNavigateToPrivacy = { backStack.add(Screen.Help(HelpTopic.PRIVACY)) },
                    onBack = { handleBack() }
                )
            }
        }
    )
    }
    }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
        )
    }
}

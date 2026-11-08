package com.smyddevstudio.kanako

import android.content.res.Configuration
import android.os.LocaleList
import android.graphics.drawable.AnimatedImageDrawable
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.ui.components.AutoReadDisclosure
import com.smyddevstudio.kanako.ui.screens.HomeScreen
import com.smyddevstudio.kanako.ui.screens.PreferencesScreen
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePreferencesTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun disconnectedServiceIsNotReportedAsMissingPermission() {
        val connected = mutableStateOf(false)
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {},
                    preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
                    overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = connected.value)
            }
        }
        compose.onNodeWithText(context.getString(R.string.home_setup_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.setup_accessibility_disconnected)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_accessibility_reconnect)).assertIsDisplayed()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).assertIsEnabled()
    }

    @Test fun enabledServiceWithMissingDisclosureShowsTheSeparateReviewStep() {
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {}, preferences = KanakoPreferences(),
                    overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true)
            }
        }
        compose.onNodeWithText(context.getString(R.string.home_setup_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.setup_disclosure_needed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).assertIsNotEnabled()
    }

    @Test fun homeControlsRemainAvailableDuringOptionalTutorialPractice() {
        var opens = 0
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, { opens++ },
                    preferences = KanakoPreferences(isOverlayActive = true, hasAcceptedSnapshotDisclosure = true),
                    overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true,
                    animationsEnabled = false, tutorialActive = true)
            }
        }
        compose.onNodeWithText(context.getString(R.string.camera_open)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.setup_stop)).assertIsEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.preferences_title)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun tutorialLaunchButtonsDoNotRepeatCardSelection() {
        val selected = mutableListOf<com.smyddevstudio.kanako.model.TutorialPath>()
        val started = mutableListOf<com.smyddevstudio.kanako.model.TutorialPath>()
        compose.setContent {
            KanakoTheme {
                CompositionLocalProvider(com.smyddevstudio.kanako.ui.components.LocalTutorialFocus provides
                    com.smyddevstudio.kanako.ui.components.TutorialFocus(0, setOf("floating_card", "camera_card"))) {
                    HomeScreen({}, {}, {},
                        preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
                        overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true,
                        supported = true, animationsEnabled = false, tutorialActive = true,
                        onSelectTutorialReader = { selected.add(it) }, onStartTutorialReader = { started.add(it) })
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(com.smyddevstudio.kanako.model.TutorialPath.CAMERA,
                com.smyddevstudio.kanako.model.TutorialPath.FLOATING), started)
            assertTrue(selected.isEmpty())
        }
    }

    @Test fun setupWaitsForRealAccessStateWithoutBlockingCamera() {
        val overlay = mutableStateOf(false)
        val connected = mutableStateOf(false)
        val consent = mutableStateOf(false)
        var overlayRequests = 0
        var accessibilityRequests = 0
        var starts = 0
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {},
                    preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = consent.value),
                    overlayGranted = overlay.value, accessibilityEnabled = true,
                    accessibilityConnected = connected.value,
                    onUpdateOverlayActive = { starts++ }, animationsEnabled = false,
                    onRequestOverlay = { overlayRequests++ },
                    onRequestAccessibility = { accessibilityRequests++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.camera_open)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.home_setup_action)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.setup_open_overlay)).performClick()
        compose.runOnIdle { assertEquals(1, overlayRequests); assertEquals(0, starts) }
        compose.runOnIdle { overlay.value = true }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.setup_open_accessibility)).performClick()
        compose.runOnIdle { assertEquals(1, accessibilityRequests); assertEquals(0, starts) }
        compose.runOnIdle { consent.value = true; connected.value = true }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().performClick()
        compose.waitUntil(3_000) { starts == 1 }
    }

    @Test fun homeRequiresConnectedAccessAndConsentButCameraIsIndependent() {
        val consent = mutableStateOf(false)
        val connected = mutableStateOf(false)
        val overlay = mutableStateOf(false)
        var cameraOpens = 0
        var starts = 0
        var overlayRequests = 0
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, { cameraOpens++ }, {},
                    preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = consent.value),
                    overlayGranted = overlay.value, accessibilityEnabled = true,
                    accessibilityConnected = connected.value,
                    onUpdateOverlayActive = { starts++ }, animationsEnabled = false,
                    onRequestOverlay = { overlayRequests++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, cameraOpens)
            assertEquals(0, starts)
            consent.value = true
        }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { overlay.value = true }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.setup_open_overlay)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.home_manage_access)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.setup_open_overlay)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, overlayRequests) }
    }

    @Test fun homeReportsEachPermissionIndependentlyAndWaitsForConnection() {
        val overlay = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        val consent = mutableStateOf(false)
        val connected = mutableStateOf(false)
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {},
                    preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = consent.value),
                    overlayGranted = overlay.value, accessibilityEnabled = enabled.value,
                    accessibilityConnected = connected.value)
            }
        }
        compose.onNodeWithText(context.getString(R.string.home_setup_action)).performScrollTo().performClick()
        fun assertStatus(title: Int, status: Int) {
            compose.onNode(hasText(context.getString(title)) and hasText(context.getString(status))).assertExists()
        }
        assertStatus(R.string.setup_overlay_title, R.string.setup_required)
        assertStatus(R.string.setup_accessibility_title, R.string.setup_required)
        compose.runOnIdle { overlay.value = true; enabled.value = true }
        assertStatus(R.string.setup_overlay_title, R.string.setup_granted)
        assertStatus(R.string.setup_accessibility_title, R.string.setup_required)
        compose.runOnIdle { consent.value = true }
        assertStatus(R.string.setup_accessibility_title, R.string.setup_allowed_connecting)
        compose.onNodeWithText(context.getString(R.string.setup_ready)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithText(context.getString(R.string.setup_ready)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.home_manage_access)).performScrollTo().performClick()
        assertStatus(R.string.setup_overlay_title, R.string.setup_granted)
        assertStatus(R.string.setup_accessibility_title, R.string.setup_granted)
        compose.runOnIdle { overlay.value = false }
        assertStatus(R.string.setup_overlay_title, R.string.setup_required)
        assertStatus(R.string.setup_accessibility_title, R.string.setup_granted)
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { enabled.value = false }
        assertStatus(R.string.setup_accessibility_title, R.string.setup_required)
    }

    @Test fun activeReaderCanAlwaysBeStoppedAfterAccessIsLost() {
        var stopped = false
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {}, preferences = KanakoPreferences(isOverlayActive = true),
                    supported = false, onUpdateOverlayActive = { stopped = !it })
            }
        }
        compose.onNodeWithText(context.getString(R.string.setup_stop)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    @Test fun readyHomeActionsAndHelpRemainReachableWithLargeText() {
        val active = mutableStateOf(false)
        var helpOpens = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                KanakoTheme {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        HomeScreen({ helpOpens++ }, {}, {},
                            preferences = KanakoPreferences(isOverlayActive = active.value, hasAcceptedSnapshotDisclosure = true),
                            overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true,
                            onUpdateOverlayActive = { active.value = it }, animationsEnabled = false)
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.setup_ready)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_manage_access)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.setup_stop)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(active.value) }
        compose.onNodeWithText(context.getString(R.string.how_it_works)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, helpOpens) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 28)
    @Test fun showReaderWaitsForDepartureAndStopRemainsImmediate() {
        val active = mutableStateOf(false)
        var starts = 0
        var stops = 0
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {},
                    preferences = KanakoPreferences(isOverlayActive = active.value, hasAcceptedSnapshotDisclosure = true),
                    overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true,
                    animationsEnabled = true,
                    onUpdateOverlayActive = {
                        if (it) starts++ else stops++
                        active.value = it
                    })
            }
        }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText(context.getString(R.string.setup_enable)).assertIsNotEnabled()
        compose.waitUntil(timeoutMillis = 6_000) { starts == 1 }
        assertTrue(imageViews().none { it.tag == R.drawable.kanako_floating })
        compose.onNodeWithText(context.getString(R.string.setup_stop)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, stops)
            assertFalse(active.value)
        }
        compose.waitUntil(timeoutMillis = 3_000) { imageViews().any { it.tag == R.drawable.kanako_floating } }
    }

    @Test fun cameraFlashDefersOneNavigationAndDisablesRepeatTaps() {
        var opens = 0
        var openedAt = 0L
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, { opens++; openedAt = android.os.SystemClock.uptimeMillis() }, {},
                    animationsEnabled = true)
            }
        }
        val camera = compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo()
        val pressedAt = android.os.SystemClock.uptimeMillis()
        camera.performClick()
        compose.runOnIdle { assertEquals(0, opens) }
        camera.assertIsNotEnabled()
        compose.waitUntil(timeoutMillis = 8_000) { opens == 1 }
        compose.runOnIdle {
            assertEquals(1, opens)
            assertTrue(openedAt - pressedAt >= 1_020L)
        }
        camera.assertIsEnabled()
    }

    @Test fun leavingTheForegroundCancelsPendingCameraNavigation() {
        var opens = 0
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.runOnUiThread {
            owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        compose.setContent {
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                KanakoTheme { HomeScreen({}, { opens++ }, {}, animationsEnabled = true) }
            }
        }
        compose.onNodeWithText(context.getString(R.string.camera_open)).performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            imageViews().lastOrNull()?.drawable is android.graphics.drawable.Animatable
        }
        compose.runOnIdle {
            owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
            owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        Thread.sleep(1_800L)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, opens) }
        compose.onNodeWithText(context.getString(R.string.camera_open)).assertIsEnabled()
    }

    private fun imageViews(): List<ImageView> {
        val result = mutableListOf<ImageView>()
        onView(isRoot()).check { root, _ ->
            fun collect(view: View) {
                if (view is ImageView) result += view
                if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
            }
            collect(root)
        }
        return result
    }

    @Test fun greetingActionHasOneLabelInTheNativeAccessibilityTree() {
        val motion = mutableStateOf(true)
        compose.setContent {
            KanakoTheme { HomeScreen({}, {}, {}, animationsEnabled = motion.value) }
        }
        val label = context.getString(R.string.replay_mascot_greeting)
        fun labelCount(node: AccessibilityNodeInfo?): Int {
            if (node == null) return 0
            val own = if (node.contentDescription?.toString() == label) 1 else 0
            return own + (0 until node.childCount).sumOf { labelCount(node.getChild(it)) }
        }
        fun labeledActions(node: AccessibilityNodeInfo?): Int {
            if (node == null) return 0
            val own = if (node.isClickable && labelCount(node) > 0) 1 else 0
            return own + (0 until node.childCount).sumOf { labeledActions(node.getChild(it)) }
        }
        for (enabled in listOf(true, false)) {
            compose.runOnIdle { motion.value = enabled }
            compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHasClickAction()
            compose.waitUntil(timeoutMillis = 5_000) {
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                labelCount(root) == 1 && labeledActions(root) == 1
            }
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 28)
    @Test fun homeButtonsMatchAndTappingTheGreetingReplaysIt() {
        compose.setContent {
            KanakoTheme {
                HomeScreen({}, {}, {},
                    preferences = KanakoPreferences(hasAcceptedSnapshotDisclosure = true),
                    overlayGranted = true, accessibilityEnabled = true, accessibilityConnected = true,
                    animationsEnabled = true)
            }
        }
        val reader = compose.onNode(hasText(context.getString(R.string.setup_enable)) and hasClickAction())
            .performScrollTo().fetchSemanticsNode().boundsInRoot
        val camera = compose.onNode(hasText(context.getString(R.string.camera_open)) and hasClickAction())
            .performScrollTo().fetchSemanticsNode().boundsInRoot
        assertEquals(camera.width, reader.width, 0.5f)
        assertEquals(camera.height, reader.height, 0.5f)
        val greeting = compose.onNodeWithContentDescription(context.getString(R.string.replay_mascot_greeting))
        greeting.performScrollTo()
        fun animation() = imageViews().mapNotNull { it.drawable as? AnimatedImageDrawable }.singleOrNull()
        compose.waitUntil(timeoutMillis = 5_000) { animation() != null }
        val original = requireNotNull(animation())
        compose.waitUntil(timeoutMillis = 6_000) { !original.isRunning }
        greeting.performClick()
        compose.waitUntil(timeoutMillis = 3_000) { animation()?.let { it !== original && it.isRunning } == true }
        compose.runOnIdle { assertFalse(original.isRunning) }
    }

    @Test fun preferencesMascotIsAStaticImage() {
        compose.setContent { KanakoTheme { PreferencesScreen(onBack = {}) } }
        compose.onNodeWithContentDescription(context.getString(R.string.preview_control)).assertIsDisplayed()
        assertTrue(imageViews().isEmpty())
    }

    @Test fun recommendedAutoReadRequiresExplicitAgreementAndCanBeDeclined() {
        val showing = mutableStateOf(true)
        var enabled = false
        compose.setContent {
            KanakoTheme {
                if (showing.value) AutoReadDisclosure(
                    onConfirm = { enabled = true; showing.value = false },
                    onDismiss = { showing.value = false })
            }
        }
        compose.runOnIdle { assertFalse(enabled) }
        compose.onNodeWithText(context.getString(R.string.snapshot_disclosure_cancel)).performClick()
        compose.runOnIdle { assertFalse(enabled); showing.value = true }
        compose.onNodeWithText(context.getString(R.string.auto_read_enable)).performClick()
        compose.runOnIdle { assertTrue(enabled) }
    }

    @Test fun japaneseHomeUsesOneLocalizedNameAndJapaneseCameraEntry() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ja"))
        }
        val localized = context.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
                KanakoTheme { HomeScreen({}, {}, {}) }
            }
        }
        compose.onNodeWithText("かなこ").assertIsDisplayed()
        compose.onNodeWithText("Kanako").assertDoesNotExist()
        compose.onNodeWithText(localized.getString(R.string.camera_open)).performScrollTo().assertIsDisplayed()
        assertTrue(localized.getString(R.string.snapshot_disclosure_body).contains("保存・アップロードはしません"))
        assertTrue(localized.getString(R.string.camera_state_permission_body).contains("メモリ"))
    }

    @Test fun japanesePreferencesKeepsPreviewPinnedAndUsesLocalizedScriptLabels() {
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ja"))
        })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
                KanakoTheme { PreferencesScreen(onBack = {}) }
            }
        }
        compose.onNodeWithText("設定").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(localized.getString(R.string.script_romaji)))
        compose.onNodeWithText("ローマ字").assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.preferences_preview)).assertIsDisplayed()
    }
}

package com.smyddevstudio.kanako

import android.content.res.Configuration
import android.os.LocaleList
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
                    onUpdateOverlayActive = { starts++ },
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
                            onUpdateOverlayActive = { active.value = it })
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
        assertTrue(localized.getString(R.string.snapshot_disclosure_body).contains("保存・アップロードせず"))
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

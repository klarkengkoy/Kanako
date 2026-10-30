package com.smyddevstudio.kanako

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smyddevstudio.kanako.ui.navigation.HelpTopic
import com.smyddevstudio.kanako.ui.screens.HowItWorksScreen
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun privacyEntryOpensTheRelevantTopicAndKeepsBackSeparateFromHome() {
        var backs = 0
        var homes = 0
        compose.setContent {
            KanakoTheme {
                HowItWorksScreen(onBack = { backs++ }, onNavigateToHome = { homes++ },
                    initialTopic = HelpTopic.PRIVACY)
            }
        }
        compose.onNodeWithText(context.getString(R.string.preferences_privacy)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.how_privacy_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.how_start_title)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(0, homes) }
        compose.onNodeWithText(context.getString(R.string.home_return)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, homes) }
    }

    @Test fun cameraTopicShowsCameraReferenceWithoutFloatingSetupInstructions() {
        compose.setContent { KanakoTheme { HowItWorksScreen(onBack = {}) } }
        compose.onNodeWithText(context.getString(R.string.camera_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.camera_title)).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.how_camera)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.how_start)).assertDoesNotExist()
    }
}

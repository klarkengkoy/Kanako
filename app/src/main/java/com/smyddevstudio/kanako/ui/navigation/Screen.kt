package com.smyddevstudio.kanako.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object HowItWorks : Screen

    @Serializable
    data class Help(val topic: HelpTopic) : Screen

    @Serializable
    data object Camera : Screen

    @Serializable
    data object CameraResult : Screen

    @Deprecated("Restored legacy stacks return to Home")
    @Serializable
    data object SetupPermissions : Screen

    @Serializable
    data object Preferences : Screen
}

@Serializable
enum class HelpTopic { FLOATING_READER, CAMERA, APPEARANCE, PRIVACY, TROUBLESHOOTING }

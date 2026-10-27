package com.smyddevstudio.kanako.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Screen : NavKey {
    @Serializable
    data object Home : Screen

    @Serializable
    data object HowItWorks : Screen

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

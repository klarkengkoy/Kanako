package com.smyddevstudio.kanako.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

internal object KanakoUiProfile {
    val homePadding = 24.dp
    val homeCardInset = 24.dp
    val homeCardGap = 24.dp
    val homeActionHeight = 64.dp
    val homeActionHorizontalPadding = 24.dp
    val homeActionVerticalPadding = 20.dp
    val homeHeroTextMinWidth = 192.dp
    const val homeHeroMaxScale = 2f
    const val homeHeroSourcePixels = 256f
    const val cameraFlashDelayMillis = 1_020L
    const val cameraFlashDuration = 320
    const val cameraFlashGrowFraction = 0.65f
    const val cameraFlashPeakAlpha = 0.85f
    val groupRadius = 24.dp
    val groupInset = 16.dp
    val groupShape = RoundedCornerShape(groupRadius)
    val choiceShape = RoundedCornerShape(groupRadius - groupInset)
    const val pressScale = 0.96f
    const val quietPressScale = 0.98f
    const val pressDuration = 90
    const val releaseDamping = 0.72f
    const val releaseStiffness = 500f
    const val expandDuration = 320
    const val contentDuration = 220
    val pressedDepth = 1.dp
    val contactRadius = 3.dp
    val contactOffset = 1.dp
    val castRadius = 18.dp
    val castOffset = 6.dp
    val quietCastRadius = 10.dp
    val quietCastOffset = 3.dp
    val rimWidth = 1.dp
    const val rimAlpha = 0.14f
    const val darkRimAlpha = 0.08f
    const val lightTone = 0.035f
    const val darkTone = 0.025f
    const val contactAlpha = 0.12f
    const val darkContactAlpha = 0.20f
    const val castAlpha = 0.10f
    const val darkCastAlpha = 0.18f
    const val quietDepth = 0.65f
    const val pressedCastStrength = 0.70f

    fun <T> releaseSpec() = spring<T>(
        dampingRatio = releaseDamping,
        stiffness = releaseStiffness
    )
}

internal val LocalKanakoMotionEnabled = staticCompositionLocalOf { true }
internal val LocalKanakoDarkTheme = staticCompositionLocalOf { false }

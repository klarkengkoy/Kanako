package com.smyddevstudio.kanako.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile
import kotlin.math.hypot

@Composable
internal fun CameraLaunchFlash(
    origin: Offset,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val center = Offset(origin.x.coerceIn(0f, size.width), origin.y.coerceIn(0f, size.height))
        val radius = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
        val fraction = progress().coerceIn(0f, 1f)
        val grow = KanakoUiProfile.cameraFlashGrowFraction
        val expansion = FastOutSlowInEasing.transform((fraction / grow).coerceIn(0f, 1f))
        val fade = ((1f - fraction) / (1f - grow)).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = KanakoUiProfile.cameraFlashPeakAlpha * fade),
            radius = radius * expansion,
            center = center
        )
    }
}

@Preview(name = "Camera launch flash")
@Composable
private fun CameraLaunchFlashPreview() {
    CameraLaunchFlash(Offset(180f, 80f), { 0.5f }, Modifier.size(240.dp))
}

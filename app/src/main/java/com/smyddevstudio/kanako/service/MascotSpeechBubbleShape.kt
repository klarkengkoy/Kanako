package com.smyddevstudio.kanako.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal class MascotSpeechBubbleShape(
    private val aboveMascot: Boolean,
    private val tipFraction: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { 8.dp.toPx() }.coerceAtMost(size.height / 3)
        val radius = with(density) { 16.dp.toPx() }.coerceAtMost(size.width / 4)
        val top = if (aboveMascot) 0f else tail
        val bottom = if (aboveMascot) size.height - tail else size.height
        val x = (size.width * tipFraction).coerceIn(radius, size.width - radius)
        val base = if (aboveMascot) bottom - 1f else top + 1f
        val tip = if (aboveMascot) size.height else 0f
        val body = Path().apply {
            addRoundRect(RoundRect(Rect(0f, top, size.width, bottom), CornerRadius(radius)))
        }
        val pointer = Path().apply {
            moveTo(x - tail, base)
            lineTo(x, tip)
            lineTo(x + tail, base)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, pointer))
    }
}

@Preview(widthDp = 240, heightDp = 220, showBackground = true)
@Composable
private fun MascotSpeechBubblePreview() {
    KanakoTheme {
        Surface(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            shape = MascotSpeechBubbleShape(false, 0.25f),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.controls_title))
                Text(stringResource(R.string.script_kana))
                Text(stringResource(R.string.controls_stop))
            }
        }
    }
}

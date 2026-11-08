package com.smyddevstudio.kanako.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile
import com.smyddevstudio.kanako.ui.theme.LocalKanakoDarkTheme
import com.smyddevstudio.kanako.ui.theme.LocalKanakoMotionEnabled

@Composable
internal fun rememberKanakoPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalKanakoMotionEnabled.current
    return animateFloatAsState(
        targetValue = if (pressed && enabled && motion) 1f else 0f,
        animationSpec = when {
            !motion -> snap()
            pressed && enabled -> tween(KanakoUiProfile.pressDuration)
            else -> KanakoUiProfile.releaseSpec()
        },
        label = "Control compression"
    )
}

internal fun Modifier.kanakoPressResponse(
    pressure: State<Float>,
    focal: Boolean = false
): Modifier = drawWithContent {
    val compression = pressure.value
    val targetScale = if (focal) KanakoUiProfile.pressScale else KanakoUiProfile.quietPressScale
    scale(1f - ((1f - targetScale) * compression)) {
        translate(top = KanakoUiProfile.pressedDepth.toPx() * compression) {
            this@drawWithContent.drawContent()
        }
    }
}

@Composable
internal fun Modifier.kanakoObject(
    shape: Shape,
    color: Color,
    focal: Boolean = false,
    enabled: Boolean = true,
    pressure: State<Float>? = null
): Modifier {
    val dark = LocalKanakoDarkTheme.current
    val depth = if (focal) 1f else KanakoUiProfile.quietDepth
    val castAlpha = if (dark) KanakoUiProfile.darkCastAlpha else KanakoUiProfile.castAlpha
    val contactAlpha = if (dark) KanakoUiProfile.darkContactAlpha else KanakoUiProfile.contactAlpha
    val rimAlpha = if (dark) KanakoUiProfile.darkRimAlpha else KanakoUiProfile.rimAlpha
    val raised = if (enabled) {
        this.dropShadow(shape) {
            radius = (if (focal) KanakoUiProfile.castRadius else KanakoUiProfile.quietCastRadius).toPx()
            offset = Offset(
                x = KanakoUiProfile.contactOffset.toPx(),
                y = (if (focal) KanakoUiProfile.castOffset else KanakoUiProfile.quietCastOffset).toPx()
            )
            this.color = Color.Black
            alpha = castAlpha * depth * (1f -
                (1f - KanakoUiProfile.pressedCastStrength) * (pressure?.value ?: 0f).coerceIn(0f, 1f))
        }.dropShadow(shape) {
            radius = KanakoUiProfile.contactRadius.toPx()
            offset = Offset(KanakoUiProfile.contactOffset.toPx(), KanakoUiProfile.contactOffset.toPx())
            this.color = Color.Black
            alpha = contactAlpha * depth
        }
    } else this
    return raised.clip(shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val body = Brush.verticalGradient(
            listOf(
                lerp(color, Color.White, KanakoUiProfile.lightTone),
                color,
                lerp(color, Color.Black, KanakoUiProfile.darkTone)
            )
        )
        val rim = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = rimAlpha), Color.Transparent, Color.Transparent),
            end = Offset(size.width, size.height)
        )
        onDrawBehind {
            if (enabled) {
                drawOutline(outline, brush = body)
                drawOutline(outline, brush = rim, style = Stroke(KanakoUiProfile.rimWidth.toPx()))
            } else drawOutline(outline, color = color)
        }
    }
}

@Composable
internal fun KanakoCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape: Shape = KanakoUiProfile.groupShape,
    focal: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val pressure = interactionSource?.let { rememberKanakoPress(it, enabled = true) }
    Card(
        modifier = modifier
            .then(if (pressure != null) Modifier.kanakoPressResponse(pressure) else Modifier)
            .kanakoObject(shape, colors.containerColor, focal = focal, pressure = pressure),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = colors.contentColor
        ),
        content = content
    )
}

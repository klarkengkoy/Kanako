package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Composable
internal fun KanakoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressure = rememberKanakoPress(interaction, enabled)
    val shape = ButtonDefaults.shape
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.kanakoPressResponse(pressure, focal = true).kanakoObject(
            shape = shape,
            color = if (enabled) colors.containerColor else colors.disabledContainerColor,
            focal = true,
            enabled = enabled,
            pressure = pressure
        ),
        shape = shape,
        colors = colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        elevation = null,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun KanakoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressure = rememberKanakoPress(interaction, enabled)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.kanakoPressResponse(pressure),
        enabled = enabled,
        shape = shape,
        interactionSource = interaction,
        content = content
    )
}

@Composable
internal fun KanakoTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressure = rememberKanakoPress(interaction, enabled)
    TextButton(
        onClick = onClick,
        modifier = modifier.kanakoPressResponse(pressure),
        enabled = enabled,
        interactionSource = interaction,
        content = content
    )
}

@Composable
internal fun KanakoIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressure = rememberKanakoPress(interaction, enabled)
    IconButton(
        onClick = onClick,
        modifier = modifier.kanakoPressResponse(pressure),
        enabled = enabled,
        interactionSource = interaction,
        content = content
    )
}

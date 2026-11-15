package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile

@Composable
internal fun KanakoDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    maxWidth: Dp = KanakoUiProfile.dialogMaxWidth,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        KanakoCard(
            modifier = modifier.padding(KanakoUiProfile.dialogOuterPadding)
                .widthIn(max = maxWidth).fillMaxWidth()
                .heightIn(max = KanakoUiProfile.dialogMaxHeight)
                .then(if (fillHeight) Modifier.fillMaxHeight(0.9f) else Modifier),
            content = content
        )
    }
}

@Preview
@Composable
private fun KanakoDialogPreview() {
    KanakoTheme {
        KanakoDialog(onDismissRequest = {}) {
            Text("Kanako", Modifier.padding(KanakoUiProfile.dialogContentPadding))
        }
    }
}

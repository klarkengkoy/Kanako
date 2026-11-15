package com.smyddevstudio.kanako.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.components.KanakoDialog
import com.smyddevstudio.kanako.ui.components.KanakoOutlinedButton
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile

private data class PolicySection(val title: String?, val paragraphs: List<String>)

// The bundled policy uses h2 sections and p paragraphs; inline links stay with their paragraph.
private fun policySections(html: String): List<PolicySection> = buildList {
    var title: String? = null
    var paragraphs = mutableListOf<String>()
    val blocks = Regex("""<(h2|p)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    for (block in blocks.findAll(html.substringAfter("<body>").substringBefore("</body>"))) {
        if (block.groupValues[1] == "h2") {
            if (paragraphs.isNotEmpty()) add(PolicySection(title, paragraphs.toList()))
            title = block.groupValues[2].trim()
            paragraphs = mutableListOf()
        } else paragraphs.add(block.groupValues[2].trim())
    }
    if (paragraphs.isNotEmpty()) add(PolicySection(title, paragraphs.toList()))
}

@Composable
internal fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    val sections = remember(resources) {
        resources.openRawResource(R.raw.privacy_policy).bufferedReader(Charsets.UTF_8).use {
            policySections(it.readText())
        }
    }
    KanakoDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        maxWidth = KanakoUiProfile.policyMaxWidth,
        fillHeight = true
    ) {
        Column(
            Modifier.padding(KanakoUiProfile.dialogContentPadding),
            verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogSectionGap)
        ) {
            Text(
                stringResource(R.string.account_privacy),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )
            SelectionContainer(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogSectionGap)) {
                    sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogHeadingGap)) {
                            section.title?.let { title ->
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics { heading() }
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(KanakoUiProfile.dialogParagraphGap)) {
                                section.paragraphs.forEachIndexed { index, paragraph ->
                                    PolicyParagraph(
                                        html = paragraph,
                                        metadata = section.title == null && index == 0
                                    )
                                }
                            }
                        }
                    }
                }
            }
            KanakoOutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = KanakoUiProfile.dialogActionHeight)
            ) {
                Text(stringResource(R.string.account_close), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PolicyParagraph(
    html: String,
    modifier: Modifier = Modifier,
    metadata: Boolean = false
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val paragraph = remember(html, linkColor) {
        AnnotatedString.fromHtml(
            html,
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            )
        )
    }
    Text(
        paragraph,
        modifier = modifier,
        style = if (metadata) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
        color = if (metadata) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    )
}

@Preview(name = "Privacy")
@Preview(name = "Privacy dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Privacy compact large text", widthDp = 320, heightDp = 480, fontScale = 1.5f)
@Composable
private fun PrivacyPolicyDialogPreview() {
    KanakoTheme { PrivacyPolicyDialog(onDismiss = {}) }
}

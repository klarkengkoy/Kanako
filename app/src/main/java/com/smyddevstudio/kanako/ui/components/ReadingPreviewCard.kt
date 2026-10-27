package com.smyddevstudio.kanako.ui.components

import android.text.Annotation
import android.text.Spanned
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.data.tokenizer.JapaneseTextTokenizer
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabSize
import com.smyddevstudio.kanako.model.KanakoPreferences
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.ui.theme.CreamBackgroundDark

@Composable
internal fun ReadingSample(
    preferences: KanakoPreferences,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val text = LocalResources.current.getText(
        if (compact) R.string.preview_sample_short else R.string.preview_sample_text
    )
    val segments = remember(text, preferences.scriptType) {
        readingSampleSegments(text, preferences.scriptType)
    }
    val background = when (preferences.annotationPlateStyle) {
        AnnotationPlateStyle.CLEAR -> androidx.compose.ui.graphics.Color.Transparent
        AnnotationPlateStyle.WASHI_CAPSULE -> CreamBackgroundDark
        AnnotationPlateStyle.HIGH_CONTRAST -> MaterialTheme.colorScheme.inverseSurface
        AnnotationPlateStyle.SUBTLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    FuriganaText(
        segments = segments,
        furiganaSize = preferences.furiganaSize,
        furiganaColorEnum = preferences.furiganaColor,
        kanjiColor = MaterialTheme.colorScheme.onSurface,
        readingPlateColor = background,
        modifier = modifier.padding(12.dp)
    )
}

/** Uses the fixed sample's resource annotations without loading the OCR tokenizer dictionary. */
internal fun readingSampleSegments(text: CharSequence, scriptType: ScriptType): List<FuriganaSegment> {
    val annotated = text as? Spanned ?: return listOf(FuriganaSegment(text.toString()))
    val readings = annotated.getSpans(0, text.length, Annotation::class.java)
        .filter { it.key == "reading" }
        .sortedBy { annotated.getSpanStart(it) }
    val formatter = JapaneseTextTokenizer()
    return buildList {
        var offset = 0
        for (reading in readings) {
            val start = annotated.getSpanStart(reading)
            val end = annotated.getSpanEnd(reading)
            if (start > offset) add(FuriganaSegment(text.subSequence(offset, start).toString()))
            add(
                FuriganaSegment(
                    text = text.subSequence(start, end).toString(),
                    reading = formatter.formatReading(reading.value, scriptType)
                )
            )
            offset = end
        }
        if (offset < text.length) add(FuriganaSegment(text.subSequence(offset, text.length).toString()))
    }
}

@Composable
internal fun ReadingPreviewCard(
    preferences: KanakoPreferences,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.preferences_preview),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingSample(preferences, Modifier.weight(1f), compact = compact)
                val controlSize = when (preferences.fabSize) {
                    FabSize.COMPACT -> 48.dp
                    FabSize.MEDIUM -> 56.dp
                    FabSize.LARGE -> 68.dp
                }
                Image(
                    painterResource(R.drawable.kanako_floating),
                    contentDescription = stringResource(R.string.preview_control),
                    modifier = Modifier.size(controlSize)
                )
            }
        }
    }
}

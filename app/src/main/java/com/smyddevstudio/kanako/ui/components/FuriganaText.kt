package com.smyddevstudio.kanako.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.ui.theme.readableReadingColor
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.PrimaryCrimson
import com.smyddevstudio.kanako.ui.theme.PrimaryCrimsonLight

data class FuriganaSegment(
    val text: String,
    val reading: String? = null,
    val isHighlighted: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuriganaText(
    segments: List<FuriganaSegment>,
    modifier: Modifier = Modifier,
    kanjiFontSize: TextUnit = 20.sp,
    kanjiColor: Color = MaterialTheme.colorScheme.onBackground,
    kanjiFontWeight: FontWeight = FontWeight.Normal,
    furiganaFontSize: TextUnit = 12.sp,
    furiganaColor: Color = PrimaryCrimson,
    highlightColor: Color = PrimaryCrimsonLight,
    horizontalSpacing: Dp = 1.dp,
    verticalSpacing: Dp = 6.dp,
    showFurigana: Boolean = true,
    readingPlateColor: Color = Color.Transparent
) {
    // Split plain text segments (reading == null) with length > 1 into single-character segments
    // so they wrap smoothly at character boundaries in FlowRow.
    val displaySegments = segments.flatMap { segment ->
        if (segment.reading.isNullOrEmpty() && segment.text.length > 1) {
            segment.text.map { char ->
                FuriganaSegment(
                    text = char.toString(),
                    reading = null,
                    isHighlighted = segment.isHighlighted
                )
            }
        } else {
            listOf(segment)
        }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        itemVerticalAlignment = Alignment.Bottom
    ) {
        displaySegments.forEach { segment ->
            FuriganaSegmentItem(
                segment = segment,
                kanjiFontSize = kanjiFontSize,
                kanjiColor = kanjiColor,
                kanjiFontWeight = kanjiFontWeight,
                furiganaFontSize = furiganaFontSize,
                furiganaColor = furiganaColor,
                highlightColor = highlightColor,
                showFurigana = showFurigana,
                readingPlateColor = readingPlateColor
            )
        }
    }
}

@Composable
fun FuriganaText(
    segments: List<FuriganaSegment>,
    furiganaSize: FuriganaSize,
    furiganaColorEnum: FuriganaColor,
    modifier: Modifier = Modifier,
    kanjiFontSize: TextUnit = 20.sp,
    kanjiColor: Color = MaterialTheme.colorScheme.onBackground,
    kanjiFontWeight: FontWeight = FontWeight.Normal,
    highlightColor: Color = PrimaryCrimsonLight,
    showFurigana: Boolean = true,
    readingPlateColor: Color = Color.Transparent
) {
    FuriganaText(
        segments = segments,
        modifier = modifier,
        kanjiFontSize = kanjiFontSize,
        kanjiColor = kanjiColor,
        kanjiFontWeight = kanjiFontWeight,
        furiganaFontSize = furiganaSize.sizeSp.sp,
        furiganaColor = Color(furiganaColorEnum.colorHex),
        highlightColor = highlightColor,
        showFurigana = showFurigana,
        readingPlateColor = readingPlateColor
    )
}

@Composable
fun FuriganaSegmentItem(
    segment: FuriganaSegment,
    kanjiFontSize: TextUnit,
    kanjiColor: Color,
    kanjiFontWeight: FontWeight,
    furiganaFontSize: TextUnit,
    furiganaColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    showFurigana: Boolean = true,
    readingPlateColor: Color = Color.Transparent
) {
    val itemModifier = if (segment.isHighlighted) {
        modifier
            .background(highlightColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp)
    } else {
        modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = itemModifier
    ) {
        if (showFurigana && !segment.reading.isNullOrEmpty()) {
            Text(
                text = segment.reading,
                fontSize = furiganaFontSize,
                modifier = Modifier.background(readingPlateColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = if (readingPlateColor == Color.Transparent) 0.dp else 4.dp),
                color = readableReadingColor(
                    furiganaColor,
                    if (readingPlateColor != Color.Transparent) readingPlateColor
                    else if (segment.isHighlighted) highlightColor else MaterialTheme.colorScheme.background
                ),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = segment.text,
            fontSize = kanjiFontSize,
            color = kanjiColor,
            fontWeight = kanjiFontWeight,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FuriganaTextPreviewSmall() {
    KanakoTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            FuriganaText(
                segments = listOf(
                    FuriganaSegment("私", "わたし"),
                    FuriganaSegment("は"),
                    FuriganaSegment("日本語", "にほんご", isHighlighted = true),
                    FuriganaSegment("を"),
                    FuriganaSegment("勉強", "べんきょう"),
                    FuriganaSegment("しています。")
                ),
                furiganaFontSize = 10.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FuriganaTextPreviewMedium() {
    KanakoTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            FuriganaText(
                segments = listOf(
                    FuriganaSegment("漢字", "かんじ"),
                    FuriganaSegment("の"),
                    FuriganaSegment("読み方", "よみかた"),
                    FuriganaSegment("を"),
                    FuriganaSegment("表示", "ひょうじ", isHighlighted = true),
                    FuriganaSegment("します。")
                ),
                furiganaFontSize = 12.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FuriganaTextPreviewLarge() {
    KanakoTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            FuriganaText(
                segments = listOf(
                    FuriganaSegment("猫", "ねこ"),
                    FuriganaSegment("と"),
                    FuriganaSegment("犬", "いぬ"),
                    FuriganaSegment("が"),
                    FuriganaSegment("公園", "こうえん"),
                    FuriganaSegment("で"),
                    FuriganaSegment("遊", "あそ"),
                    FuriganaSegment("んでいます。")
                ),
                furiganaFontSize = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FuriganaTextPreviewParagraph() {
    KanakoTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            FuriganaText(
                segments = listOf(
                    FuriganaSegment("昔", "むかし"),
                    FuriganaSegment("々", "むかし"),
                    FuriganaSegment("、ある"),
                    FuriganaSegment("所", "ところ"),
                    FuriganaSegment("に"),
                    FuriganaSegment("おじいさんと"),
                    FuriganaSegment("おばあさんが"),
                    FuriganaSegment("住", "す"),
                    FuriganaSegment("んでいました。おじいさんは"),
                    FuriganaSegment("山", "やま"),
                    FuriganaSegment("へ"),
                    FuriganaSegment("芝刈", "しばか"),
                    FuriganaSegment("りに、おばあさんは"),
                    FuriganaSegment("川", "かわ"),
                    FuriganaSegment("へ"),
                    FuriganaSegment("洗濯", "せんたく", isHighlighted = true),
                    FuriganaSegment("に"),
                    FuriganaSegment("行", "い"),
                    FuriganaSegment("きました。")
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

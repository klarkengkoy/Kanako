package com.smyddevstudio.kanako.ui.components

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.ui.theme.KanakoTheme
import com.smyddevstudio.kanako.ui.theme.KanakoUiProfile
import java.util.Locale

private enum class InterfaceLanguage(
    val tag: String,
    val nativeName: String,
    @param:DrawableRes val flag: Int
) {
    ENGLISH("en", "English", R.drawable.flag_language_en),
    JAPANESE("ja", "日本語", R.drawable.flag_language_ja),
    FRENCH("fr", "Français", R.drawable.flag_language_fr),
    ITALIAN("it", "Italiano", R.drawable.flag_language_it),
    NEPALI("ne", "नेपाली", R.drawable.flag_language_ne),
    FILIPINO("fil", "Filipino", R.drawable.flag_language_fil);

    companion object {
        fun fromTag(tag: String): InterfaceLanguage? {
            val language = Locale.forLanguageTag(tag).language.let { if (it == "tl") "fil" else it }
            return entries.firstOrNull { it.tag == language }
        }
    }
}

@Composable
internal fun HomeLanguagePicker(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val selectedTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
    val displayLanguage = LocaleListCompat.wrap(configuration.locales).interfaceLanguage()
    val deviceLanguage = LocaleManagerCompat.getSystemLocales(LocalContext.current).interfaceLanguage()
    LanguagePickerContent(
        selectedLanguageTag = selectedTag,
        displayLanguageTag = displayLanguage.tag,
        onLanguageSelected = { tag ->
            val locales = LocaleListCompat.forLanguageTags(tag.orEmpty())
            if (locales != AppCompatDelegate.getApplicationLocales()) {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        },
        modifier = modifier,
        enabled = enabled,
        deviceLanguageTag = deviceLanguage.tag
    )
}

private fun LocaleListCompat.interfaceLanguage(): InterfaceLanguage =
    (0 until size()).firstNotNullOfOrNull { index ->
        get(index)?.toLanguageTag()?.let(InterfaceLanguage::fromTag)
    } ?: InterfaceLanguage.ENGLISH

@Composable
internal fun LanguagePickerContent(
    selectedLanguageTag: String?,
    displayLanguageTag: String,
    onLanguageSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deviceLanguageTag: String = displayLanguageTag
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLanguage = InterfaceLanguage.fromTag(displayLanguageTag) ?: InterfaceLanguage.ENGLISH
    val selectedLanguage = selectedLanguageTag?.let(InterfaceLanguage::fromTag)
    val description = stringResource(R.string.language_button, displayLanguage.nativeName)
    val menuState = stringResource(
        if (expanded) R.string.language_menu_expanded else R.string.language_menu_collapsed
    )
    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }
    Box(modifier) {
        KanakoIconButton(
            onClick = { expanded = !expanded },
            enabled = enabled,
            modifier = Modifier.size(48.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .semantics {
                    contentDescription = description
                    stateDescription = menuState
                }
        ) {
            Box(Modifier.size(32.dp)) {
                LanguageFlag(displayLanguage.flag, Modifier.align(Alignment.Center))
                Icon(
                    painter = painterResource(R.drawable.ic_language_dropdown),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(12.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 224.dp, max = 320.dp),
            shape = KanakoUiProfile.groupShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            LanguageMenuItem(
                label = stringResource(R.string.language_follow_device),
                flag = (InterfaceLanguage.fromTag(deviceLanguageTag) ?: InterfaceLanguage.ENGLISH).flag,
                isSelected = selectedLanguageTag == null,
                onClick = {
                    expanded = false
                    onLanguageSelected(null)
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            InterfaceLanguage.entries.forEach { language ->
                LanguageMenuItem(
                    label = language.nativeName,
                    flag = language.flag,
                    isSelected = selectedLanguage == language,
                    onClick = {
                        expanded = false
                        onLanguageSelected(language.tag)
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageMenuItem(
    label: String,
    @DrawableRes flag: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick,
        modifier = modifier.padding(horizontal = 8.dp)
            .clip(KanakoUiProfile.choiceShape)
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .heightIn(min = 48.dp)
            .semantics { selected = isSelected },
        leadingIcon = { LanguageFlag(flag) },
        trailingIcon = {
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp)
    )
}

@Composable
private fun LanguageFlag(
    @DrawableRes flag: Int,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(flag),
        contentDescription = null,
        modifier = modifier.size(28.dp).clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeLanguagePickerPreview() {
    KanakoTheme {
        Surface {
            LanguagePickerContent("ja", "ja", {}, Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark menu", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text menu", showBackground = true, fontScale = 2f)
@Composable
private fun LanguageMenuPreview() {
    KanakoTheme {
        Surface(shape = KanakoUiProfile.groupShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.widthIn(max = 320.dp).padding(vertical = 8.dp)) {
                InterfaceLanguage.entries.forEach { language ->
                    LanguageMenuItem(language.nativeName, language.flag, language == InterfaceLanguage.JAPANESE, {})
                }
            }
        }
    }
}

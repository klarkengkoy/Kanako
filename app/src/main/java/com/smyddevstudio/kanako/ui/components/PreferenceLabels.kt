package com.smyddevstudio.kanako.ui.components

import androidx.annotation.StringRes
import com.smyddevstudio.kanako.R
import com.smyddevstudio.kanako.model.FuriganaSize
import com.smyddevstudio.kanako.model.FuriganaColor
import com.smyddevstudio.kanako.model.ScriptType
import com.smyddevstudio.kanako.model.AnnotationPlateStyle
import com.smyddevstudio.kanako.model.FabSize

@get:StringRes
internal val FuriganaSize.labelRes: Int get() = when (this) {
    FuriganaSize.SMALL -> R.string.reading_size_small
    FuriganaSize.MEDIUM -> R.string.reading_size_medium
    FuriganaSize.LARGE -> R.string.reading_size_large
}
@get:StringRes
internal val FuriganaColor.labelRes: Int get() = when (this) {
    FuriganaColor.MUSHROOM_CRIMSON -> R.string.color_crimson
    FuriganaColor.INK_BLACK -> R.string.color_black
    FuriganaColor.INDIGO -> R.string.color_indigo
    FuriganaColor.PINE_GREEN -> R.string.color_green
}
@get:StringRes
internal val ScriptType.labelRes: Int get() = when (this) {
    ScriptType.KANA -> R.string.script_kana
    ScriptType.ROMAJI -> R.string.script_romaji
}
@get:StringRes
internal val AnnotationPlateStyle.labelRes: Int get() = when (this) {
    AnnotationPlateStyle.CLEAR -> R.string.plate_clear
    AnnotationPlateStyle.WASHI_CAPSULE -> R.string.plate_washi
    AnnotationPlateStyle.HIGH_CONTRAST -> R.string.plate_contrast
    AnnotationPlateStyle.SUBTLE -> R.string.plate_subtle
}
@get:StringRes
internal val FabSize.labelRes: Int get() = when (this) {
    FabSize.COMPACT -> R.string.size_compact
    FabSize.MEDIUM -> R.string.size_medium
    FabSize.LARGE -> R.string.size_large
}

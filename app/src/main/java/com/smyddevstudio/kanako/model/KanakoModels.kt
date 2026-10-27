package com.smyddevstudio.kanako.model

import kotlinx.serialization.Serializable

@Serializable
enum class FuriganaSize(val label: String, val sizeSp: Int) {
    SMALL("10sp", 10),
    MEDIUM("12sp", 12),
    LARGE("14sp", 14)
}

@Serializable
enum class FuriganaColor(val label: String, val colorHex: Long) {
    MUSHROOM_CRIMSON("Mushroom Crimson", 0xFFB82923),
    INK_BLACK("Ink Black", 0xFF2B2625),
    INDIGO("Indigo", 0xFF233E60),
    PINE_GREEN("Pine Green", 0xFF24583F)
}

@Serializable
enum class ScriptType(val label: String) {
    KANA("Kana"),
    ROMAJI("Romaji")
}

@Serializable
enum class AnnotationPlateStyle(val label: String) {
    CLEAR("Clear"),
    WASHI_CAPSULE("Washi Capsule"),
    HIGH_CONTRAST("High Contrast"),
    SUBTLE("Subtle Card")
}

@Serializable
enum class FabSize(val label: String) {
    COMPACT("Compact"),
    MEDIUM("Medium"),
    LARGE("Large")
}

@Serializable
enum class FabDocking(val label: String) {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_LEFT("Bottom Left"),
    FLOATING("Floating Overlay")
}

@Serializable
data class KanakoPreferences(
    val furiganaSize: FuriganaSize = FuriganaSize.MEDIUM,
    val furiganaColor: FuriganaColor = FuriganaColor.MUSHROOM_CRIMSON,
    val scriptType: ScriptType = ScriptType.KANA,
    val annotationPlateStyle: AnnotationPlateStyle = AnnotationPlateStyle.WASHI_CAPSULE,
    val touchPassthrough: Boolean = true,
    val fabSize: FabSize = FabSize.MEDIUM,
    val fabDocking: FabDocking = FabDocking.FLOATING,
    val fabOffsetX: Int = 100,
    val fabOffsetY: Int = 300,
    val hasSavedFloatingPosition: Boolean = false,
    val autoHideOnKeyboard: Boolean = true,
    val autoReadAfterScroll: Boolean = false,
    val offerAutoRead: Boolean = false,
    val hasAcceptedSnapshotDisclosure: Boolean = false,
    val isOverlayActive: Boolean = false,
    val isAutoTranslateEnabled: Boolean = false,
    val isDarkMode: Boolean = false
)

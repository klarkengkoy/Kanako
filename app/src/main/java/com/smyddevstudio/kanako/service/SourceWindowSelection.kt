package com.smyddevstudio.kanako.service

internal fun applicationWindowPriority(isApplication: Boolean, focused: Boolean, active: Boolean): Int =
    when {
        !isApplication -> 0
        focused -> 2
        active -> 1
        else -> 0
    }

internal fun isReaderOverlayEvent(
    ownPackage: String,
    eventPackage: String?,
    eventWindow: Int,
    sourcePackage: String?,
    sourceWindow: Int?
): Boolean = eventPackage == ownPackage &&
    (sourcePackage != ownPackage || sourceWindow != eventWindow)

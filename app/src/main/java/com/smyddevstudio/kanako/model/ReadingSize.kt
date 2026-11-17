package com.smyddevstudio.kanako.model

internal val FuriganaSize.readingScale: Float
    get() = sizeSp.toFloat() / FuriganaSize.MEDIUM.sizeSp

internal fun preferredReadingFontPixels(minimum: Float, sourceSize: Float, sizeScale: Float = 1f): Float =
    (sourceSize * 0.5f * sizeScale).coerceIn(minimum, minimum * 2f)

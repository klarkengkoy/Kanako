package com.smyddevstudio.kanako.service

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Paired light/dark edges stay visible without retaining or sampling source pixels. */
internal fun drawReadingHighlight(
    canvas: Canvas, bounds: ReadingBounds, selected: Boolean, strokeWidth: Float
) {
    if (bounds.isEmpty || !strokeWidth.isFinite() || strokeWidth <= 0f) return
    val outerWidth = strokeWidth * if (selected) 4f else 3f
    val inset = minOf(outerWidth / 2f, (bounds.right - bounds.left) / 4f, (bounds.bottom - bounds.top) / 4f)
    val left = bounds.left + inset
    val top = bounds.top + inset
    val right = bounds.right - inset
    val bottom = bounds.bottom - inset
    val radius = strokeWidth * 3f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.argb(if (selected) 48 else 20, 255, 255, 255)
    canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = outerWidth
    paint.color = Color.argb(230, 20, 20, 20)
    canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    paint.strokeWidth = strokeWidth * if (selected) 2f else 1f
    paint.color = Color.WHITE
    canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
}

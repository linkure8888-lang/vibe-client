package com.vibe.client.ui

import com.vibe.client.ui.Theme

/**
 * Encapsulates the rendering context for UI modules with flexible overloads for all stub styles.
 */
class RenderContext(
    val drawText: (String, Float, Float, Int) -> Unit = { _, _, _, _ -> },
    val drawRect: (Float, Float, Float, Float, Int) -> Unit = { _, _, _, _, _ -> },
    val textWidth: (String) -> Float = { 0f },
    val lineHeight: Float = 10f,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    fun drawText(text: String, x: Number, y: Number, color: Int) = drawText.invoke(text, x.toFloat(), y.toFloat(), color)
    fun drawText(text: String, x: Number, y: Number, color: Int, shadow: Boolean) = drawText.invoke(text, x.toFloat(), y.toFloat(), color)
    
    fun drawRect(x: Number, y: Number, x2: Number, y2: Number, color: Int) = drawRect.invoke(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), color)
    fun fill(x: Number, y: Number, w: Number, h: Number, color: Int) = drawRect.invoke(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)
    
    // Additional overloaded fill variant for different parameter ordering (e.g. x, y, width, height, color)
    fun fill(x: Number, y: Number, w: Number, h: Number, color: Number) = drawRect.invoke(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color.toInt())

    fun textWidth(text: String): Float = textWidth.invoke(text)
    fun lineHeight(): Float = lineHeight

    val accent: Int = Theme.ACCENT
    operator fun invoke(): Int = Theme.ACCENT
    operator fun invoke(t: Float): Int = Theme.ACCENT

    var panelStyle: Boolean = true
    var isHovered: Boolean = false
    var inPvpPreset: Boolean = false
    var RADIUS: Float = 4f
    val fill: RectFill = { x, y, w, h, color -> fill(x, y, w, h, color) }
}

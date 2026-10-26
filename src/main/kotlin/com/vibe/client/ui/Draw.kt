package com.vibe.client.ui

/**
 * Drawing primitives shared by every custom-drawn screen.
 *
 * The vanilla [net.minecraft.client.gui.DrawContext] only offers axis-aligned
 * `fill`, so anything that is not a rectangle has to be composed out of
 * rectangles. That is fine — at Minecraft GUI scales a surface built from
 * horizontal scanlines with rounded ends is visually indistinguishable from a
 * real rounded rectangle, and it costs no extra state.
 *
 * Everything here takes a `fill` lambda (the caller's rect primitive) so the
 * helpers stay engine-free and can be reused by the HUD, the editor and any
 * future screen.
 */
typealias RectFill = (x: Int, y: Int, w: Int, h: Int, color: Int) -> Unit

object Draw {

    /**
     * Vertical gradient between two colours.
     *
     * [steps] is deliberately small: the HUD path calls this per frame and
     * banding is invisible at these heights anyway.
     */
    fun gradientV(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        top: Int, bottom: Int,
        steps: Int = 24
    ) {
        if (w <= 0 || h <= 0) return
        val n = steps.coerceAtLeast(1)
        for (i in 0 until n) {
            val y0 = y + (h.toLong() * i / n).toInt()
            val y1 = y + (h.toLong() * (i + 1) / n).toInt()
            if (y1 <= y0) continue
            val f = if (n == 1) 0f else i / (n - 1f)
            fill(x, y0, w, y1 - y0, Theme.blend(top, bottom, f))
        }
    }

    /**
     * Filled rectangle with rounded corners.
     *
     * Each scanline is shortened by the circle inset for its row, which is the
     * exact shape of a rounded rect minus the corner antialiasing. [radius] is
     * clamped so it can never exceed half of either side.
     */
    fun roundRect(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        radius: Int,
        color: Int
    ) {
        if (w <= 0 || h <= 0) return
        val r = radius.coerceIn(0, minOf(w, h) / 2)
        if (r <= 0) {
            fill(x, y, w, h, color)
            return
        }
        for (row in 0 until h) {
            // Distance from the nearest horizontal corner centre.
            val dy = when {
                row < r -> r - row - 0.5f
                row >= h - r -> row - (h - r) + 0.5f
                else -> 0f
            }
            val inset = if (dy <= 0f) 0 else {
                val dx = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
                (r - dx).toInt()
            }
            val rowX = x + inset
            val rowW = w - inset * 2
            if (rowW > 0) fill(rowX, y + row, rowW, 1, color)
        }
    }

    /** Same shape as [roundRect], filled with a vertical gradient. */
    fun roundRectGradient(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        radius: Int,
        top: Int, bottom: Int,
        steps: Int = 24
    ) {
        if (w <= 0 || h <= 0) return
        val n = steps.coerceAtLeast(1)
        for (i in 0 until n) {
            val y0 = y + (h.toLong() * i / n).toInt()
            val y1 = y + (h.toLong() * (i + 1) / n).toInt()
            if (y1 <= y0) continue
            val f = if (n == 1) 0f else i / (n - 1f)
            roundRectRows(fill, x, y0, w, y1 - y0, radius, h, Theme.blend(top, bottom, f))
        }
    }

    /**
     * One slice of a rounded rect: the corner inset is computed from the
     * absolute row inside the whole shape, not from the slice.
     */
    private fun roundRectRows(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        radius: Int, totalH: Int,
        color: Int
    ) {
        val r = radius.coerceIn(0, minOf(w, totalH) / 2)
        if (r <= 0) {
            fill(x, y, w, h, color)
            return
        }
        val baseY = y
        for (row in 0 until h) {
            val absRow = baseY + row
            val dy = when {
                absRow < r -> r - absRow - 0.5f
                absRow >= totalH - r -> absRow - (totalH - r) + 0.5f
                else -> 0f
            }
            val inset = if (dy <= 0f) 0 else {
                val dx = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
                (r - dx).toInt()
            }
            val rowW = w - inset * 2
            if (rowW > 0) fill(x + inset, absRow, rowW, 1, color)
        }
    }

    /** 1px outline of a rounded rect. */
    fun roundRectOutline(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        radius: Int,
        color: Int
    ) {
        if (w <= 2 || h <= 2) return
        val r = radius.coerceIn(0, minOf(w, h) / 2)
        if (r <= 0) {
            fill(x, y, w, 1, color)
            fill(x, y + h - 1, w, 1, color)
            fill(x, y, 1, h, color)
            fill(x + w - 1, y, 1, h, color)
            return
        }
        // Horizontal runs.
        fill(x + r, y, w - r * 2, 1, color)
        fill(x + r, y + h - 1, w - r * 2, 1, color)
        // Vertical runs.
        fill(x, y + r, 1, h - r * 2, color)
        fill(x + w - 1, y + r, 1, h - r * 2, color)
        // Corner arcs, one pixel block per row.
        for (i in 0 until r) {
            val dy = r - i - 0.5f
            val dx = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
            val inset = (r - dx).toInt()
            fill(x + inset, y + i, 1, 1, color)
            fill(x + w - 1 - inset, y + i, 1, 1, color)
            fill(x + inset, y + h - 1 - i, 1, 1, color)
            fill(x + w - 1 - inset, y + h - 1 - i, 1, 1, color)
        }
    }

    /**
     * Neon bloom around a rectangle.
     *
     * Concentric 1px outlines with falling alpha, the standard 2D trick. Nine
     * layers is where the falloff stops showing steps at typical GUI scales.
     */
    fun glow(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        color: Int,
        intensity: Float,
        layers: Int = 9
    ) {
        if (intensity <= 0.02f) return
        for (i in layers downTo 1) {
            val a = ((color ushr 24) * (1f - i / (layers + 1f)) * intensity * 0.55f)
                .toInt().coerceIn(0, 255)
            if (a <= 1) continue
            val c = (a shl 24) or (color and 0x00FFFFFF)
            fill(x - i, y - i, w + i * 2, 1, c)
            fill(x - i, y + h + i - 1, w + i * 2, 1, c)
            fill(x - i, y - i, 1, h + i * 2, c)
            fill(x + w + i - 1, y - i, 1, h + i * 2, c)
        }
    }

    /**
     * Horizontal accent ramp, [Theme.ACCENT] to [Theme.ACCENT_2].
     *
     * Used for the header rule, the active tab marker and progress fills — the
     * one signature detail that ties the launcher and the in-game menu together.
     */
    fun accentRamp(
        fill: RectFill,
        x: Int, y: Int, w: Int, h: Int,
        steps: Int = 24
    ) {
        if (w <= 0 || h <= 0) return
        val n = steps.coerceAtLeast(1)
        for (i in 0 until n) {
            val x0 = x + (w.toLong() * i / n).toInt()
            val x1 = x + (w.toLong() * (i + 1) / n).toInt()
            if (x1 <= x0) continue
            val f = if (n == 1) 0f else i / (n - 1f)
            fill(x0, y, x1 - x0, h, Theme.blend(Theme.ACCENT, Theme.ACCENT_2, f))
        }
    }
}

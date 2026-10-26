package com.vibe.client.ui

/**
 * Central palette for the client UI.
 *
 * Direction: deep obsidian base, one violet-to-cyan neon accent pair, one pink
 * highlight used sparingly. Every surface is a gradient (light from above) and
 * every raised element gets a 1px highlight on its top edge. Flat fills are what
 * make a menu read as a debug window; a gradient plus a hairline is what makes
 * it read as a product.
 *
 * Everything the in-game menu, the HUD, the toasts and the main menu draw comes
 * from here, so re-skinning the whole client is a single-file change.
 */
object Theme {

    // -- Backdrop ---------------------------------------------------------
    /** Full-screen dim behind the menu. */
    const val BACKDROP = 0xE00A0B12.toInt()

    // -- Surfaces ---------------------------------------------------------
    /** Panel gradient: lighter at the top, near-black at the bottom. */
    const val PANEL_TOP = 0xF4151725.toInt()
    const val PANEL_BOTTOM = 0xF407080E.toInt()

    /** Recessed surface (sidebar, list wells). */
    const val SURFACE = 0xA60F111B.toInt()

    /** Raised / hovered surface. */
    const val SURFACE_HI = 0xD91B1E2E.toInt()

    // -- Lines ------------------------------------------------------------
    const val BORDER = 0x1FFFFFFF
    const val BORDER_STRONG = 0x40FFFFFF
    const val DIVIDER = 0x14FFFFFF

    /** Top-edge light line on a raised surface. */
    const val HIGHLIGHT = 0x26FFFFFF

    // -- Accent -----------------------------------------------------------
    /** Primary accent: violet. */
    const val ACCENT = 0xFF8B5CF6.toInt()

    /** Gradient partner: cyan. Accent -> Accent2 is the signature ramp. */
    const val ACCENT_2 = 0xFF22D3EE.toInt()

    /** Rare contrast pop: pink. */
    const val ACCENT_3 = 0xFFF472B6.toInt()

    const val ACCENT_DIM = 0x708B5CF6
    const val ACCENT_SOFT = 0x2E8B5CF6

    // -- Extended accents -------------------------------------------------
    /** Darker violet for pressed states, badges, deep tints. */
    const val VIOLET_DARK = 0xFF6D28D9.toInt()

    /** Darker cyan for secondary icons, dimmed status. */
    const val CYAN_DARK = 0xFF0891B2.toInt()

    /** Success / online / positive state. */
    const val SUCCESS = 0xFF10B981.toInt()

    /** Destructive / error / offline state. */
    const val ERROR_STATE = 0xFFEF4444.toInt()

    /** Caution / warning. */
    const val WARNING = 0xFFF59E0B.toInt()

    // -- Rows -------------------------------------------------------------
    const val ROW_HOVER = 0x1AFFFFFF
    const val ROW_SELECTED = 0x338B5CF6

    // -- Text -------------------------------------------------------------
    const val TEXT = 0xFFF6F7FC.toInt()
    const val TEXT_MUTED = 0xFFA2A6BB.toInt()
    const val TEXT_DIM = 0xFF666A80.toInt()

    // -- Status -----------------------------------------------------------
    const val OK = 0xFF4ADE80.toInt()
    const val WARN = 0xFFFBBF24.toInt()
    const val ERROR = 0xFFF87171.toInt()

    // -- Controls ---------------------------------------------------------
    const val PILL_ON = ACCENT
    const val PILL_OFF = 0xFF2A2D3C.toInt()
    const val KNOB = 0xFFFFFFFF.toInt()

    // -- Depth ------------------------------------------------------------
    /** Drop shadow under the panel. */
    const val SHADOW = 0x76000000

    // -- Gradients --------------------------------------------------------
    /**
     * GRADIENT_VIOLET_CYAN: a pair of stop colours for the signature linear
     * gradient, violet (#8B5CF6) → cyan (#22D3EE). Use with [Draw.gradientV]
     * or [Draw.accentRamp].
     */
    val GRADIENT_VIOLET_CYAN: Pair<Int, Int> = Pair(ACCENT, ACCENT_2)

    // -- Fonts ------------------------------------------------------------
    /**
     * Logical size tokens.  Minecraft doesn't expose a real font system, so
     * these are int tokens the rendering layer interprets (e.g. to pick a
     * different renderer or apply a scale).  The values match a sensible
     * "sp equivalent" for typical GUI scales.
     */
    object Font {
        /** Normal body text — matches Minecraft's default renderer. */
        const val BODY_SIZE = 8

        /** Larger title text, bold. */
        const val TITLE_SIZE = 18
        const val TITLE_BOLD = true

        /** Large bold label (subtitles, section headers). */
        const val LARGE_BOLD_SIZE = 16
        const val LARGE_BOLD_BOLD = true
    }

    // -- Helpers ----------------------------------------------------------

    /** ARGB interpolation; [t] is clamped to 0..1. */
    fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Replaces the alpha channel of [color]. */
    fun alpha(color: Int, a: Int): Int =
        ((a.coerceIn(0, 255)) shl 24) or (color and 0x00FFFFFF)

    /** Scales the alpha of [color] by [factor] (0..1). */
    fun fade(color: Int, factor: Float): Int {
        val a = ((color ushr 24) * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * Returns a gradient-stop pair interpolated between [GRADIENT_VIOLET_CYAN]
     * stops at position [t] (0 = violet end, 1 = cyan end).
     */
    fun gradientVioletCyan(t: Float): Int = blend(ACCENT, ACCENT_2, t)
}

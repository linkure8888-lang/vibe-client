package com.vibe.client.feature

import com.vibe.client.VibeClient
import com.vibe.client.config.ConfigManager
import com.vibe.client.ui.Theme
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.TitleScreen

/**
 * Custom main menu: a tuned backdrop plus Vibe branding.
 *
 * Placement rules, learned from the first attempt looking wrong:
 *
 *  - **Never draw over the vanilla logo.** The Minecraft wordmark sits at the
 *    top centre, and the splash text hangs off it. Anything placed near the top
 *    centre collides with both. Our brand lives in the corners.
 *  - **The bottom of the screen already belongs to Mojang.** The copyright line
 *    and the version are bottom-left, and the "not signed in" prompt is
 *    bottom-right. So the corners are used as a *column* above those, not on
 *    top of them: brand in the top-left, build info in the top-right.
 *
 * The backdrop itself is three translucent washes over the game's own
 * panorama: an accent bloom, a dark pass, and a vertical vignette. Tuning the
 * existing panorama is what makes it read as *our* menu while keeping the
 * expensive part (and the drifting motion) that Minecraft already provides.
 */
object VibeMainMenu {

    /** Read by the mixin; false restores the untouched vanilla menu. */
    val enabled: Boolean get() = ConfigManager.current.customMainMenu

    /** Frames since the client started, used to drive the pulse. */
    private var ticks = 0

    /** Called once per frame before the vanilla menu draws. */
    fun beforeRender(screen: TitleScreen) {
        if (!enabled) return
        ticks++
    }

    /** Called once per frame after the vanilla menu has drawn. */
    fun draw(context: DrawContext, screen: TitleScreen) {
        if (!enabled) return

        val width = screen.width
        val height = screen.height
        val renderer = MinecraftClient.getInstance().textRenderer

        drawBackdrop(context, width, height)
        drawBrand(context, renderer)
        drawBuildInfo(context, renderer, width)
    }

    // -- Backdrop ----------------------------------------------------------

    /**
     * Darkens and tints the panorama without hiding it.
     *
     * The vanilla panorama is bright and busy; the buttons sit directly on it
     * and become hard to read. The washes fix the contrast, which is a real
     * usability win, not just decoration.
     */
    private fun drawBackdrop(context: DrawContext, width: Int, height: Int) {
        // Accent bloom: a faint blue lift, so the scene feels like our palette.
        context.fill(0, 0, width, height, TINT_ACCENT)
        // Main darkening pass.
        context.fill(0, 0, width, height, TINT_DARK)

        // Vignette: a few progressively stronger bands at top and bottom.
        // Cheaper than a gradient shader and it reads the same at these sizes.
        val bandHeight = (height / 16).coerceAtLeast(4)
        for (i in 0 until 4) {
            val alphaStep = (i + 1) * 0x0C
            val color = (alphaStep shl 24)
            val y = i * bandHeight
            context.fill(0, y, width, y + bandHeight, color)
            context.fill(0, height - y - bandHeight, width, height - y, color)
        }
    }

    // -- Branding ----------------------------------------------------------

    private fun drawBrand(context: DrawContext, renderer: net.minecraft.client.font.TextRenderer) {
        val x = 10
        val y = 10

        // Accent bar to the left of the wordmark, matching the in-game HUD
        // plates so the whole client shares one visual language.
        val pulse = 0.75f + 0.25f * kotlin.math.sin(ticks / 30.0).toFloat()
        context.fill(x, y, x + 2, y + 10, Theme.ACCENT)
        context.fill(x + 2, y, x + 3, y + 10, withAlpha(Theme.ACCENT, pulse))

        context.drawTextWithShadow(renderer, "VIBE", x + 8, y, Theme.ACCENT)
        val vibeWidth = renderer.getWidth("VIBE")
        context.drawTextWithShadow(renderer, "CLIENT", x + 8 + vibeWidth + 3, y, Theme.TEXT)

        // Status line under the brand: a one-glance "the client is loaded".
        context.drawTextWithShadow(renderer, "v${VibeClient.version} \u2022 MC ${VibeClient.mcVersion}",
            x + 8, y + 12, Theme.TEXT_DIM)
    }

    private fun drawBuildInfo(
        context: DrawContext,
        renderer: net.minecraft.client.font.TextRenderer,
        width: Int
    ) {
        val hint = "Right Shift \u2014 menu"
        context.drawTextWithShadow(renderer, hint,
            width - 10 - renderer.getWidth(hint), 10, Theme.TEXT_DIM)

        val clock = currentTime()
        context.drawTextWithShadow(renderer, clock,
            width - 10 - renderer.getWidth(clock), 22, Theme.TEXT_MUTED)
    }

    /**
     * "HH:mm" in local time.
     *
     * Hand-rolled rather than `String.format`: the format call is
     * locale-sensitive and would emit native digits on some systems, which
     * looks wrong next to the Latin wordmark.
     */
    private fun currentTime(): String {
        val now = java.time.LocalTime.now()
        return "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    /** Scales a colour's alpha, used for the "breathing" accent bar. */
    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF)
        val scaled = (alpha * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (scaled shl 24) or (color and 0x00FFFFFF)
    }

    /** Accent wash: a faint blue lift so the scene matches the palette. */
    private const val TINT_ACCENT = 0x1A2F4A8C

    /** Main darkening pass. */
    private const val TINT_DARK = 0xA60E1014.toInt()
}

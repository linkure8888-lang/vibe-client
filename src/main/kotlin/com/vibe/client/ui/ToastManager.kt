package com.vibe.client.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext

/**
 * Top-of-screen notification toasts (Badlion/Lunar style).
 *
 * Pure in-memory list, max [MAX_TOASTS] entries. The render path is allocation
 * free apart from the toast text itself, and only runs when a toast exists.
 */
object ToastManager {

    private const val MAX_TOASTS = 4
    private const val DURATION_MS = 4000L
    private const val WIDTH = 220
    private const val HEIGHT = 22
    private const val MARGIN = 6

    private class Toast(val text: String, val createdAt: Long)

    private val toasts = ArrayDeque<Toast>()

    /** Adds a toast; the newest one sits on top. */
    fun show(text: String) {
        toasts.addLast(Toast(text, System.currentTimeMillis()))
        while (toasts.size > MAX_TOASTS) toasts.removeFirst()
    }

    fun render(context: DrawContext) {
        if (toasts.isEmpty()) return

        val now = System.currentTimeMillis()
        while (toasts.isNotEmpty() && now - toasts.first().createdAt > DURATION_MS) {
            toasts.removeFirst()
        }
        if (toasts.isEmpty()) return

        val client = MinecraftClient.getInstance()
        val textRenderer = client.textRenderer
        val screenWidth = client.window.scaledWidth

        var y = MARGIN
        for (toast in toasts) {
            val x = screenWidth - WIDTH - MARGIN
            context.fill(x, y, x + WIDTH, y + HEIGHT, 0xCC101010.toInt())
            context.drawBorder(x, y, WIDTH, HEIGHT, 0xFF55FF55.toInt())
            context.drawTextWithShadow(
                textRenderer,
                toast.text,
                x + 8,
                y + (HEIGHT - textRenderer.fontHeight) / 2,
                0xFFFFFFFF.toInt()
            )
            y += HEIGHT + 4
        }
    }
}
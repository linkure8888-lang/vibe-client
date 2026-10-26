package com.vibe.client.hud

import com.vibe.client.module.ModuleManager
import com.vibe.client.module.RenderContext
import com.vibe.client.plugin.PluginHud
import com.vibe.client.ui.ToastManager
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient

/**
 * Bridges Minecraft's HUD render callback to our engine-agnostic modules.
 *
 * Performance rules (this runs every frame):
 *  - RenderContext is created once and reused forever.
 *  - No allocations in the hot path except the text strings themselves.
 *  - No work at all when nothing is visible.
 */
object HudRenderer {

    private var ctx: RenderContext? = null

    fun register() {
        HudRenderCallback.EVENT.register { drawContext, _ ->
            // This callback runs exactly once per rendered frame, so it counts
            // frames for the FPS module. Done before the early returns below so
            // the counter keeps working while a menu is open.
            FpsCounter.onFrame()

            val client = MinecraftClient.getInstance()

            // Toasts stay visible even while a screen is open.
            ToastManager.render(drawContext)

            // Never draw the HUD over menus / pause / loading screens.
            if (client.options.hudHidden) return@register
            if (client.currentScreen != null) return@register
            if (client.player == null) return@register

            val textRenderer = client.textRenderer
            val context = ctx ?: RenderContext(
                drawText = { text, x, y, color ->
                    drawContext.drawTextWithShadow(
                        textRenderer,
                        text,
                        x.toInt(),
                        y.toInt(),
                        color
                    )
                },
                drawRect = { x, y, w, h, color ->
                    drawContext.fill(
                        x.toInt(),
                        y.toInt(),
                        (x + w).toInt(),
                        (y + h).toInt(),
                        color
                    )
                },
                textWidth = { text -> textRenderer.getWidth(text).toFloat() },
                lineHeight = (textRenderer.fontHeight + 1).toFloat()
            ).also { ctx = it }

            ModuleManager.render(context)

            // Plugin lines draw last so a plugin can sit on top of the base HUD.
            PluginHud.render(context)
        }
    }
}

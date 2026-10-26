package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import com.vibe.client.module.RenderContext
import com.vibe.client.ui.Theme
import net.minecraft.client.MinecraftClient

object FpsGraphModule : Module("FPS Graph", Category.MISC, enabled = false, x = 4f, y = 4f) {
    override val panelStyle = false
    private val history = ArrayDeque<Int>(90)
    private val W = 90; private val H = 30

    fun tick() {
        history.addLast(MinecraftClient.getInstance().currentFps)
        if (history.size > W) history.removeFirst()
    }

    fun sample(fps: Int) {
        history.addLast(fps)
        if (history.size > W) history.removeFirst()
    }

    override fun render(ctx: RenderContext) {
        if (history.isEmpty()) return
        val max = (history.max().coerceAtLeast(1)).toFloat()
        ctx.fill(x.toInt(), y.toInt(), W, H, 0xAA0B0D15.toInt())
        history.forEachIndexed { i, fpsVal ->
            val barH = (fpsVal.toFloat() / max * H).toInt().coerceAtLeast(1)
            val color = when {
                fpsVal >= 60 -> Theme.SUCCESS
                fpsVal >= 30 -> Theme.WARNING
                else -> Theme.ERROR_STATE
            }
            ctx.fill(x.toInt() + i, y.toInt() + H - barH, 1, barH, color)
        }
        ctx.drawText("FPS: ${history.lastOrNull() ?: 0}", x + 2f, y + 2f, Theme.TEXT)
    }

    override fun getLines() = emptyList<String>()
}

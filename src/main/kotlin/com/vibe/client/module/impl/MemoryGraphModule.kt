package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import com.vibe.client.module.RenderContext
import com.vibe.client.ui.Theme

object MemoryGraphModule : Module("Memory Graph", Category.MISC, enabled = false, x = 4f, y = 80f) {
    override val panelStyle = false
    private val history = ArrayDeque<Int>(90)
    private val W = 90; private val H = 30

    fun tick() {
        val rt = Runtime.getRuntime()
        val usedMb = ((rt.totalMemory() - rt.freeMemory()) / 1048576L).toInt()
        history.addLast(usedMb)
        if (history.size > W) history.removeFirst()
    }

    fun sample(mb: Int) {
        history.addLast(mb)
        if (history.size > W) history.removeFirst()
    }

    override fun render(ctx: RenderContext) {
        if (history.isEmpty()) return
        val max = (Runtime.getRuntime().maxMemory() / 1048576L).toInt().coerceAtLeast(1)
        ctx.fill(x.toInt(), y.toInt(), W, H, 0xAA0B0D15.toInt())
        history.forEachIndexed { i, mb ->
            val barH = (mb.toFloat() / max * H).toInt().coerceAtLeast(1)
            val pct = mb.toFloat() / max
            val color = when {
                pct < 0.6f -> Theme.SUCCESS
                pct < 0.8f -> Theme.WARNING
                else       -> Theme.ERROR_STATE
            }
            ctx.fill(x.toInt() + i, y.toInt() + H - barH, 1, barH, color)
        }
        ctx.drawText("RAM: ${history.lastOrNull() ?: 0}MB", x + 2f, y + 2f, Theme.TEXT)
    }

    override fun getLines() = emptyList<String>()
}

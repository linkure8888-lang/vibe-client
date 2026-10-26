package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import com.vibe.client.module.RenderContext
import com.vibe.client.ui.Theme

object PingGraphModule : Module("Ping Graph", Category.MISC, enabled = false, x = 4f, y = 40f) {
    override val panelStyle = false
    private val history = ArrayDeque<Int>(90)
    private val W = 90; private val H = 30

    fun addPing(ms: Int) {
        history.addLast(ms)
        if (history.size > W) history.removeFirst()
    }

    fun sample(ms: Int) {
        addPing(ms)
    }

    override fun render(ctx: RenderContext) {
        if (history.isEmpty()) return
        val max = (history.max().coerceAtLeast(1)).toFloat()
        ctx.fill(x.toInt(), y.toInt(), W, H, 0xAA0B0D15.toInt())
        history.forEachIndexed { i, ping ->
            val barH = (ping.toFloat() / max * H).toInt().coerceAtLeast(1)
            val color = when {
                ping <= 50  -> Theme.SUCCESS
                ping <= 100 -> Theme.WARNING
                else        -> Theme.ERROR_STATE
            }
            ctx.fill(x.toInt() + i, y.toInt() + H - barH, 1, barH, color)
        }
        ctx.drawText("Ping: ${history.lastOrNull() ?: 0}ms", x + 2f, y + 2f, Theme.TEXT)
    }

    override fun getLines() = emptyList<String>()
}

package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import com.vibe.client.module.RenderContext
import com.vibe.client.ui.Draw
import com.vibe.client.ui.Theme

object KeystrokesModule : Module("Keystrokes", Category.HUD, enabled = false, x = 100f, y = 80f) {
    override val panelStyle = false
    var w = false; var a = false; var s = false; var d = false
    var lmb = false; var rmb = false; var space = false; var shift = false
    fun tick() {}

    private val SZ = 14; private val GAP = 2

    override fun render(ctx: RenderContext) {
        val ox = x.toInt(); val oy = y.toInt()
        fun key(label: String, pressed: Boolean, kx: Int, ky: Int) {
            val col = if (pressed) 0xCC8B5CF6.toInt() else 0xCC1E1E2E.toInt()
            Draw.roundRect({ x, y, w, h, c -> ctx.fill(x, y, w, h, c) }, ox + kx, oy + ky, SZ, SZ, 3, col)
            val tx = ox + kx + SZ / 2 - ctx.textWidth(label) / 2
            ctx.drawText(label, tx.toFloat(), (oy + ky + 3).toFloat(), Theme.TEXT)
        }
        val step = SZ + GAP
        key("W",   w,     step,       0)
        key("A",   a,     0,          step)
        key("S",   s,     step,       step)
        key("D",   d,     step * 2,   step)
        key("LMB", lmb,   0,          step * 2)
        key("SPC", space, step,       step * 2)
        key("RMB", rmb,   step * 2,   step * 2)
    }

    override fun getLines() = emptyList<String>()
}

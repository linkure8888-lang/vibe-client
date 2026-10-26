package com.vibe.client.plugin

import com.vibe.client.module.RenderContext

/**
 * HUD lines contributed by plugins.
 *
 * Kept separate from [com.vibe.client.module.ModuleManager] because plugin
 * lines are dynamic: they appear/disappear at runtime and belong to a plugin
 * that can be disabled. The live HUD calls [render] once per frame, so this
 * path avoids allocations: an entry stores its own text and an AtomicReference
 * is not needed because all writes happen on the game thread.
 */
object PluginHud {

    class Line(
        val pluginId: String,
        val id: String,
        @Volatile var text: String,
        @Volatile var x: Float,
        @Volatile var y: Float,
        @Volatile var color: Int
    )

    private val lines = ArrayList<Line>(8)

    @Synchronized
    fun put(pluginId: String, id: String, text: String, x: Float, y: Float, color: Int) {
        val existing = lines.firstOrNull { it.pluginId == pluginId && it.id == id }
        if (existing != null) {
            existing.text = text
            existing.x = x
            existing.y = y
            existing.color = color
            return
        }
        lines += Line(pluginId, id, text, x, y, color)
    }

    /** Updates only the text; returns false when the line does not exist. */
    @Synchronized
    fun setText(pluginId: String, id: String, text: String): Boolean {
        val line = lines.firstOrNull { it.pluginId == pluginId && it.id == id } ?: return false
        line.text = text
        return true
    }

    @Synchronized
    fun remove(pluginId: String, id: String): Boolean =
        lines.removeAll { it.pluginId == pluginId && it.id == id }

    /** Drops every line of one plugin (called on disable / unload). */
    @Synchronized
    fun removeAll(pluginId: String) {
        lines.removeAll { it.pluginId == pluginId }
    }

    /** Draws every line. Called from the HUD hook; no allocation here. */
    fun render(ctx: RenderContext) {
        // Snapshot under the lock, draw outside it: drawing may be slow.
        val snapshot = synchronized(this) {
            if (lines.isEmpty()) return
            lines.toTypedArray()
        }
        for (line in snapshot) {
            ctx.drawText(line.text, line.x, line.y, line.color)
        }
    }

    @Synchronized
    fun count(): Int = lines.size
}

package com.vibe.client.module

import com.vibe.client.ui.Theme

/** GUI category shown in the ClickGUI rail. */
enum class Category(val displayName: String) {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    HUD("HUD"),
    WORLD("World"),
    MISC("Misc");

    val label: String get() = displayName
}

open class Module(
    val name: String,
    val category: Category,
    var enabled: Boolean = false,
    var x: Float = 100f,
    var y: Float = 100f
) {
    open fun getLines(): List<String> = emptyList()
    open fun render(context: RenderContext) {}
    open fun toggle() { enabled = !enabled }
    open val inPvpPreset: Boolean get() = false
    
    // Accent can be called as a function returning Theme.ACCENT, or accessed as an Int
    open val panelStyle: Boolean get() = true
    
    open fun accent(): Int = Theme.ACCENT

    open fun width(ctx: RenderContext): Float {
        val lines = getLines()
        if (lines.isEmpty()) return 100f
        return lines.maxOf { ctx.textWidth(it) } + 10f
    }

    open fun height(ctx: RenderContext): Float {
        val lines = getLines()
        if (lines.isEmpty()) return 20f
        return lines.size * ctx.lineHeight() + 6f
    }

    open val width: Float get() = 100f
    open val height: Float get() = 20f

    open fun isHovered(mouseX: Double, mouseY: Double, ctx: RenderContext): Boolean {
        return mouseX >= x && mouseX <= x + width(ctx) && mouseY >= y && mouseY <= y + height(ctx)
    }

    companion object {
        const val RADIUS: Int = 4
    }

}

open class GraphModule(
    name: String,
    category: Category,
    enabled: Boolean = false,
    x: Float = 100f,
    y: Float = 100f
) : Module(name, category, enabled, x, y) {
    protected val buffer = IntArray(60)
    protected var head = 0
    protected var maxSample = 100

    protected fun push(value: Int) {
        buffer[head] = value
        head = (head + 1) % buffer.size
        if (value > maxSample) maxSample = value
    }

    protected fun latest(): Int = buffer[(head - 1 + buffer.size) % buffer.size]
}

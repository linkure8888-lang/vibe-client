package com.vibe.client.gui.hud

import com.vibe.client.config.ConfigManager
import com.vibe.client.module.Module
import com.vibe.client.module.ModuleManager
import com.vibe.client.module.RenderContext
import com.vibe.client.ui.Draw
import com.vibe.client.ui.RectFill
import com.vibe.client.ui.Theme
import com.vibe.client.ui.ToastManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * Drag & drop HUD editor — the core of the "Lunar-style" HUD customisation.
 *
 * Design:
 *  - It is a plain helper (NOT a Screen), so the menu can host it inside a tab
 *    while the game world stays visible behind it.
 *  - Positions are stored in the module itself; the editor only mutates x/y.
 *  - A click that lands on a module selects it and starts a drag; the offset
 *    between the cursor and the module's origin is preserved, so a module
 *    never jumps under the cursor. A click on empty space clears the selection
 *    and is NOT consumed, so the host screen can handle it.
 *  - Every move calls ConfigManager.requestSave(), which is debounced (1.5 s),
 *    so dragging does not hammer the disk.
 *
 * Editor chrome (the part that turns "a box you can move" into "a tool"):
 *  - a dim outline on every module, a brighter one plus a faint wash under the
 *    cursor,
 *  - an accent outline plus a corner handle on the selected one,
 *  - edge / centre snapping against the screen and the other modules, with an
 *    accent guide line showing what was snapped to,
 *  - a 32 px grid while a modifier key is held during a drag,
 *  - a live name + X/Y readout next to the module being moved.
 *
 * Performance: the HUD path (ModuleManager.render) is untouched and stays
 * allocation free. Everything here only runs while the menu is open, so the
 * few allocations per frame are irrelevant.
 */
class HudEditor {

    private var dragging: Module? = null
    private var grabOffsetX = 0f
    private var grabOffsetY = 0f

    /** Position the module had when the drag started ("did it move?"). */
    private var dragStartX = 0f
    private var dragStartY = 0f

    /** Screen coordinate of the active snap guide, null when nothing snapped. */
    private var snapGuideX: Int? = null
    private var snapGuideY: Int? = null

    private var selectedModule: Module? = null

    /**
     * The module the user picked.
     *
     * Clicking a module selects it (and starts a drag), clicking empty space
     * clears the selection. A host screen can read this to drive a detail
     * panel.
     */
    val selected: Module? get() = selectedModule

    /** True while the user is dragging something (menu can show a hint). */
    val isDragging: Boolean get() = dragging != null

    /** Only modules that are enabled appear in the editor. */
    private fun modules(): List<Module> = ModuleManager.all().filter { it.enabled }

    /** Called from the screen's mouseClicked. Returns true when consumed. */
    fun mouseClicked(mouseX: Double, mouseY: Double, ctx: RenderContext): Boolean {
        // Iterate in reverse so the topmost drawn module wins the hit test.
        val hit = modules().lastOrNull { it.enabled && it.isHovered(mouseX, mouseY, ctx) }
        if (hit == null) {
            // Empty space: drop the selection, but let the host keep the click.
            selectedModule = null
            return false
        }
        selectedModule = hit
        dragging = hit
        dragStartX = hit.x
        dragStartY = hit.y
        grabOffsetX = mouseX.toFloat() - hit.x
        grabOffsetY = mouseY.toFloat() - hit.y
        snapGuideX = null
        snapGuideY = null
        return true
    }

    /** Called from mouseDragged. */
    fun mouseDragged(mouseX: Double, mouseY: Double) {
        val module = dragging ?: return
        val window = MinecraftClient.getInstance().window
        val scaledWidth = window.scaledWidth.toFloat()
        val scaledHeight = window.scaledHeight.toFloat()

        // The module is measured with the last known render context; without
        // one only the screen edges can be snapped to.
        val ctx = lastContext
        val w = ctx?.let { module.width(it) } ?: MIN_VISIBLE
        val h = ctx?.let { module.height(it) } ?: MIN_VISIBLE

        val desiredX = mouseX.toFloat() - grabOffsetX
        val desiredY = mouseY.toFloat() - grabOffsetY

        val snap = computeSnap(module, ctx, desiredX, desiredY, w, h, scaledWidth, scaledHeight)

        // Clamp so a module can never be dragged fully off-screen.
        module.x = snap.x.coerceIn(0f, (scaledWidth - MIN_VISIBLE).coerceAtLeast(0f))
        module.y = snap.y.coerceIn(0f, (scaledHeight - MIN_VISIBLE).coerceAtLeast(0f))
        snapGuideX = snap.guideX
        snapGuideY = snap.guideY
        ConfigManager.requestSave()
    }

    /** Called from mouseReleased. */
    fun mouseReleased() {
        val module = dragging ?: return
        dragging = null
        snapGuideX = null
        snapGuideY = null
        if (module.x != dragStartX || module.y != dragStartY) {
            ToastManager.show("${module.name} moved to ${module.x.toInt()}, ${module.y.toInt()}")
        }
        ConfigManager.requestSave()
    }

    /**
     * Draws every module plus editor chrome (outline of the one being dragged).
     * Modules hidden by the PvP preset are still drawn here, dimmed, so the
     * user can position them for later.
     */
    fun render(context: DrawContext, ctx: RenderContext) {
        val client = MinecraftClient.getInstance()
        val fill: RectFill = { x, y, w, h, color -> context.fill(x, y, x + w, y + h, color) }
        val scaledWidth = client.window.scaledWidth
        val scaledHeight = client.window.scaledHeight

        // The grid sits under the modules so the plates stay readable.
        if (isDragging && modifierHeld()) drawGrid(fill, scaledWidth, scaledHeight)

        val mouseX = scaledMouseX(client)
        val mouseY = scaledMouseY(client)

        for (module in modules()) {
            if (module.enabled) {
                module.render(ctx)
            }
            drawModuleChrome(context, fill, module, ctx, mouseX, mouseY)
        }

        drawSnapGuides(fill, scaledWidth, scaledHeight)

        val dragged = dragging
        if (dragged != null) drawReadout(context, fill, dragged, ctx)
    }

    /** Puts every module back to its default column on the left. */
    fun resetPositions() {
        var y = 4f
        for (module in modules()) {
            module.x = 4f
            module.y = y
            val ctx = lastContext
            y += if (ctx != null) module.height(ctx) + 3f else 14f
        }
        ConfigManager.requestSave()
    }

    /**
     * Puts a single module back on the default column on the left.
     *
     * The default layout is the same one [resetPositions] produces, so a
     * single reset lands the module exactly where the full reset would have
     * put it — no separate "default" table to keep in sync.
     */
    fun resetPosition(module: Module) {
        val ctx = lastContext
        var y = 4f
        for (other in modules()) {
            if (other === module) {
                module.x = 4f
                module.y = y
                break
            }
            y += if (ctx != null) other.height(ctx) + 3f else 14f
        }
        ConfigManager.requestSave()
        ToastManager.show("${module.name} reset")
    }

    /** Selects a module without starting a drag. */
    fun select(module: Module?) {
        selectedModule = module
    }

    /** Drops the current selection. */
    fun clearSelection() {
        selectedModule = null
    }

    /** Last RenderContext seen, used by resetPositions to measure heights. */
    var lastContext: RenderContext? = null
        private set

    fun remember(ctx: RenderContext) {
        lastContext = ctx
    }

    // =====================================================================
    // Chrome
    // =====================================================================

    /**
     * The per-module treatment: a dim outline normally, a brighter one plus a
     * faint wash under the cursor, an accent outline plus a corner handle when
     * selected, red when the module is off and amber when the PvP preset hides
     * it.
     */
    private fun drawModuleChrome(
        context: DrawContext,
        fill: RectFill,
        module: Module,
        ctx: RenderContext,
        mouseX: Double,
        mouseY: Double
    ) {
        val w = module.width(ctx).coerceAtLeast(MIN_VISIBLE).toInt()
        val h = module.height(ctx).coerceAtLeast(ctx.lineHeight.toFloat()).toInt()
        val x = module.x.toInt()
        val y = module.y.toInt()

        val isSelected = module === selectedModule
        val isHovered = module.isHovered(mouseX, mouseY, ctx)

        val color = when {
            isSelected -> Theme.ACCENT
            !module.enabled -> COLOR_DISABLED
            ModuleManager.pvpPreset && !module.inPvpPreset -> COLOR_PRESET_HIDDEN
            isHovered -> COLOR_HOVER
            else -> COLOR_IDLE
        }

        // Round the outline like the module's own plate; custom-drawn modules
        // (keystrokes, graphs) keep the plain rectangle.
        val radius = if (module.panelStyle) Module.RADIUS else 0

        if (isSelected) {
            Draw.roundRect(fill, x, y, w, h, radius, TINT_SELECTED)
            Draw.glow(fill, x, y, w, h, Theme.ACCENT, 0.5f, 6)
        } else if (isHovered) {
            Draw.roundRect(fill, x, y, w, h, radius, TINT_HOVER)
        }

        if (radius > 0) {
            Draw.roundRectOutline(fill, x, y, w, h, radius, color)
        } else {
            context.drawBorder(x, y, w, h, color)
        }

        // Selection handle: a small accent chip on the top-left corner, so the
        // selection still reads over bright terrain.
        if (isSelected) {
            Draw.roundRect(fill, x - 2, y - 2, 6, 6, 1, Theme.ACCENT)
        }
    }

    /** The 1px accent lines that show what the module snapped to. */
    private fun drawSnapGuides(fill: RectFill, scaledWidth: Int, scaledHeight: Int) {
        val guide = Theme.fade(Theme.ACCENT, 0.7f)
        snapGuideX?.let { fill(it, 0, 1, scaledHeight, guide) }
        snapGuideY?.let { fill(0, it, scaledWidth, 1, guide) }
    }

    /** The 32 px grid, shown while a modifier is held during a drag. */
    private fun drawGrid(fill: RectFill, scaledWidth: Int, scaledHeight: Int) {
        var gx = GRID_SIZE
        while (gx < scaledWidth) {
            fill(gx, 0, 1, scaledHeight, COLOR_GRID)
            gx += GRID_SIZE
        }
        var gy = GRID_SIZE
        while (gy < scaledHeight) {
            fill(0, gy, scaledWidth, 1, COLOR_GRID)
            gy += GRID_SIZE
        }
    }

    /** Name and integer X/Y of the module being moved. */
    private fun drawReadout(
        context: DrawContext,
        fill: RectFill,
        module: Module,
        ctx: RenderContext
    ) {
        val client = MinecraftClient.getInstance()
        val text = "${module.name}  ${module.x.toInt()}, ${module.y.toInt()}"
        val textWidth = client.textRenderer.getWidth(text)
        val textHeight = client.textRenderer.fontHeight

        val moduleH = module.height(ctx).coerceAtLeast(ctx.lineHeight.toFloat()).toInt()
        var tx = module.x.toInt()
        var ty = module.y.toInt() - textHeight - 5
        if (ty < 0) ty = module.y.toInt() + moduleH + 4
        tx = tx.coerceIn(0, (client.window.scaledWidth - textWidth - 6).coerceAtLeast(0))

        Draw.roundRect(fill, tx - 3, ty - 2, textWidth + 6, textHeight + 4, 3, READOUT_BG)
        context.drawTextWithShadow(client.textRenderer, text, tx, ty, Theme.TEXT)
    }

    // =====================================================================
    // Snapping
    // =====================================================================

    /** Result of one snapping pass: the new position plus the guide lines. */
    private class Snap(val x: Float, val y: Float, val guideX: Int?, val guideY: Int?)

    /**
     * Snaps the dragged module to the screen edges and to the edges and
     * centres of the other modules.
     *
     * The dragged module's own left / centre / right anchors are matched
     * against the same anchors of every other module, so "flush left" snaps to
     * flush left and "centres lined up" snaps to centred.
     *
     * The screen limits are `0` and `scaledWidth - MIN_VISIBLE` rather than
     * `scaledWidth` itself: MIN_VISIBLE is the hard clamp that keeps a module
     * on screen, so that is the furthest right a module may legally sit.
     */
    private fun computeSnap(
        module: Module,
        ctx: RenderContext?,
        desiredX: Float,
        desiredY: Float,
        w: Float,
        h: Float,
        scaledWidth: Float,
        scaledHeight: Float
    ): Snap {
        var bestX = desiredX
        var bestXDistance = SNAP_RANGE
        var bestY = desiredY
        var bestYDistance = SNAP_RANGE
        var guideX: Int? = null
        var guideY: Int? = null

        val maxX = (scaledWidth - MIN_VISIBLE).coerceAtLeast(0f)
        val maxY = (scaledHeight - MIN_VISIBLE).coerceAtLeast(0f)

        // Screen edges.
        var delta = -desiredX
        if (abs(delta) < bestXDistance) {
            bestXDistance = abs(delta)
            bestX = desiredX + delta
            guideX = 0
        }
        delta = maxX - desiredX
        if (abs(delta) < bestXDistance) {
            bestXDistance = abs(delta)
            bestX = desiredX + delta
            guideX = maxX.toInt()
        }
        delta = -desiredY
        if (abs(delta) < bestYDistance) {
            bestYDistance = abs(delta)
            bestY = desiredY + delta
            guideY = 0
        }
        delta = maxY - desiredY
        if (abs(delta) < bestYDistance) {
            bestYDistance = abs(delta)
            bestY = desiredY + delta
            guideY = maxY.toInt()
        }

        // Other modules: same anchor against same anchor.
        if (ctx != null) {
            for (other in modules()) {
                if (other === module) continue
                val otherW = other.width(ctx).coerceAtLeast(MIN_VISIBLE)
                val otherH = other.height(ctx).coerceAtLeast(ctx.lineHeight.toFloat())

                for (i in 0..2) {
                    val targetX = anchorPos(i, other.x, otherW)
                    delta = targetX - (desiredX + anchorOffset(i, w))
                    if (abs(delta) < bestXDistance) {
                        bestXDistance = abs(delta)
                        bestX = desiredX + delta
                        guideX = targetX.toInt()
                    }
                    val targetY = anchorPos(i, other.y, otherH)
                    delta = targetY - (desiredY + anchorOffset(i, h))
                    if (abs(delta) < bestYDistance) {
                        bestYDistance = abs(delta)
                        bestY = desiredY + delta
                        guideY = targetY.toInt()
                    }
                }
            }
        }

        return Snap(bestX, bestY, guideX, guideY)
    }

    /** Anchor offset inside the dragged module: 0 = left/top, 1 = centre, 2 = right/bottom. */
    private fun anchorOffset(index: Int, size: Float): Float =
        if (index == 0) 0f else if (index == 1) size / 2f else size

    /** Absolute coordinate of the same anchor on another module. */
    private fun anchorPos(index: Int, origin: Float, size: Float): Float =
        if (index == 0) origin else if (index == 1) origin + size / 2f else origin + size

    // =====================================================================
    // Input helpers
    // =====================================================================

    /**
     * True while any modifier key is held.
     *
     * GLFW 3.3 — the version Minecraft ships — has no `glfwGetKeyMods`, so the
     * modifier bitmask is rebuilt from the modifier keys themselves and the
     * GLFW_MOD_* bits are then tested exactly like the real one.
     */
    private fun modifierHeld(): Boolean {
        val handle = MinecraftClient.getInstance().window.handle
        var mods = 0
        if (isPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT) ||
            isPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)
        ) {
            mods = mods or GLFW.GLFW_MOD_SHIFT
        }
        if (isPressed(handle, GLFW.GLFW_KEY_LEFT_ALT) ||
            isPressed(handle, GLFW.GLFW_KEY_RIGHT_ALT)
        ) {
            mods = mods or GLFW.GLFW_MOD_ALT
        }
        if (isPressed(handle, GLFW.GLFW_KEY_LEFT_CONTROL) ||
            isPressed(handle, GLFW.GLFW_KEY_RIGHT_CONTROL)
        ) {
            mods = mods or GLFW.GLFW_MOD_CONTROL
        }
        return mods and (GLFW.GLFW_MOD_SHIFT or GLFW.GLFW_MOD_ALT or GLFW.GLFW_MOD_CONTROL) != 0
    }

    private fun isPressed(handle: Long, key: Int): Boolean =
        GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS

    /**
     * Cursor position in GUI (scaled) coordinates.
     *
     * `Mouse.getX()` reports window pixels; the game itself converts them
     * with `x * scaledWidth / width`, so the editor does the same. Without
     * the conversion the hover highlight would be off by the GUI scale.
     */
    private fun scaledMouseX(client: MinecraftClient): Double {
        val window = client.window
        val width = window.getWidth()
        if (width <= 0) return client.mouse.getX()
        return client.mouse.getX() * window.scaledWidth / width
    }

    private fun scaledMouseY(client: MinecraftClient): Double {
        val window = client.window
        val height = window.getHeight()
        if (height <= 0) return client.mouse.getY()
        return client.mouse.getY() * window.scaledHeight / height
    }

    companion object {
        private const val MIN_VISIBLE = 8f

        /** How close a module has to be to a snap target, in pixels. */
        private const val SNAP_RANGE = 4f

        /** Grid spacing while a modifier is held. */
        private const val GRID_SIZE = 32

        private const val COLOR_IDLE = 0x40FFFFFF
        private const val COLOR_HOVER = 0xC0FFFFFF.toInt()
        private const val COLOR_DISABLED = 0x80FF5555.toInt()
        private const val COLOR_PRESET_HIDDEN = 0x80FFAA00.toInt()

        /** Faint wash under the cursor. */
        private const val TINT_HOVER = 0x10FFFFFF

        /** Faint accent wash on the selected module. */
        private const val TINT_SELECTED = 0x188B5CF6

        /** Faint grid lines. */
        private const val COLOR_GRID = 0x14FFFFFF

        /** Backing plate of the drag readout. */
        private const val READOUT_BG = 0xC00B0D15.toInt()
    }
}

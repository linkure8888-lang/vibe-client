package com.vibe.client.gui

import com.vibe.client.binds.BindAction
import com.vibe.client.binds.BindManager
import com.vibe.client.config.ConfigManager
import com.vibe.client.gui.hud.HudEditor
import com.vibe.client.lang.Lang
import com.vibe.client.module.Module
import com.vibe.client.module.ModuleManager
import com.vibe.client.module.ModuleMeta
import com.vibe.client.ui.RenderContext
import com.vibe.client.plugin.PluginManager
import com.vibe.client.store.ModsManager
import com.vibe.client.ui.Draw
import com.vibe.client.ui.RectFill
import com.vibe.client.ui.Theme
import com.vibe.client.ui.ToastManager
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * Vibe Client ClickGUI — Lunar & Badlion Tier Redesign.
 *
 * Features:
 *  - Obsidian glass aesthetic with neon violet-to-cyan gradient blooms.
 *  - Instant module search & filter bar.
 *  - Clean 3-column layout: Rail (navigation & categories), Content (cards with working toggles), and Detail panel (live module state & keybinds).
 *  - HUD Editor with drag-and-drop support.
 *  - FPS Boost, Binds with conflict detection, Mods, Plugins, and Settings.
 */
class ClickGui(private val parent: Screen?) : Screen(Text.literal("Vibe Client")) {

    private enum class Tab(val labelKey: String, val fallback: String) {
        MODULES("menu.modules", "Modules"),
        HUD("menu.hud_editor", "HUD"),
        FEATURES("menu.features", "Features"),
        BINDS("menu.binds", "Binds"),
        BOOST("menu.performance", "FPS Boost"),
        MODS("menu.mods", "Mods"),
        PLUGINS("menu.plugins", "Plugins"),
        SETTINGS("menu.settings", "Settings")
    }

    private var tab = Tab.MODULES
    private var prevTab: Tab = Tab.MODULES
    private var tabTransitionProgress: Float = 0f

    private var category: com.vibe.client.module.Category? = null

    private var searchQuery: String = ""
    private var searching: Boolean = false

    private val hudEditor = HudEditor()
    private var hudContext: RenderContext? = null

    private var scroll = 0.0
    private var contentHeight = 0

    private var hoverRow = -1
    private var hoverToggle = false
    private var selected: Module? = null

    private var bindRowIndex = -1
    private var warnRowY = -1

    private val headerHeight: Int get() = 48
    private val railWidth: Int get() = 176
    private val detailWidth: Int get() = 290
    private val margin: Int get() = 14
    private val radius: Int get() = 14

    private val panelLeft: Int get() = margin
    private val panelTop: Int get() = margin
    private val panelRight: Int get() = width - margin
    private val panelBottom: Int get() = height - margin

    private val railLeft: Int get() = panelLeft + 10
    private val railTop: Int get() = panelTop + headerHeight
    private val railBottom: Int get() = panelBottom - 10

    private val contentLeft: Int get() = railLeft + railWidth + 14
    private val contentTop: Int get() = railTop + 4
    private val contentRight: Int get() = panelRight - detailWidth - 24
    private val contentBottom: Int get() = railBottom - 4

    private val detailLeft: Int get() = panelRight - detailWidth - 10
    private val detailRight: Int get() = panelRight - 10
    private val detailTop: Int get() = railTop + 4
    private val detailBottom: Int get() = railBottom - 4

    private val viewHeight: Int get() = (contentBottom - contentTop).coerceAtLeast(20)

    private var fill: RectFill = { _, _, _, _, _ -> }

    private class RailItem(
        val label: String,
        val tab: Tab,
        val category: com.vibe.client.module.Category?,
        val groupStart: Boolean = false
    )

    private val railItems: List<RailItem> = buildList {
        add(RailItem("All modules", Tab.MODULES, null))
        for (entry in com.vibe.client.module.Category.entries) {
            add(RailItem(entry.label, Tab.MODULES, entry))
        }
        var firstClientTab = true
        for (entry in Tab.entries) {
            if (entry == Tab.MODULES) continue
            add(
                RailItem(
                    localized(entry.labelKey, entry.fallback),
                    entry, null,
                    groupStart = firstClientTab
                )
            )
            firstClientTab = false
        }
    }

    override fun init() {
        scroll = 0.0
        contentHeight = 0
        hoverRow = -1
        hoverToggle = false
        animated = true
        openStartMs = System.currentTimeMillis()
    }

    private var animated = false
    private var openStartMs = 0L

    private val openProgress: Float
        get() {
            if (!animated) return 1f
            val elapsed = (System.currentTimeMillis() - openStartMs).toFloat()
            val linear = (elapsed / OPEN_MS).coerceIn(0f, 1f)
            val eased = 1f - (1f - linear) * (1f - linear) * (1f - linear)
            if (eased >= 1f) animated = false
            return eased
        }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val t = openProgress
        fill = { x, y, w, h, color -> context.fill(x, y, x + w, y + h, color) }

        val backdropAlpha = ((Theme.BACKDROP ushr 24) * t).toInt().coerceIn(0, 255)
        context.fill(0, 0, width, height, (backdropAlpha shl 24) or (Theme.BACKDROP and 0x00FFFFFF))

        if (tab != prevTab) {
            tabTransitionProgress = 0f
            prevTab = tab
        }
        tabTransitionProgress = (tabTransitionProgress + delta * 8f).coerceIn(0f, 1f)

        val slide = ((1f - t) * -12f).toInt()
        context.matrices.push()
        context.matrices.translate(0f, slide.toFloat(), 0f)

        val panelW = panelRight - panelLeft
        val panelH = panelBottom - panelTop

        Draw.glow(fill, panelLeft, panelTop, panelW, panelH, Theme.ACCENT, t, 12)
        Draw.roundRect(fill, panelLeft + 3, panelTop + 6, panelW, panelH, radius, Theme.SHADOW)
        Draw.roundRect(fill, panelLeft + 1, panelTop + 2, panelW, panelH, radius, Theme.SHADOW)

        val panelAlpha = (0.97f + 0.03f * t).toFloat()
        val alpha = (255 * panelAlpha).toInt()
        Draw.roundRectGradient(fill, panelLeft, panelTop, panelW, panelH, radius,
            Theme.alpha(Theme.PANEL_TOP, alpha), Theme.alpha(Theme.PANEL_BOTTOM, alpha), 32)
        Draw.roundRectOutline(fill, panelLeft, panelTop, panelW, panelH, radius, Theme.alpha(Theme.BORDER_STRONG, alpha))
        fill(panelLeft + radius, panelTop + 1, panelW - radius * 2, 1, Theme.alpha(Theme.HIGHLIGHT, alpha))

        drawHeader(context)
        drawRail(context, mouseX, mouseY)

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom)
        when (tab) {
            Tab.MODULES -> drawModules(context, mouseX, mouseY)
            Tab.HUD -> drawHudEditor(context)
            Tab.FEATURES -> drawFeatures(context, mouseX, mouseY)
            Tab.BINDS -> drawBinds(context, mouseX, mouseY)
            Tab.BOOST -> drawBoost(context, mouseX, mouseY)
            Tab.MODS -> drawMods(context, mouseX, mouseY)
            Tab.PLUGINS -> drawPlugins(context, mouseX, mouseY)
            Tab.SETTINGS -> drawSettings(context, mouseX, mouseY)
        }
        context.disableScissor()
        drawScrollbar(context)

        drawDetail(context)

        context.matrices.pop()

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawHeader(context: DrawContext) {
        val padX = 20
        val brandY = panelTop + 16

        val markSize = 18
        Draw.roundRect(fill, panelLeft + padX, brandY - 3, markSize, markSize, 5, Theme.ACCENT_SOFT)
        Draw.accentRamp(fill, panelLeft + padX + 3, brandY + 1, 4, 8, 6)
        Draw.accentRamp(fill, panelLeft + padX + 9, brandY + 1, 4, 8, 6)

        val x = panelLeft + padX + markSize + 12
        context.drawTextWithShadow(textRenderer, "VIBE", x, brandY, Theme.TEXT)
        val vibeW = textRenderer.getWidth("VIBE")
        context.drawTextWithShadow(textRenderer, "CLIENT", x + vibeW + 4, brandY, Theme.ACCENT_2)

        val ruleY = panelTop + headerHeight - 1
        val ruleW = panelRight - panelLeft - padX * 2
        for (i in 0 until 32) {
            val segW = ruleW / 32
            val sx = panelLeft + padX + i * segW
            val f = i / 31f
            val alpha = ((1f - f) * 0.85f * 255).toInt().coerceIn(0, 255)
            fill(sx, ruleY, segW, 1, Theme.fade(Theme.blend(Theme.ACCENT, Theme.ACCENT_2, f), alpha / 255f))
        }

        val build = "MC " + com.vibe.client.VibeClient.mcVersion + "  •  Fabric"
        val chipW = textRenderer.getWidth(build) + 16
        val chipX = panelRight - 16 - chipW
        Draw.roundRect(fill, chipX, brandY - 4, chipW, 16, 8, Theme.SURFACE_HI)
        Draw.roundRectOutline(fill, chipX, brandY - 4, chipW, 16, 8, Theme.BORDER)
        context.drawTextWithShadow(textRenderer, build, chipX + 8, brandY - 1, Theme.TEXT_MUTED)
    }

    private fun drawRail(context: DrawContext, mouseX: Int, mouseY: Int) {
        val w = railWidth
        val h = railBottom - railTop

        Draw.roundRect(fill, railLeft, railTop, w, h, 10, Theme.SURFACE)
        Draw.roundRectOutline(fill, railLeft, railTop, w, h, 10, Theme.BORDER)

        var y = railTop + 8
        for ((index, item) in railItems.withIndex()) {
            if (item.groupStart) {
                fill(railLeft + 12, y + 4, w - 24, 1, Theme.DIVIDER)
                y += 12
            }

            val active = item.tab == tab && item.category == category
            val hovered = !active &&
                mouseX in railLeft until railLeft + w &&
                mouseY in y until y + RAIL_ROW_H

            if (active) {
                Draw.roundRect(fill, railLeft + 6, y, w - 12, RAIL_ROW_H, 8, Theme.SURFACE_HI)
                Draw.roundRectOutline(fill, railLeft + 6, y, w - 12, RAIL_ROW_H, 8, Theme.fade(Theme.ACCENT, 0.4f))
                Draw.accentRamp(fill, railLeft + 6, y + 6, 3, RAIL_ROW_H - 12, 8)
            } else if (hovered) {
                Draw.roundRect(fill, railLeft + 6, y, w - 12, RAIL_ROW_H, 8, Theme.ROW_HOVER)
            }

            ModuleIcons.category(
                item.category, fill,
                railLeft + 12, y + (RAIL_ROW_H - ICON_SM) / 2, ICON_SM,
                when {
                    active -> Theme.ACCENT_2
                    hovered -> Theme.TEXT
                    else -> Theme.TEXT_DIM
                }
            )

            context.drawTextWithShadow(
                textRenderer, item.label, railLeft + 12 + ICON_SM + 8,
                y + (RAIL_ROW_H - textRenderer.fontHeight) / 2,
                if (active) Theme.TEXT else Theme.TEXT_MUTED
            )

            val count = railCount(item)
            if (count >= 0) {
                val text = count.toString()
                val badgeW = textRenderer.getWidth(text) + 8
                val bx = railLeft + w - 14 - badgeW
                Draw.roundRect(fill, bx, y + (RAIL_ROW_H - 14) / 2, badgeW, 14, 7, Theme.fade(Theme.SURFACE_HI, 0.8f))
                context.drawTextWithShadow(textRenderer, text, bx + 4,
                    y + (RAIL_ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT_DIM)
            }

            y += RAIL_ROW_H
        }

        val version = "v" + com.vibe.client.VibeClient.version
        context.drawTextWithShadow(textRenderer, version, railLeft + 16, railBottom - 18, Theme.TEXT_DIM)
    }

    private fun railCount(item: RailItem): Int {
        if (item.tab != Tab.MODULES) return -1
        val all = ModuleManager.all()
        if (item.category == null) return all.size
        return all.count { ModuleMeta.of(it).category == item.category }
    }

    private fun drawModules(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1
        hoverToggle = false

        val searchW = contentRight - contentLeft - 12
        val searchH = 22
        val searchX = contentLeft + 6
        val searchY = y

        Draw.roundRect(fill, searchX, searchY, searchW, searchH, 8, if (searching) Theme.SURFACE_HI else Theme.SURFACE)
        Draw.roundRectOutline(fill, searchX, searchY, searchW, searchH, 8, if (searching) Theme.ACCENT else Theme.BORDER)

        val placeholder = if (searchQuery.isEmpty()) "🔍 Search modules (type to filter)..." else "🔍 " + searchQuery
        val placeholderColor = if (searchQuery.isEmpty()) Theme.TEXT_DIM else Theme.TEXT
        context.drawTextWithShadow(textRenderer, placeholder, searchX + 10, searchY + 7, placeholderColor)

        y += searchH + 8

        val modules = filteredModules()
        y = drawSectionTitle(context, if (searchQuery.isNotEmpty()) "Search: " + searchQuery else (category?.label ?: "All modules"), modules.size, y)

        modules.forEachIndexed { index, module ->
            val rowY = y
            val hovered = isRowHovered(mouseX, mouseY, rowY)
            if (hovered) hoverRow = index
            if (rowY + CARD_H >= contentTop && rowY <= contentBottom) {
                drawModuleCard(context, module, rowY, hovered, mouseX, mouseY)
            }
            y += CARD_H + 4
        }

        if (modules.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No modules found.", contentLeft + 12, y + 6, Theme.TEXT_DIM)
            y += 24
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun filteredModules(): List<Module> {
        val all = ModuleManager.all()
        val byCat = if (category == null) all else all.filter { ModuleMeta.of(it).category == category }
        if (searchQuery.isEmpty()) return byCat
        val q = searchQuery.lowercase()
        return byCat.filter { it.name.lowercase().contains(q) || ModuleMeta.of(it).description.lowercase().contains(q) }
    }

    private fun drawModuleCard(
        context: DrawContext,
        module: Module,
        rowY: Int,
        hovered: Boolean,
        mouseX: Int,
        mouseY: Int
    ) {
        val cardX = contentLeft + 6
        val cardW = contentRight - contentLeft - 12

        val isSelected = selected === module
        when {
            isSelected -> {
                Draw.roundRect(fill, cardX, rowY, cardW, CARD_H, 10, Theme.fade(Theme.ACCENT, 0.18f))
                Draw.roundRectOutline(fill, cardX, rowY, cardW, CARD_H, 10, Theme.fade(Theme.ACCENT, 0.7f))
            }
            hovered -> {
                Draw.roundRect(fill, cardX, rowY, cardW, CARD_H, 10, Theme.SURFACE_HI)
                Draw.roundRectOutline(fill, cardX, rowY, cardW, CARD_H, 10, Theme.BORDER_STRONG)
            }
            else -> Draw.roundRect(fill, cardX, rowY, cardW, CARD_H, 10, 0x10FFFFFF)
        }

        val iconColor = when {
            isSelected || hovered -> Theme.TEXT
            module.enabled -> module.accent()
            else -> Theme.TEXT_DIM
        }
        val iconX = cardX + 12
        val iconY = rowY + (CARD_H - ICON_MD) / 2

        Draw.roundRect(fill, iconX - 4, iconY - 4, ICON_MD + 8, ICON_MD + 8, 6,
            if (module.enabled) Theme.fade(module.accent(), 0.15f) else 0x0AFFFFFF)
        ModuleIcons.module(module.name, fill, iconX, iconY, ICON_MD, iconColor)

        val textX = iconX + ICON_MD + 12
        val nameColor = if (module.enabled) Theme.TEXT else Theme.TEXT_MUTED
        context.drawTextWithShadow(textRenderer, module.name, textX, rowY + 8, nameColor)

        val description = ModuleMeta.of(module).description
        val toggleX = cardX + cardW - PILL_W - 14
        val maxDescW = toggleX - textX - 10
        if (maxDescW > 40) {
            val shown = trimToWidth(description, maxDescW)
            context.drawTextWithShadow(textRenderer, shown, textX, rowY + 19, Theme.TEXT_DIM)
        }

        val toggleY = rowY + (CARD_H - PILL_H) / 2
        val overToggle = mouseX in toggleX until toggleX + PILL_W &&
            mouseY in toggleY until toggleY + PILL_H
        if (hovered && overToggle) hoverToggle = true
        drawToggle(context, toggleX, toggleY, module.enabled, overToggle)
    }

    private fun trimToWidth(text: String, maxWidth: Int): String {
        if (textRenderer.getWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && textRenderer.getWidth(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun drawRowBackground(rowY: Int, hovered: Boolean, active: Boolean) {
        when {
            hovered -> {
                Draw.roundRect(fill, contentLeft + 6, rowY + 1, contentRight - contentLeft - 12,
                    ROW_H - 2, 8, Theme.SURFACE_HI)
                Draw.roundRectOutline(fill, contentLeft + 6, rowY + 1,
                    contentRight - contentLeft - 12, ROW_H - 2, 8, Theme.BORDER_STRONG)
            }
            active -> Draw.roundRect(fill, contentLeft + 6, rowY + 1,
                contentRight - contentLeft - 12, ROW_H - 2, 8, 0x14FFFFFF)
        }
    }

    private fun drawDetail(context: DrawContext) {
        val w = detailRight - detailLeft
        val h = detailBottom - detailTop
        Draw.roundRect(fill, detailLeft, detailTop, w, h, 12, Theme.SURFACE)
        Draw.roundRectOutline(fill, detailLeft, detailTop, w, h, 12, Theme.BORDER_STRONG)

        val padX = detailLeft + 16
        var y = detailTop + 14

        if (tab == Tab.HUD) selected = hudEditor.selected

        val module = selected
        if (tab == Tab.MODULES && module != null) {
            y = drawDetailHeader(context, module, y)
            y += 6
            y = drawInfoRow(context, "Category", ModuleMeta.of(module).category.label, y)
            y = drawInfoRow(context, "Position", "" + module.x.toInt() + ", " + module.y.toInt(), y)
            y = drawInfoRow(context, "Style", if (module.panelStyle) "Panel" else "Custom", y)
            y = drawInfoRow(context, "PvP Preset", if (module.inPvpPreset) "Visible" else "Hidden", y)
            y = drawKeybindRow(context, module, y)

            y += 10
            context.drawTextWithShadow(textRenderer, "DESCRIPTION", padX, y, Theme.TEXT_DIM)
            y += 14
            y = drawWrapped(context, ModuleMeta.of(module).description, padX, y, w - 32)

            y += 12
            context.drawTextWithShadow(textRenderer, "CURRENT OUTPUT", padX, y, Theme.TEXT_DIM)
            y += 14
            val lines = module.getLines()
            if (lines.isEmpty()) {
                context.drawTextWithShadow(textRenderer, "Nothing to show right now.", padX, y, Theme.TEXT_DIM)
                y += 14
            } else {
                for (line in lines) {
                    context.drawTextWithShadow(textRenderer, line, padX, y, Theme.TEXT_MUTED)
                    y += 13
                }
            }
            return
        }

        context.drawTextWithShadow(textRenderer, "VIBE CLIENT", padX, y, Theme.TEXT)
        y += 16
        context.drawTextWithShadow(textRenderer, "MC " + com.vibe.client.VibeClient.mcVersion + "  •  Fabric", padX, y, Theme.TEXT_DIM)
        y += 22

        for ((label, value) in sectionInfo()) {
            y = drawInfoRow(context, label, value, y)
        }
    }

    private fun drawDetailHeader(context: DrawContext, module: Module, y: Int): Int {
        val plate = 28
        val plateX = detailLeft + 16
        val iconX = plateX + (plate - ICON_MD) / 2
        val iconY = y + (plate - ICON_MD) / 2
        val tint = if (module.enabled) module.accent() else Theme.TEXT_DIM

        Draw.roundRect(fill, plateX, y, plate, plate, 8, Theme.fade(tint, 0.18f))
        Draw.roundRectOutline(fill, plateX, y, plate, plate, 8, Theme.fade(tint, 0.4f))
        ModuleIcons.module(module.name, fill, iconX, iconY, ICON_MD, tint)

        val textX = plateX + plate + 12
        context.drawTextWithShadow(textRenderer, module.name, textX, y + 5, Theme.TEXT)

        val dotY = y + 18
        Draw.roundRect(fill, textX, dotY + 1, 6, 6, 3, if (module.enabled) Theme.OK else Theme.TEXT_DIM)
        context.drawTextWithShadow(
            textRenderer,
            if (module.enabled) "Enabled" else "Disabled",
            textX + 10, dotY,
            if (module.enabled) Theme.OK else Theme.TEXT_DIM
        )
        return y + plate
    }

    private fun drawInfoRow(context: DrawContext, label: String, value: String, y: Int): Int {
        context.drawTextWithShadow(textRenderer, label, detailLeft + 16, y, Theme.TEXT_DIM)
        val valueX = detailRight - 16 - textRenderer.getWidth(value)
        context.drawTextWithShadow(textRenderer, value, valueX, y, Theme.TEXT_MUTED)
        return y + 16
    }

    private fun drawKeybindRow(context: DrawContext, module: Module, y: Int): Int {
        context.drawTextWithShadow(textRenderer, "Keybind", detailLeft + 16, y, Theme.TEXT_DIM)
        val bind = BindManager.all.firstOrNull {
            it.action == BindAction.TOGGLE_MODULE && it.payload == module.name && it.enabled
        }
        if (bind == null) {
            val text = "unbound"
            val x = detailRight - 16 - textRenderer.getWidth(text)
            context.drawTextWithShadow(textRenderer, text, x, y, Theme.TEXT_DIM)
            return y + 16
        }
        val text = BindManager.keyName(bind.key)
        val capW = textRenderer.getWidth(text) + 12
        val capX = detailRight - 16 - capW
        Draw.roundRect(fill, capX, y - 2, capW, 14, 5, Theme.SURFACE_HI)
        fill(capX + 3, y - 2, capW - 6, 1, Theme.HIGHLIGHT)
        context.drawTextWithShadow(textRenderer, text, capX + 6, y, Theme.TEXT)
        return y + 16
    }

    private fun drawWrapped(context: DrawContext, text: String, x: Int, y: Int, maxWidth: Int): Int {
        val words = text.split(' ')
        val line = StringBuilder()
        var cursorY = y
        for (word in words) {
            val candidate = if (line.isEmpty()) word else line.toString() + " " + word
            if (textRenderer.getWidth(candidate) > maxWidth && line.isNotEmpty()) {
                context.drawTextWithShadow(textRenderer, line.toString(), x, cursorY, Theme.TEXT_MUTED)
                cursorY += 13
                line.setLength(0)
                line.append(word)
            } else {
                line.setLength(0)
                line.append(candidate)
            }
        }
        if (line.isNotEmpty()) {
            context.drawTextWithShadow(textRenderer, line.toString(), x, cursorY, Theme.TEXT_MUTED)
            cursorY += 13
        }
        return cursorY
    }

    private fun sectionInfo(): List<Pair<String, String>> = when (tab) {
        Tab.HUD -> listOf(
            "Modules" to ModuleManager.all().size.toString(),
            "Preset" to if (ModuleManager.pvpPreset) "PvP" else "Full HUD"
        )
        Tab.FEATURES -> listOf(
            "Features" to featureList().size.toString(),
            "PvP Preset" to if (ModuleManager.pvpPreset) "ON" else "OFF"
        )
        Tab.BINDS -> listOf(
            "Binds" to BindManager.all.size.toString(),
            "Conflicts" to BindManager.conflicts().size.toString()
        )
        Tab.BOOST -> listOf(
            "FPS" to com.vibe.client.hud.FpsCounter.fps.toString(),
            "Boost" to if (ConfigManager.current.fpsBoost) "ON" else "OFF"
        )
        Tab.MODS -> listOf(
            "Installed" to ModsManager.list().size.toString(),
            "Folder" to (ModsManager.directory?.absolutePath ?: "not set")
        )
        Tab.PLUGINS -> listOf(
            "Plugins" to PluginManager.all.size.toString(),
            "Enabled" to PluginManager.all.count { it.state == PluginManager.State.ENABLED }.toString()
        )
        Tab.SETTINGS -> listOf(
            "Language" to ConfigManager.language.uppercase(),
            "PvP Preset" to if (ModuleManager.pvpPreset) "ON" else "OFF"
        )
        Tab.MODULES -> listOf(
            "Modules" to ModuleManager.all().size.toString(),
            "Enabled" to ModuleManager.all().count { it.enabled }.toString()
        )
    }

    private fun drawHudEditor(context: DrawContext) {
        val ctx = hudContext ?: createHudContext(context).also { hudContext = it }
        hudEditor.remember(ctx)
        hudEditor.render(context, ctx)

        val hint = localized("hud.editor_hint", "Drag modules with LMB • Snap & Align")
        context.drawTextWithShadow(textRenderer, hint, contentLeft + 12, contentBottom - 18, Theme.TEXT_MUTED)
    }

    private fun featureList(): List<Feature> {
        val config = ConfigManager.current
        val cosmetics = com.vibe.client.cosmetics.CosmeticsManager
        return listOf(
            Feature("Smooth Sneak", config.smoothSneak),
            Feature("FreeLook", config.freeLook),
            Feature("Fullbright", config.fullbright),
            Feature("Clear Weather", config.clearWeather),
            Feature("No Hurt Cam", config.noHurtCam),
            Feature("No View Bob", config.noViewBob),
            Feature("Hide Hand", config.hideHand),
            Feature("Custom Crosshair", config.customCrosshair),
            Feature("Block Overlay", config.blockOverlay),
            Feature("Chunk Borders", config.chunkBorders),
            Feature("Motion Blur", config.motionBlur),
            Feature("Hit Marker", config.hitMarker),
            Feature("Kill Sounds", config.killSounds),
            Feature("Hit Color", config.hitColor),
            Feature("Custom Main Menu", config.customMainMenu),
            Feature("Low-FPS Protection", config.lowFpsProtection),
            Feature("PvP Preset", ModuleManager.pvpPreset),
            Feature("Cape: " + (cosmetics.equipped?.name ?: "None"), cosmetics.equipped != null)
        )
    }

    private fun drawFeatures(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1

        y = drawSectionTitle(context, "Features & Tweaks", 0, y)

        val features = featureList()
        features.forEachIndexed { index, feature ->
            val rowY = y
            val hovered = isRowHovered(mouseX, mouseY, rowY)
            if (hovered) hoverRow = index

            if (rowY + ROW_H >= contentTop && rowY <= contentBottom) {
                drawRowBackground(rowY, hovered, feature.value)
                context.drawTextWithShadow(
                    textRenderer, feature.label, contentLeft + 28,
                    rowY + (ROW_H - textRenderer.fontHeight) / 2,
                    if (feature.value) Theme.TEXT else Theme.TEXT_MUTED
                )
                drawToggle(context, contentRight - 16 - PILL_W,
                    rowY + (ROW_H - PILL_H) / 2, feature.value, false)
            }
            y += ROW_H
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawBoost(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1

        y = drawSectionTitle(context, "FPS Boost & Performance", 0, y)

        val fps = com.vibe.client.hud.FpsCounter.fps
        val fpsColor = when {
            fps >= 120 -> Theme.OK
            fps >= 60 -> Theme.ACCENT_2
            fps >= 30 -> Theme.WARN
            else -> Theme.ERROR
        }
        val cardH = 46
        val cardW = contentRight - contentLeft - 24
        Draw.roundRect(fill, contentLeft + 12, y, cardW, cardH, 10, Theme.SURFACE)
        Draw.roundRectOutline(fill, contentLeft + 12, y, cardW, cardH, 10, Theme.BORDER_STRONG)

        val barW = (cardW - 160).coerceAtLeast(40)
        val ratio = (fps / 240f).coerceIn(0f, 1f)
        Draw.roundRect(fill, contentLeft + 24, y + cardH - 14, barW, 5, 2, Theme.fade(Theme.TEXT, 0.12f))
        Draw.accentRamp(fill, contentLeft + 24, y + cardH - 14, (barW * ratio).toInt(), 5, 16)

        val big = fps.toString()
        context.drawTextWithShadow(textRenderer, big, contentLeft + 24, y + 11, fpsColor)
        context.drawTextWithShadow(textRenderer, "FPS", contentLeft + 28 + textRenderer.getWidth(big), y + 18, Theme.TEXT_MUTED)
        y += cardH + 8

        val rowY = y
        val hovered = isRowHovered(mouseX, mouseY, rowY)
        if (hovered) hoverRow = 0
        if (rowY + ROW_H >= contentTop && rowY <= contentBottom) {
            drawRowBackground(rowY, hovered, ConfigManager.current.fpsBoost)
            context.drawTextWithShadow(textRenderer, "FPS Boost Master", contentLeft + 28,
                rowY + (ROW_H - textRenderer.fontHeight) / 2,
                if (ConfigManager.current.fpsBoost) Theme.TEXT else Theme.TEXT_MUTED)
            drawToggle(context, contentRight - 16 - PILL_W,
                rowY + (ROW_H - PILL_H) / 2, ConfigManager.current.fpsBoost, false)
        }
        y += ROW_H + 6

        context.drawTextWithShadow(textRenderer, "OPTIMIZATIONS APPLIED", contentLeft + 28, y + 2, Theme.TEXT_DIM)
        y += 18

        val applied = listOf(
            "Clouds: Fast / Disabled",
            "Entity Shadows: Optimized",
            "Biome Blend: Instant",
            "Particles: Optimized",
            "View Bobbing: Clean",
            "Menu Blur: Optimized"
        )
        for (line in applied) {
            Draw.roundRect(fill, contentLeft + 30, y + 4, 4, 4, 2, Theme.fade(Theme.ACCENT_2, 0.8f))
            context.drawTextWithShadow(textRenderer, line, contentLeft + 42, y + 1, Theme.TEXT_MUTED)
            y += 14
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawBinds(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1
        bindRowIndex = -1

        y = drawSectionTitle(context, "Key Binds & Conflicts", BindManager.all.size, y)

        val conflicts = BindManager.conflicts()
        if (conflicts.isNotEmpty()) {
            warnRowY = y
            val w = contentRight - contentLeft - 24
            Draw.roundRect(fill, contentLeft + 12, y, w, 20, 7, Theme.fade(Theme.WARN, 0.15f))
            Draw.roundRectOutline(fill, contentLeft + 12, y, w, 20, 7, Theme.fade(Theme.WARN, 0.4f))
            context.drawTextWithShadow(
                textRenderer,
                "⚠️ " + conflicts.size + " key conflict(s) detected",
                contentLeft + 20, y + 6, Theme.WARN
            )
            y += 26
        } else {
            warnRowY = -1
        }

        if (BindManager.all.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No binds configured.", contentLeft + 28, y + 6, Theme.TEXT_DIM)
            y += 24
        } else {
            BindManager.all.forEachIndexed { index, bind ->
                val rowY = y
                val hovered = isRowHovered(mouseX, mouseY, rowY)
                if (hovered) {
                    hoverRow = BIND_BASE_INDEX + index
                    bindRowIndex = index
                }
                if (rowY + ROW_H >= contentTop && rowY <= contentBottom) {
                    drawBindRow(context, bind, index, rowY, hovered)
                }
                y += ROW_H
            }
        }

        y += 10
        y = drawSectionTitle(context, "Smart Assistant Suggestions", 0, y)

        val suggestions = BindManager.suggestions()
        if (suggestions.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "All recommended binds are active.", contentLeft + 28, y + 6, Theme.TEXT_DIM)
            y += 24
        } else {
            suggestions.forEachIndexed { index, suggestion ->
                val rowY = y
                val hovered = isRowHovered(mouseX, mouseY, rowY)
                if (hovered) {
                    hoverRow = SUGGEST_BASE_INDEX + index
                }
                if (rowY + ROW_H >= contentTop && rowY <= contentBottom) {
                    if (hovered) {
                        Draw.roundRect(fill, contentLeft + 6, rowY + 1,
                            contentRight - contentLeft - 12, ROW_H - 2, 8, Theme.SURFACE_HI)
                    }
                    val key = BindManager.keyName(suggestion.key)
                    val capW = textRenderer.getWidth(key) + 12
                    Draw.roundRect(fill, contentLeft + 14, rowY + 7, capW, 14, 5, Theme.ACCENT_SOFT)
                    context.drawTextWithShadow(textRenderer, key, contentLeft + 20,
                        rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.ACCENT_2)
                    context.drawTextWithShadow(textRenderer, suggestion.describe(),
                        contentLeft + 24 + capW, rowY + (ROW_H - textRenderer.fontHeight) / 2,
                        Theme.TEXT_MUTED)
                    context.drawTextWithShadow(textRenderer, "+", contentRight - 22,
                        rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.OK)
                }
                y += ROW_H
            }
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawBindRow(
        context: DrawContext,
        bind: com.vibe.client.binds.Bind,
        index: Int,
        rowY: Int,
        hovered: Boolean
    ) {
        val conflicting = BindManager.conflictFor(bind.key, index) != null
        drawRowBackground(rowY, hovered, bind.enabled)

        val key = BindManager.keyName(bind.key)
        val capW = textRenderer.getWidth(key) + 12
        val capColor = when {
            !bind.enabled -> 0x14FFFFFF
            conflicting -> Theme.fade(Theme.ERROR, 0.25f)
            else -> Theme.ACCENT_SOFT
        }
        Draw.roundRect(fill, contentLeft + 14, rowY + 7, capW, 14, 5, capColor)

        val keyColor = when {
            !bind.enabled -> Theme.TEXT_DIM
            conflicting -> Theme.ERROR
            else -> Theme.ACCENT_2
        }
        context.drawTextWithShadow(textRenderer, key, contentLeft + 19,
            rowY + (ROW_H - textRenderer.fontHeight) / 2, keyColor)

        context.drawTextWithShadow(textRenderer, bind.describe(), contentLeft + 22 + capW,
            rowY + (ROW_H - textRenderer.fontHeight) / 2,
            if (bind.enabled) Theme.TEXT else Theme.TEXT_MUTED)

        drawToggle(context, contentRight - 16 - PILL_W, rowY + (ROW_H - PILL_H) / 2,
            bind.enabled, false)
    }

    private class Feature(val label: String, val value: Boolean)

    private fun drawMods(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1

        y = drawSectionTitle(context, "Mod Management", 0, y)

        val entries = listOf(
            localized("mods.installed", "Installed Mods"),
            localized("mods.store", "Mod Store & Addons")
        )

        entries.forEachIndexed { index, label ->
            val rowY = y
            val hovered = isRowHovered(mouseX, mouseY, rowY)
            if (hovered) hoverRow = index

            if (hovered) {
                Draw.roundRect(fill, contentLeft + 6, rowY + 1,
                    contentRight - contentLeft - 12, ROW_H - 2, 8, Theme.SURFACE_HI)
            }
            context.drawTextWithShadow(textRenderer, label, contentLeft + 28,
                rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT)
            context.drawTextWithShadow(textRenderer, "›", contentRight - 22,
                rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT_DIM)
            y += ROW_H
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawPlugins(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1

        val total = PluginManager.all.size
        val enabled = PluginManager.all.count { it.state == PluginManager.State.ENABLED }

        y = drawSectionTitle(context, "Plugins Hub", total, y)

        val summary = "Active Plugins: " + enabled + " / " + total
        context.drawTextWithShadow(textRenderer, summary, contentLeft + 28, y + 5, Theme.TEXT_MUTED)
        y += ROW_H

        val rowY = y
        val hovered = isRowHovered(mouseX, mouseY, rowY)
        if (hovered) hoverRow = 0
        if (hovered) {
            Draw.roundRect(fill, contentLeft + 6, rowY + 1,
                contentRight - contentLeft - 12, ROW_H - 2, 8, Theme.SURFACE_HI)
        }
        context.drawTextWithShadow(textRenderer, "Open Plugin Manager", contentLeft + 28,
            rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT)
        context.drawTextWithShadow(textRenderer, "›", contentRight - 22,
            rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT_DIM)

        contentHeight = rowY - contentTop + ROW_H + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawSettings(context: DrawContext, mouseX: Int, mouseY: Int) {
        var y = contentTop + 4 - scroll.toInt()
        hoverRow = -1

        y = drawSectionTitle(context, "Client Settings", 0, y)

        val rows = listOf(
            "Language: " + ConfigManager.language.uppercase(),
            "PvP Preset Mode: " + if (ModuleManager.pvpPreset) "ON" else "OFF"
        )

        rows.forEachIndexed { index, label ->
            val rowY = y
            val hovered = isRowHovered(mouseX, mouseY, rowY)
            if (hovered) hoverRow = index
            if (hovered) {
                Draw.roundRect(fill, contentLeft + 6, rowY + 1,
                    contentRight - contentLeft - 12, ROW_H - 2, 8, Theme.SURFACE_HI)
            }
            context.drawTextWithShadow(textRenderer, label, contentLeft + 28,
                rowY + (ROW_H - textRenderer.fontHeight) / 2, Theme.TEXT)
            y += ROW_H
        }

        contentHeight = y - contentTop + scroll.toInt() + 8
        clampScroll()
    }

    private fun drawSectionTitle(context: DrawContext, title: String, count: Int, y: Int): Int {
        Draw.accentRamp(fill, contentLeft + 14, y + 4, 3, 9, 6)
        context.drawTextWithShadow(textRenderer, title.uppercase(), contentLeft + 22, y + 3, Theme.TEXT_MUTED)

        if (count > 0) {
            val badge = count.toString()
            val bw = textRenderer.getWidth(badge) + 12
            val bx = contentRight - 16 - bw
            Draw.roundRect(fill, bx, y + 1, bw, 15, 7, Theme.SURFACE_HI)
            context.drawTextWithShadow(textRenderer, badge, bx + 6, y + 4, Theme.TEXT_MUTED)
        }

        val lineY = y + 22
        val lineX = contentLeft + 14
        val lineW = contentRight - 16 - lineX
        for (i in 0 until 18) {
            val segW = (lineW / 18).coerceAtLeast(1)
            val f = 1f - i / 17f
            fill(lineX + i * segW, lineY, segW, 1, Theme.fade(Theme.DIVIDER, f))
        }
        return y + 28
    }

    private fun drawToggle(context: DrawContext, x: Int, y: Int, on: Boolean, hovered: Boolean) {
        val r = PILL_H / 2
        if (on) {
            Draw.roundRect(fill, x, y, PILL_W, PILL_H, r, Theme.PILL_ON)
            Draw.accentRamp(fill, x, y, PILL_W, PILL_H, 10)
            Draw.glow(fill, x, y, PILL_W, PILL_H, Theme.ACCENT, 0.9f, 6)
        } else {
            Draw.roundRect(fill, x, y, PILL_W, PILL_H, r, Theme.PILL_OFF)
            Draw.roundRectOutline(fill, x, y, PILL_W, PILL_H, r, Theme.BORDER_STRONG)
        }
        val knobX = if (on) x + PILL_W - PILL_H + 2 else x + 2
        val knob = if (hovered && on) Theme.KNOB else Theme.fade(Theme.KNOB, 0.95f)
        Draw.roundRect(fill, knobX, y + 2, PILL_H - 4, PILL_H - 4, r - 2, knob)
    }

    private fun drawScrollbar(context: DrawContext) {
        if (contentHeight <= viewHeight) return
        val trackX = contentRight - 4
        val trackH = viewHeight
        val barH = (trackH.toLong() * trackH / contentHeight).toInt().coerceAtLeast(24)
        val maxScroll = (contentHeight - viewHeight).coerceAtLeast(1)
        val barY = contentTop + ((trackH - barH) * (scroll / maxScroll)).toInt()
        Draw.roundRect(fill, trackX, contentTop, 4, trackH, 2, Theme.fade(Theme.DIVIDER, 0.9f))
        Draw.roundRect(fill, trackX, barY, 4, barH, 2, Theme.fade(Theme.ACCENT, 0.7f))
    }

    private fun isRowHovered(mouseX: Int, mouseY: Int, rowY: Int): Boolean =
        mouseX in contentLeft until contentRight &&
            mouseY >= rowY && mouseY < rowY + ROW_H &&
            mouseY in contentTop until contentBottom

    private fun clampScroll() {
        val maxScroll = (contentHeight - viewHeight).coerceAtLeast(0).toDouble()
        if (scroll > maxScroll) scroll = maxScroll
        if (scroll < 0) scroll = 0.0
    }

    private fun localized(key: String, fallback: String): String {
        val value = Lang.t(key)
        return if (value == key) fallback else value
    }

    private fun createHudContext(context: DrawContext): RenderContext {
        val renderer = MinecraftClient.getInstance().textRenderer
        return RenderContext(
            drawText = { text, x, y, color ->
                context.drawTextWithShadow(renderer, text, x.toInt(), y.toInt(), color)
            },
            drawRect = { x, y, w, h, color ->
                context.fill(x.toInt(), y.toInt(), (x + w).toInt(), (y + h).toInt(), color)
            },
            textWidth = { text -> renderer.getWidth(text).toFloat() },
            lineHeight = (renderer.fontHeight + 1).toFloat()
        )
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (tab == Tab.MODULES && searching) {
            if (chr == '\b'.code.toChar() && searchQuery.isNotEmpty()) {
                searchQuery = searchQuery.dropLast(1)
            } else if (chr >= ' ') {
                searchQuery += chr
            }
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (tab == Tab.HUD && button == 0) {
            val ctx = hudContext
            if (ctx != null && hudEditor.mouseClicked(mouseX, mouseY, ctx)) return true
        }

        if (tab == Tab.MODULES) {
            val searchW = contentRight - contentLeft - 12
            val searchH = 22
            val searchX = contentLeft + 6
            val searchY = contentTop + 4 - scroll.toInt()
            if (mouseX >= searchX && mouseX < searchX + searchW && mouseY >= searchY && mouseY < searchY + searchH) {
                searching = true
                return true
            } else {
                searching = false
            }
        }

        if (mouseX >= railLeft && mouseX < railLeft + railWidth &&
            mouseY >= railTop && mouseY < railBottom
        ) {
            var y = railTop + 8
            for (item in railItems) {
                if (item.groupStart) y += 12
                if (mouseY >= y && mouseY < y + RAIL_ROW_H) {
                    if (tab != item.tab || category != item.category) {
                        tab = item.tab
                        category = item.category
                        scroll = 0.0
                    }
                    return true
                }
                y += RAIL_ROW_H
            }
            return true
        }

        if (mouseX < contentLeft || mouseX >= contentRight) return false
        if (mouseY < contentTop || mouseY >= contentBottom) return false

        val index = hoverRow
        if (index < 0) return false
        if (button != 0 && button != 1) return false

        when (tab) {
            Tab.MODULES -> {
                if (button != 0) return false
                val module = filteredModules().getOrNull(index) ?: return false
                if (hoverToggle) {
                    module.toggle()
                    ConfigManager.requestSave()
                } else {
                    selected = module
                }
                return true
            }
            Tab.FEATURES -> return clickFeature(index, button)
            Tab.BINDS -> return clickBind(index, button)
            Tab.BOOST -> {
                if (button != 0) return false
                if (index == 0) {
                    val config = ConfigManager.current
                    config.fpsBoost = !config.fpsBoost
                    ConfigManager.requestSave()
                }
                return true
            }
            Tab.MODS -> {
                if (button != 0) return false
                if (index == 0) client?.setScreen(InstalledModsScreen(this))
                if (index == 1) client?.setScreen(ModStoreScreen(this))
                return true
            }
            Tab.PLUGINS -> {
                if (button != 0) return false
                client?.setScreen(PluginScreen(this))
                return true
            }
            Tab.SETTINGS -> {
                if (button != 0) return false
                if (index == 0) {
                    Lang.toggle()
                    return true
                }
                if (index == 1) {
                    ModuleManager.togglePvpPreset()
                    ConfigManager.requestSave()
                    return true
                }
            }
            else -> {}
        }
        return false
    }

    private fun clickBind(index: Int, button: Int): Boolean {
        if (button == 1) {
            if (index >= BIND_BASE_INDEX && index < SUGGEST_BASE_INDEX) {
                BindManager.removeAt(index - BIND_BASE_INDEX)
                ToastManager.show("Bind removed")
            }
            return true
        }
        if (button != 0) return false

        if (index >= SUGGEST_BASE_INDEX) {
            val suggestionIndex = index - SUGGEST_BASE_INDEX
            val suggestion = BindManager.suggestions().getOrNull(suggestionIndex) ?: return false
            BindManager.add(suggestion)
            ToastManager.show("Added: " + suggestion.describe())
            return true
        }

        if (index >= BIND_BASE_INDEX) {
            val bindIndex = index - BIND_BASE_INDEX
            val bind = BindManager.all.getOrNull(bindIndex) ?: return false
            BindManager.setEnabled(bindIndex, !bind.enabled)
            return true
        }
        return false
    }

    private fun clickFeature(index: Int, button: Int): Boolean {
        val config = ConfigManager.current
        if (index == 10 && button == 1) {
            val steps = floatArrayOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)
            var at = -1
            for (i in steps.indices) {
                if (steps[i] == config.motionBlurStrength) at = i
            }
            config.motionBlurStrength = if (at < 0 || at + 1 >= steps.size) steps[0] else steps[at + 1]
            if (!config.motionBlur) config.motionBlur = true
            ConfigManager.requestSave()
            ToastManager.show("Motion Blur " + (config.motionBlurStrength * 100).toInt() + "%")
            return true
        }
        if (button != 0) return false
        when (index) {
            0 -> config.smoothSneak = !config.smoothSneak
            1 -> config.freeLook = !config.freeLook
            2 -> config.fullbright = !config.fullbright
            3 -> config.clearWeather = !config.clearWeather
            4 -> config.noHurtCam = !config.noHurtCam
            5 -> config.noViewBob = !config.noViewBob
            6 -> config.hideHand = !config.hideHand
            7 -> config.customCrosshair = !config.customCrosshair
            8 -> config.blockOverlay = !config.blockOverlay
            9 -> config.chunkBorders = !config.chunkBorders
            10 -> {
                config.motionBlur = !config.motionBlur
                if (config.motionBlur) ToastManager.show("Motion Blur: cycle strength with RMB")
            }
            11 -> config.hitMarker = !config.hitMarker
            12 -> config.killSounds = !config.killSounds
            13 -> config.hitColor = !config.hitColor
            14 -> config.customMainMenu = !config.customMainMenu
            15 -> ConfigManager.setLowFpsProtection(!config.lowFpsProtection)
            16 -> ModuleManager.togglePvpPreset()
            17 -> {
                val cosmetics = com.vibe.client.cosmetics.CosmeticsManager
                val ids = cosmetics.catalogue.map { it.id }
                val current = cosmetics.equippedIdOrNull()
                val next = when (current) {
                    null -> ids.first()
                    else -> {
                        val at = ids.indexOf(current)
                        if (at < 0 || at + 1 >= ids.size) null else ids[at + 1]
                    }
                }
                cosmetics.equip(next)
            }
            else -> return false
        }
        ConfigManager.requestSave()
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dx: Double, dy: Double): Boolean {
        if (tab == Tab.HUD && button == 0 && hudEditor.isDragging) {
            hudEditor.mouseDragged(mouseX, mouseY)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (tab == Tab.HUD && button == 0) hudEditor.mouseReleased()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= contentLeft && mouseX < contentRight &&
            mouseY >= contentTop && mouseY < contentBottom
        ) {
            scroll -= verticalAmount * 18
            clampScroll()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun shouldPause(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchQuery.isNotEmpty()) {
                searchQuery = searchQuery.dropLast(1)
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searching = false
                return true
            }
        }

        val isMenuKey = keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT ||
            keyCode == GLFW.GLFW_KEY_LEFT_SHIFT ||
            keyCode == GLFW.GLFW_KEY_M ||
            keyCode == GLFW.GLFW_KEY_N

        if (isMenuKey || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        if (tab == Tab.HUD && (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            close()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun close() {
        animated = false
        ConfigManager.flush()
        client?.setScreen(parent)
    }

    private companion object {
        const val ROW_H = 28
        const val RAIL_ROW_H = 26
        const val CARD_H = 34
        const val PILL_W = 32
        const val PILL_H = 15
        const val OPEN_MS = 240f

        const val ICON_SM = 12
        const val ICON_MD = 16

        const val BIND_BASE_INDEX = 1000
        const val SUGGEST_BASE_INDEX = 100000
    }
}

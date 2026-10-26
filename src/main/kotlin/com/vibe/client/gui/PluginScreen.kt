package com.vibe.client.gui

import com.vibe.client.lang.Lang
import com.vibe.client.plugin.PluginManager
import com.vibe.client.ui.ToastManager
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * "Plugins": everything discovered in plugins/, with state and a reason.
 *
 * The list is read from [PluginManager] on init and refreshed after every
 * action. Enable/disable is instant (no restart): the plugin gets its
 * callbacks and its HUD lines disappear immediately.
 */
class PluginScreen(private val parent: Screen?) : Screen(Text.literal("Plugins")) {

    private var entries: List<PluginManager.LoadedPlugin> = emptyList()

    override fun init() {
        refresh()

        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("mods.refresh"))) {
                clearAndInit()
            }.dimensions(width / 2 - 130, height - 28, 100, 20).build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("plugins.folder"))) {
                openPluginsFolder()
            }.dimensions(width / 2 - 20, height - 28, 140, 20).build()
        )

        // First-run users have no idea what a plugin is or where to start.
        // A written guide inside the game beats a README nobody opens.
        addDrawableChild(
            ButtonWidget.builder(Text.literal("How to make one")) {
                client?.setScreen(PluginHelpScreen(this))
            }.dimensions(width / 2 + 126, height - 28, 130, 20).build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("menu.done"))) { close() }
                .dimensions(width - 70, height - 28, 60, 20).build()
        )
    }

    private fun refresh() {
        entries = PluginManager.all.sortedBy { it.name.lowercase() }
    }

    private fun openPluginsFolder() {
        // The launcher's "Open mods folder" covers the game dir; here we only
        // tell the user where plugins live, because a mod cannot open Explorer.
        ToastManager.show(Lang.t("plugins.folder_hint"))
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF)

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer, Lang.t("plugins.none"), width / 2, height / 2 - 10, 0xAAAAAA
            )
            context.drawCenteredTextWithShadow(
                textRenderer, Lang.t("plugins.none_hint"), width / 2, height / 2 + 6, 0x888888
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        val listTop = 40
        val maxRows = ((height - listTop - 44) / ROW_HEIGHT).coerceAtLeast(1)

        entries.take(maxRows).forEachIndexed { index, plugin ->
            val y = listTop + index * ROW_HEIGHT

            val (stateText, stateColor) = when (plugin.state) {
                PluginManager.State.ENABLED -> Lang.t("menu.on") to 0x55FF55
                PluginManager.State.DISABLED -> Lang.t("menu.off") to 0xFFAA00
                PluginManager.State.BROKEN -> Lang.t("plugins.broken") to 0xFF5555
                PluginManager.State.UNSUPPORTED -> Lang.t("plugins.unsupported") to 0xFF5555
            }

            context.drawTextWithShadow(textRenderer, plugin.name, 20, y + 1, 0xFFFFFF)
            context.drawTextWithShadow(
                textRenderer,
                "v${plugin.manifest?.version ?: "?"} \u2022 ${plugin.id}",
                20, y + 11, 0x888888
            )

            val detail = plugin.reason
                ?: plugin.manifest?.let { m ->
                    if (m.permissions.isEmpty()) Lang.t("plugins.no_perms")
                    else m.permissions.joinToString(", ") { it.id }
                } ?: ""
            if (detail.isNotEmpty()) {
                context.drawTextWithShadow(
                    textRenderer, detail, 20 + 150, y + 11, 0x888888
                )
            }

            context.drawTextWithShadow(textRenderer, "[$stateText]", width - 90, y + 6, stateColor)
        }

        if (entries.size > maxRows) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                "... ${entries.size - maxRows} more",
                width / 2, height - 44, 0x888888
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        val listTop = 40
        val maxRows = ((height - listTop - 44) / ROW_HEIGHT).coerceAtLeast(1)
        val index = ((mouseY - listTop) / ROW_HEIGHT).toInt()

        if (index in 0 until minOf(entries.size, maxRows) && mouseX >= width - 110) {
            val plugin = entries[index]
            when (plugin.state) {
                PluginManager.State.ENABLED -> {
                    PluginManager.disable(plugin)
                    ToastManager.show("${plugin.name}: ${Lang.t("menu.off")}")
                }
                PluginManager.State.DISABLED -> {
                    val ok = PluginManager.enable(plugin)
                    ToastManager.show(
                        "${plugin.name}: " +
                            if (ok) Lang.t("menu.on") else Lang.t("plugins.enable_failed")
                    )
                }
                PluginManager.State.BROKEN,
                PluginManager.State.UNSUPPORTED -> {
                    ToastManager.show(plugin.reason ?: Lang.t("plugins.broken"))
                }
            }
            refresh()
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    private companion object {
        const val ROW_HEIGHT = 26
    }
}

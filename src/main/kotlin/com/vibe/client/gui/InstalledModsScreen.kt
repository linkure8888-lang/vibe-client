package com.vibe.client.gui

import com.vibe.client.lang.Lang
import com.vibe.client.store.ModsManager
import com.vibe.client.ui.ToastManager
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * "My mods": the real contents of the game's mods/ folder.
 *
 * Toggling uses the standard Fabric trick — rename to `*.jar.disabled` — which
 * means a restart is required. The screen says so instead of pretending.
 */
class InstalledModsScreen(private val parent: Screen?) : Screen(Text.literal("Installed Mods")) {

    private var mods: List<ModsManager.InstalledMod> = emptyList()

    override fun init() {
        mods = ModsManager.list()

        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("mods.refresh"))) {
                clearAndInit()
            }.dimensions(width / 2 - 60, height - 28, 120, 20).build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("menu.done"))) { close() }
                .dimensions(width - 70, height - 28, 60, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF)

        if (mods.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer, Lang.t("mods.no_mods"), width / 2, height / 2, 0xAAAAAA
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        val rowHeight = 24
        val listTop = 40
        val maxRows = (height - listTop - 44) / rowHeight

        mods.take(maxRows).forEachIndexed { index, mod ->
            val y = listTop + index * rowHeight

            val color = if (mod.enabled) 0x55FF55 else 0xFF5555
            val state = if (mod.enabled) Lang.t("menu.on") else Lang.t("menu.off")

            context.drawTextWithShadow(textRenderer, mod.displayName, 20, y + 2, color)
            context.drawTextWithShadow(
                textRenderer,
                "${mod.sizeBytes / 1024} KB",
                20, y + 12, 0x888888
            )
            context.drawTextWithShadow(textRenderer, "[$state]", width - 100, y + 6, color)

            // Click target is the row's right side; see mouseClicked.
        }

        if (mods.size > maxRows) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                "... ${mods.size - maxRows} more (window too small)",
                width / 2, height - 44, 0x888888
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)

        val rowHeight = 24
        val listTop = 40
        val maxRows = (height - listTop - 44) / rowHeight
        val index = ((mouseY - listTop) / rowHeight).toInt()

        if (index in 0 until minOf(mods.size, maxRows) && mouseX >= width - 120) {
            val mod = mods[index]
            if (ModsManager.setEnabled(mod, !mod.enabled)) {
                ToastManager.show(
                    "${mod.displayName}: " +
                        if (!mod.enabled) Lang.t("menu.on") else Lang.t("menu.off")
                )
                mods = ModsManager.list()
            }
            return true
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun close() {
        client?.setScreen(parent)
    }
}

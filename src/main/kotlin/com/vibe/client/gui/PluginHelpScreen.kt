package com.vibe.client.gui

import com.vibe.client.ui.Theme
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * In-game guide: how to write a Vibe Client plugin.
 *
 * This exists because "drop a jar in plugins/" answers nothing for someone who
 * has never built one. The screen is scrollable and covers both plugin kinds:
 *
 *  - **declarative** — a JSON file that draws HUD lines, no code, no compiler;
 *  - **code** — a jar with a class implementing the Plugin interface.
 *
 * Written as plain scrolling text on purpose. Screenshots or a diagram would
 * go stale the moment the API changes; text is what actually stays true.
 */
class PluginHelpScreen(private val parent: Screen?) : Screen(Text.literal("Plugin guide")) {

    private var scroll = 0
    private var contentHeight = 0

    override fun init() {
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Back")) { close() }
                .dimensions(width - 70, height - 28, 60, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        val top = 34
        val bottom = height - 34

        context.enableScissor(0, top, width, bottom)
        var y = top - scroll

        for (block in BLOCKS) {
            when (block) {
                is Block.Heading -> {
                    y += 4
                    context.drawTextWithShadow(textRenderer, block.text, 24, y, Theme.ACCENT)
                    y += 14
                }
                is Block.Body -> {
                    for (line in wrap(block.text, width - 48)) {
                        context.drawTextWithShadow(textRenderer, line, 24, y, Theme.TEXT_MUTED)
                        y += 11
                    }
                    y += 2
                }
                is Block.Code -> {
                    // Code gets its own plate, indented and slightly brighter,
                    // so it is obvious which parts are meant to be typed.
                    val lines = block.lines
                    val plateHeight = lines.size * 10 + 8
                    context.fill(20, y - 2, width - 20, y + plateHeight - 2, 0x66000000)
                    context.fill(20, y - 2, 22, y + plateHeight - 2, Theme.ACCENT)
                    for (line in lines) {
                        context.drawTextWithShadow(textRenderer, line, 28, y + 2, Theme.TEXT)
                        y += 10
                    }
                    y += 8
                }
            }
        }

        contentHeight = (y + scroll) - top + 16
        context.disableScissor()

        // Header and scrollbar.
        context.fill(0, 0, width, 28, 0xCC0E1014.toInt())
        context.drawTextWithShadow(textRenderer, title, 12, 10, Theme.TEXT)

        if (contentHeight > bottom - top) {
            val trackH = bottom - top
            val barH = (trackH.toLong() * trackH / contentHeight).toInt().coerceAtLeast(16)
            val maxScroll = (contentHeight - trackH).coerceAtLeast(1)
            val barY = top + ((trackH - barH) * (scroll.toFloat() / maxScroll)).toInt()
            context.fill(width - 5, top, width - 2, bottom, 0x18FFFFFF)
            context.fill(width - 5, barY, width - 2, barY + barH, Theme.ACCENT)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double,
                               verticalAmount: Double): Boolean {
        scroll = (scroll - (verticalAmount * 16).toInt())
            .coerceIn(0, (contentHeight - (height - 68)).coerceAtLeast(0))
        return true
    }

    /**
     * Greedy word wrap against the live font metrics.
     *
     * Done with the game's own text renderer rather than a fixed column count:
     * the font is variable-width, so counting characters produces ragged text.
     */
    private fun wrap(text: String, maxWidth: Int): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (textRenderer.getWidth(candidate) <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    override fun close() {
        client?.setScreen(parent)
    }

    // -- Content -----------------------------------------------------------

    private sealed interface Block {
        data class Heading(val text: String) : Block
        data class Body(val text: String) : Block
        data class Code(val lines: List<String>) : Block
    }

    private companion object {

        /**
         * The guide itself. Kept as data rather than a hundred `drawText`
         * calls so the structure stays readable and easy to extend.
         */
        val BLOCKS: List<Block> = listOf(
            Block.Heading("What a plugin is"),
            Block.Body(
                "A plugin is a small add-on that draws HUD lines, keeps its own " +
                    "settings, or fetches data from the internet. It cannot touch " +
                    "your game files or the Minecraft client — everything it does " +
                    "goes through a checked tool call, so a broken plugin can " +
                    "never crash the game."
            ),

            Block.Heading("Where plugins live"),
            Block.Body(
                "Open your game folder (the launcher has a button for it) and " +
                    "find the 'plugins' folder. Put your plugin there, then press " +
                    "Refresh in the Plugins tab. No restart needed."
            ),
            Block.Code(listOf("<game>/plugins/", "    my-plugin/", "        vibe-plugin.json", "        plugin.jar")),

            Block.Heading("Easiest way: no code at all"),
            Block.Body(
                "Make a folder inside plugins, add a single JSON file, and you " +
                    "already have a working plugin that draws text on the HUD."
            ),
            Block.Code(
                listOf(
                    "{",
                    "  \"id\": \"my-plugin\",",
                    "  \"name\": \"My Plugin\",",
                    "  \"version\": \"1.0.0\",",
                    "  \"apiVersion\": 1,",
                    "  \"permissions\": [\"hud\"],",
                    "  \"lines\": [",
                    "    { \"id\": \"hi\", \"text\": \"Hello!\",",
                    "      \"x\": 4, \"y\": 320, \"color\": -1 }",
                    "  ]",
                    "}"
                )
            ),
            Block.Body(
                "Save it as vibe-plugin.json inside your plugin folder. The " +
                    "'id' must be lowercase letters, numbers, dashes or " +
                    "underscores. Color is a number: -1 is white."
            ),

            Block.Heading("With code: a jar plugin"),
            Block.Body(
                "Write a class that implements com.vibe.client.plugin.Plugin, " +
                    "compile it against the client jar, and put the jar in your " +
                    "plugin folder together with a manifest that has \"mainClass\"."
            ),
            Block.Code(
                listOf(
                    "public class MyPlugin implements Plugin {",
                    "  public void onEnable(PluginHost host) {",
                    "    JsonObject a = new JsonObject();",
                    "    a.addProperty(\"id\", \"hi\");",
                    "    a.addProperty(\"text\", \"Hello!\");",
                    "    host.call(\"hud.addLine\", a);",
                    "  }",
                    "  public void onDisable(PluginHost host) {",
                    "    // always undo what you added",
                    "  }",
                    "}"
                )
            ),
            Block.Body(
                "A worked example ships with the client in the " +
                    "'plugins-examples' folder, including a build script."
            ),

            Block.Heading("Tools you can call"),
            Block.Body("hud.addLine / hud.setLine / hud.removeLine — draw HUD text."),
            Block.Body("config.get / config.set — store your own settings."),
            Block.Body("net.get — one HTTP request. Limited to 300 per day."),
            Block.Body("fs.read / fs.write — your own data folder only."),
            Block.Body(
                "Every tool needs the matching permission in your manifest: " +
                    "\"hud\", \"config\", \"network\", \"filesystem\". " +
                    "Without the permission the call returns an error instead of " +
                    "running."
            ),

            Block.Heading("If something breaks"),
            Block.Body(
                "The Plugins tab shows the state of each plugin: green is " +
                    "enabled, orange is disabled by you, red is broken. Click a " +
                    "red one to see the reason. A plugin that fails three times is " +
                    "switched off automatically to protect the game."
            )
        )
    }
}

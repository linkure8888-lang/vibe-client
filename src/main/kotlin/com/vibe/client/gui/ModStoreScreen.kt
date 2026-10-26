package com.vibe.client.gui

import com.vibe.client.lang.Lang
import com.vibe.client.store.ModrinthApi
import com.vibe.client.store.ModsManager
import com.vibe.client.ui.ToastManager
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mod catalog backed by the Modrinth public API.
 *
 * Threading model:
 *  - the render thread NEVER blocks on the network;
 *  - a CompletableFuture runs the request, and its result is parked in
 *    [pendingResult]; the render loop picks it up on the next frame.
 *
 * Paging is server-side: the screen asks Modrinth for [PAGE_SIZE] rows at
 * `offset = page * PAGE_SIZE`, so [projects] always holds exactly the
 * current page. Responses are cached (see StoreCache), so flipping back to
 * a page already seen costs no network at all.
 */
class ModStoreScreen(private val parent: Screen?) : Screen(Text.literal("Mod Store")) {

    private enum class Phase { IDLE, LOADING, READY, EMPTY, ERROR }

    private var phase = Phase.IDLE
    private var projects: List<ModrinthApi.Project> = emptyList()
    private var page = 0

    /** True when the last page came back full, i.e. a next page may exist. */
    private var hasNextPage = false

    private val busy = AtomicBoolean(false)

    @Volatile private var pendingResult: List<ModrinthApi.Project>? = null

    @Volatile private var downloading: String? = null

    private val mcVersion: String get() = com.vibe.client.VibeClient.mcVersion

    override fun init() {
        if (phase == Phase.IDLE) load()

        val bottom = height - 28
        addDrawableChild(
            ButtonWidget.builder(Text.literal("<")) { prevPage() }
                .dimensions(width / 2 - 120, bottom, 30, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("mods.refresh"))) { refresh() }
                .dimensions(width / 2 - 80, bottom, 160, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal(">")) { nextPage() }
                .dimensions(width / 2 + 90, bottom, 30, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal(Lang.t("menu.done"))) { close() }
                .dimensions(width - 70, bottom, 60, 20).build()
        )
    }

    private fun load() {
        if (!busy.compareAndSet(false, true)) return
        phase = Phase.LOADING
        projects = emptyList()

        val offset = page * PAGE_SIZE
        CompletableFuture.supplyAsync {
            ModrinthApi.search("", mcVersion, PAGE_SIZE, offset)
        }.whenComplete { result, error ->
            busy.set(false)
            if (error != null) {
                phase = Phase.ERROR
            } else {
                val list = result ?: emptyList()
                pendingResult = list
                hasNextPage = list.size >= PAGE_SIZE
                phase = if (list.isEmpty()) Phase.EMPTY else Phase.READY
            }
        }
    }

    /** Refresh keeps the current page but bypasses nothing — cache TTL handles it. */
    private fun refresh() {
        if (page != 0) page = 0
        load()
    }

    private fun prevPage() {
        if (page > 0) {
            page--
            load()
        }
    }

    private fun nextPage() {
        if (!hasNextPage) return
        page++
        load()
    }

    private fun download(project: ModrinthApi.Project) {
        if (downloading != null) return
        val dir = ModsManager.directory ?: return
        downloading = project.slug

        CompletableFuture.supplyAsync {
            val file = ModrinthApi.findFile(project.slug, mcVersion)
            if (file == null) return@supplyAsync null
            if (ModsManager.isInstalled(file.fileName)) return@supplyAsync file.fileName
            val written = ModrinthApi.download(file, dir)
            written?.name
        }.whenComplete { fileName, error ->
            downloading = null
            if (fileName != null) {
                ToastManager.show(Lang.t("toast.mod_installed").format(fileName))
            } else {
                ToastManager.show(Lang.t("mods.download_failed").format(project.title))
            }
        }
    }

    private fun visibleRows(): Int = ((height - LIST_TOP - 40) / ROW_HEIGHT).coerceAtLeast(1)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF)

        // Pick up a finished request without blocking.
        pendingResult?.let {
            projects = it
            pendingResult = null
        }

        when (phase) {
            Phase.IDLE, Phase.LOADING -> {
                context.drawCenteredTextWithShadow(
                    textRenderer, Lang.t("mods.loading"), width / 2, height / 2, 0xAAAAAA
                )
            }
            Phase.EMPTY -> {
                context.drawCenteredTextWithShadow(
                    textRenderer, Lang.t("mods.no_mods"), width / 2, height / 2, 0xAAAAAA
                )
            }
            Phase.ERROR -> {
                context.drawCenteredTextWithShadow(
                    textRenderer, "Network error — press Refresh", width / 2, height / 2, 0xFF5555
                )
            }
            Phase.READY -> {
                val slice = projects.take(visibleRows())
                slice.forEachIndexed { index, project ->
                    val y = LIST_TOP + index * ROW_HEIGHT

                    context.drawTextWithShadow(
                        textRenderer, project.title, 20, y + 2, 0xFFFFFF
                    )
                    val desc = if (project.description.length > 60)
                        project.description.take(57) + "..." else project.description
                    context.drawTextWithShadow(
                        textRenderer, desc, 20, y + 13, 0xAAAAAA
                    )
                    context.drawTextWithShadow(
                        textRenderer, "${project.downloads} DL", width - 160, y + 2, 0xFFFF55
                    )

                    val label = when {
                        downloading == project.slug -> Lang.t("mods.downloading")
                        else -> Lang.t("mods.download")
                    }
                    // A lightweight clickable row: handled in mouseClicked.
                    context.drawTextWithShadow(
                        textRenderer, "[$label]", width - 90, y + 13, 0x55FF55
                    )
                }

                context.drawCenteredTextWithShadow(
                    textRenderer, "MC $mcVersion • Fabric • page ${page + 1}",
                    width / 2, height - 44, 0x888888
                )
            }
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (phase == Phase.READY && button == 0 && mouseX >= width - 170) {
            val slice = projects.take(visibleRows())
            val index = ((mouseY - LIST_TOP) / ROW_HEIGHT).toInt()
            if (index in slice.indices && mouseY >= LIST_TOP) {
                download(slice[index])
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val LIST_TOP = 44
        const val ROW_HEIGHT = 32
    }
}
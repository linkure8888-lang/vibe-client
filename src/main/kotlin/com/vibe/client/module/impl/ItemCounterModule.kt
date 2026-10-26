package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient
import net.minecraft.item.Items

object ItemCounterModule : Module("Items", Category.MISC, enabled = false, x = 4f, y = 16f) {
    fun update(arrows: Int, gaps: Int, pearls: Int) {}

    override fun getLines(): List<String> {
        val inv = MinecraftClient.getInstance().player?.inventory ?: return emptyList()
        var arrows = 0; var gaps = 0; var pearls = 0
        for (i in 0 until inv.size()) {
            val stack = inv.getStack(i)
            when (stack.item) {
                Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW -> arrows += stack.count
                Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE -> gaps += stack.count
                Items.ENDER_PEARL -> pearls += stack.count
            }
        }
        return listOf("↑$arrows  ★$gaps  ◎$pearls")
    }
}

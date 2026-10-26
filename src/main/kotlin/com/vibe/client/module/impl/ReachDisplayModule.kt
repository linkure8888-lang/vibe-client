package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object ReachDisplayModule : Module("Reach", Category.COMBAT, enabled = false, x = 4f, y = 40f) {
    override fun getLines(): List<String> {
        val mc = MinecraftClient.getInstance()
        val hit = mc.crosshairTarget ?: return listOf("Reach: --")
        val p = mc.player ?: return listOf("Reach: --")
        val dist = p.eyePos.distanceTo(hit.pos)
        return listOf("Reach: ${"%.2f".format(dist)}m")
    }
}

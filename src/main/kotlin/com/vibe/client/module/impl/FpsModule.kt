package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object FpsModule : Module("FPS", Category.HUD, enabled = true, x = 4f, y = 4f) {
    override val inPvpPreset = true
    override fun getLines(): List<String> {
        val fps = MinecraftClient.getInstance().currentFps
        return listOf("FPS: $fps")
    }
}

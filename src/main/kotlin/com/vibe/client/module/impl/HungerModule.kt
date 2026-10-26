package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object HungerModule : Module("Hunger", Category.HUD, enabled = false, x = 4f, y = 80f) {
    private var food = 20
    private var saturation = 5f
    fun update(f: Int, s: Float) {
        food = f
        saturation = s
    }
    override fun getLines(): List<String> {
        val sat = "%.1f".format(saturation)
        return listOf("Food: $food  Sat: $sat")
    }
}

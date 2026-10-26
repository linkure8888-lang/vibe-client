package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient
import net.minecraft.registry.RegistryKeys

object BiomeModule : Module("Biome", Category.WORLD, enabled = false, x = 4f, y = 140f) {
    private var biomeName = "unknown"
    fun setBiome(b: String) { biomeName = b }
    override fun getLines(): List<String> {
        return listOf("Biome: $biomeName")
    }
}

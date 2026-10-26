package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object ServerAddressModule : Module("Server Address", Category.WORLD, enabled = false, x = 4f, y = 28f) {
    private var address = "Singleplayer"
    fun setAddress(addr: String) { address = addr }
    override fun getLines(): List<String> {
        return listOf("Server: $address")
    }
}

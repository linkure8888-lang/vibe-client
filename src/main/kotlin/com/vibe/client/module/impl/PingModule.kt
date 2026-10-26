package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object PingModule : Module("Ping", Category.MISC, enabled = true, x = 4f, y = 92f) {
    override val inPvpPreset = true
    var ping: Int = 0
    var pingMs: Int get() = ping; set(v) { ping = v }
    override fun getLines() = listOf("Ping: ${ping}ms")
}

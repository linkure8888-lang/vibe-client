package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient
import kotlin.math.sqrt

object SpeedModule : Module("Speed", Category.MOVEMENT, enabled = false, x = 4f, y = 4f) {
    override val inPvpPreset = true
    override fun getLines(): List<String> {
        val p = MinecraftClient.getInstance().player ?: return emptyList()
        val bps = sqrt(p.velocity.x * p.velocity.x + p.velocity.z * p.velocity.z) * 20.0
        return listOf("Speed: ${"%.2f".format(bps)} bps")
    }
    fun tick(x: Double, z: Double) {}
    fun reset() {}
}

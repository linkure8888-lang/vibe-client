package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object DirectionModule : Module("Direction", Category.HUD, enabled = false, x = 4f, y = 128f) {
    private var yaw = 0f
    fun setYaw(y: Float) { yaw = y }
    override fun getLines(): List<String> {
        val y = ((yaw % 360f) + 360f) % 360f
        val dir = when {
            y < 22.5f || y >= 337.5f -> "S"
            y < 67.5f -> "SW"
            y < 112.5f -> "W"
            y < 157.5f -> "NW"
            y < 202.5f -> "N"
            y < 247.5f -> "NE"
            y < 292.5f -> "E"
            else -> "SE"
        }
        return listOf("Dir: $dir  Yaw: ${"%.1f".format(y)}")
    }
}

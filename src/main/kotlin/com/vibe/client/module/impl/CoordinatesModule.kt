package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object CoordinatesModule : Module("Coordinates", Category.HUD, enabled = true, x = 4f, y = 30f) {
    override val inPvpPreset = false
    var xPos = 0.0
    var yPos = 0.0
    var zPos = 0.0
    var facing = "N"

    override fun getLines(): List<String> {
        val x = "%.1f".format(xPos)
        val y = "%.1f".format(yPos)
        val z = "%.1f".format(zPos)
        return listOf("XYZ: $x / $y / $z")
    }
}

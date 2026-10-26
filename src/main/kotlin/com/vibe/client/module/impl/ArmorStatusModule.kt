package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object ArmorStatusModule : Module("Armor Status", Category.HUD, enabled = false, x = 4f, y = 164f) {
    private val armorLines = arrayOf("--", "--", "--", "--", "--")
    fun setArmor(slot: Int, desc: String) {
        if (slot in armorLines.indices) armorLines[slot] = desc
    }
    override fun getLines(): List<String> {
        return armorLines.filter { it != "--" }
    }
}

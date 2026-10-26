package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object PotionModule : Module("Potion Effects", Category.HUD, enabled = false, x = 4f, y = 200f) {
    override fun getLines(): List<String> {
        val p = MinecraftClient.getInstance().player ?: return emptyList()
        return p.statusEffects.map { e ->
            val name = e.effectType.value().name.string
            val amp = if (e.amplifier > 0) " ${e.amplifier + 1}" else ""
            val ticks = e.duration
            val time = if (ticks >= 32767 * 20) "inf" else { val s = ticks / 20; "%d:%02d".format(s / 60, s % 60) }
            "$name$amp  $time"
        }
    }
}

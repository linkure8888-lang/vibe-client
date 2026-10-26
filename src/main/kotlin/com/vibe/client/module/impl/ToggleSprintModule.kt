package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import net.minecraft.client.MinecraftClient

object ToggleSprintModule : Module("Toggle Sprint", Category.MISC, enabled = true, x = 4f, y = 4f) {
    override fun getLines() = listOf("Sprint: ON")
    fun tick() {
        val p = MinecraftClient.getInstance().player ?: return
        if (enabled && !p.isSprinting && !p.isSneaking && p.forwardSpeed > 0f) p.isSprinting = true
    }
}

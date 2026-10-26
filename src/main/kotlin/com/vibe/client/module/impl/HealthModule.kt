package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module
import com.vibe.client.ui.Theme

object HealthModule : Module("Health", Category.HUD, enabled = true, x = 4f, y = 14f) {
    private var health = 20f
    private var absorption = 0f

    fun update(hp: Float, abs: Float) {
        health = hp
        absorption = abs
    }

    override fun getLines(): List<String> {
        val max = 20f
        val pct = ((health / max.coerceAtLeast(1f)) * 100f).toInt()
        return if (absorption > 0f) {
            listOf("HP: ${"%.1f".format(health)} + ${"%.1f".format(absorption)} ($pct%)")
        } else {
            listOf("HP: ${"%.1f".format(health)}/$max ($pct%)")
        }
    }

    override fun accent(): Int = Theme.SUCCESS
}

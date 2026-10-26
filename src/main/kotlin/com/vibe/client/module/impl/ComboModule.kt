package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object ComboModule : Module("Combo", Category.COMBAT, enabled = false, x = 4f, y = 52f) {
    var combo = 0
    var lastHit = 0L

    fun onHit() {
        val now = System.currentTimeMillis()
        if (now - lastHit < 3000L) combo++ else combo = 1
        lastHit = now
    }

    fun onHurt() { combo = 0 }

    override fun getLines(): List<String> {
        val now = System.currentTimeMillis()
        if (combo > 0 && now - lastHit > 3000L) combo = 0
        return if (combo > 1) listOf("Combo: $combo") else emptyList()
    }
}

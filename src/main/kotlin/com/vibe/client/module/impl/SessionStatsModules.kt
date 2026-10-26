package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object HitsPerMinModule : Module("Hits/min", Category.COMBAT, enabled = false, x = 4f, y = 4f) {
    private var rate = 0
    fun rebuild(seconds: Long) { /* kept for compat */ }
    override fun getLines() = listOf("Hits/min: $rate")
}

object DamageTakenModule : Module("Damage Taken", Category.COMBAT, enabled = false, x = 4f, y = 16f) {
    private var hp = 20f
    fun update(v: Float) { hp = v }
    override fun getLines() = listOf("HP: ${"%.1f".format(hp)}")
}

object XpGainedModule : Module("XP Gained", Category.MISC, enabled = false, x = 4f, y = 28f) {
    private var totalXp = 0
    fun update(v: Int) { totalXp = v }
    override fun getLines() = listOf("XP Total: $totalXp")
}

object MinedPerHourModule : Module("Mined/h", Category.MISC, enabled = false, x = 4f, y = 40f) {
    fun rebuild(seconds: Long) {}
    override fun getLines() = listOf("Mined/h: 0")
}

object PlacedPerHourModule : Module("Placed/h", Category.MISC, enabled = false, x = 4f, y = 52f) {
    fun rebuild(seconds: Long) {}
    override fun getLines() = listOf("Placed/h: 0")
}

object DeathsTotalModule : Module("Deaths", Category.MISC, enabled = false, x = 4f, y = 64f) {
    private var deaths = 0
    fun onDeath() { deaths++ }
    override fun getLines() = listOf("Deaths: $deaths")
}

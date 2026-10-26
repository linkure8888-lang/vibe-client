package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object TeamHpModule : Module("Team HP", Category.COMBAT, enabled = false, x = 4f, y = 4f) {
    private var rows = emptyList<String>()
    private var urgent = false
    fun update(r: List<String>, u: Boolean) { rows = r; urgent = u }
    override fun getLines() = if (rows.isNotEmpty()) rows else listOf("Team HP: None")
}

object TotemCounterModule : Module("Totems", Category.COMBAT, enabled = false, x = 4f, y = 4f) {
    private var count = 0
    fun update(c: Int) { count = c }
    override fun getLines() = listOf("Totems: $count")
}

object TntTimerModule : Module("TNT Timer", Category.COMBAT, enabled = false, x = 4f, y = 16f) {
    private var fuse = -1; private var count = 0
    fun update(f: Int, c: Int) { fuse = f; count = c }
    override fun getLines() = if (fuse >= 0) listOf("TNT: ${fuse}t ($count active)") else emptyList()
}

object MountStatsModule : Module("Mount", Category.MISC, enabled = false, x = 4f, y = 28f) {
    private var rows = emptyList<String>()
    fun update(r: List<String>) { rows = r }
    override fun getLines() = if (rows.isNotEmpty()) rows else emptyList()
}

object SpawnSafetyModule : Module("Spawn Check", Category.WORLD, enabled = false, x = 4f, y = 40f) {
    private var text = "Safe"
    fun update(blockL: Int, skyL: Int, isDay: Boolean) {
        text = if (blockL >= 8) "Safe (Block L$blockL)" else "Unsafe (Block L$blockL)"
    }
    override fun getLines() = listOf("Spawn: $text")
}

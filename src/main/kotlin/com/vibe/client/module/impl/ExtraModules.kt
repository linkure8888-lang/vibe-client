package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

// --- FPS Stats ---
object FpsStatsModule : Module("FPS Stats", Category.MISC, enabled = false, x = 4f, y = 4f) {
    private var minFps = 0; private var maxFps = 0; private var avgFps = 0
    private val buf = ArrayDeque<Int>(300)
    fun sample(fps: Int) { buf.addLast(fps); if (buf.size > 300) buf.removeFirst() }
    fun rebuild() {
        if (buf.isEmpty()) return
        minFps = buf.min(); maxFps = buf.max(); avgFps = buf.average().toInt()
    }
    override fun getLines() = if (avgFps > 0) listOf("FPS: min$minFps avg$avgFps max$maxFps") else emptyList()
}

// --- Ping Jitter ---
object PingJitterModule : Module("Ping Jitter", Category.MISC, enabled = false, x = 4f, y = 16f) {
    private var jitter = 0f; private var last = -1
    fun sample(ms: Int) {
        if (last >= 0) jitter = jitter * 0.8f + kotlin.math.abs(ms - last) * 0.2f
        last = ms
    }
    override fun getLines() = if (last >= 0) listOf("Jitter: ${"%.1f".format(jitter)}ms") else emptyList()
}

// --- Inventory Space ---
object InventorySpaceModule : Module("Inventory", Category.MISC, enabled = false, x = 4f, y = 28f) {
    private var free = 0
    fun update(count: Int) { free = count }
    override fun getLines() = listOf("Inv: $free/36 free")
}

// --- Entity Count ---
object EntityCountModule : Module("Entities", Category.WORLD, enabled = false, x = 4f, y = 40f) {
    private var count = 0
    fun update(n: Int) { count = n }
    override fun getLines() = listOf("Entities: $count")
}

// --- Playtime ---
object PlaytimeModule : Module("Playtime", Category.MISC, enabled = false, x = 4f, y = 52f) {
    private var elapsedSec = 0L
    val seconds: Long get() = elapsedSec
    fun tick() { elapsedSec++ }
    fun rebuild() { /* no-op, kept for API compat */ }
    override fun getLines(): List<String> {
        val h = elapsedSec / 3600L; val m = (elapsedSec % 3600L) / 60L; val s = elapsedSec % 60L
        return listOf("Played: %02d:%02d:%02d".format(h, m, s))
    }
}

// --- Death Coords ---
object DeathCoordsModule : Module("Death Coords", Category.MISC, enabled = false, x = 4f, y = 64f) {
    private var text = "None"
    fun record(x: Double, y: Double, z: Double) { text = "${"%.0f".format(x)}, ${"%.0f".format(y)}, ${"%.0f".format(z)}" }
    override fun getLines() = listOf("Death: $text")
}

// --- Durability Warn ---
object DurabilityWarnModule : Module("Durability", Category.MISC, enabled = false, x = 4f, y = 76f) {
    private var line = ""
    fun update(percent: Int, name: String) {
        line = if (percent in 0..14) "Dur: $name $percent%" else ""
    }
    override fun getLines() = if (line.isNotEmpty()) listOf(line) else emptyList()
}

// --- Strongest Effect ---
object StrongestEffectModule : Module("Strongest Effect", Category.MISC, enabled = false, x = 4f, y = 88f) {
    private var line = ""
    fun update(text: String) { line = text }
    override fun getLines() = if (line.isNotEmpty() && line != "none") listOf("Effect: $line") else emptyList()
}

// --- XP to Next ---
object XpToNextModule : Module("XP To Next", Category.WORLD, enabled = false, x = 4f, y = 100f) {
    private var level = 0; private var progress = 0f
    fun update(lvl: Int, prog: Float) { level = lvl; progress = prog }
    override fun getLines(): List<String> {
        val needed = when { level < 16 -> 2*level+7; level < 31 -> 5*level-38; else -> 9*level-158 }
        return listOf("XP: ${(progress*needed).toInt()}/$needed")
    }
}

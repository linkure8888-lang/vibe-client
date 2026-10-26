package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object DimensionModule : Module("Dimension", Category.WORLD, enabled = false, x = 4f, y = 4f) {
    private var dim = "overworld"
    fun setDimension(s: String) { dim = s }
    override fun getLines() = listOf("Dim: $dim")
}

object LightLevelModule : Module("Light Level", Category.WORLD, enabled = false, x = 4f, y = 16f) {
    private var feet = 0; private var head = 0
    fun update(f: Int, h: Int) { feet = f; head = h }
    override fun getLines() = listOf("Light: Feet $feet, Head $head")
}

object ChunkPosModule : Module("Chunk Pos", Category.WORLD, enabled = false, x = 4f, y = 28f) {
    private var cx = 0; private var cz = 0; private var rx = 0; private var rz = 0
    fun update(x: Int, z: Int, offX: Int, offZ: Int) { cx = x; cz = z; rx = offX; rz = offZ }
    override fun getLines() = listOf("Chunk: $cx, $cz ($rx, $rz)")
}

object NetherCoordsModule : Module("Nether Coords", Category.WORLD, enabled = false, x = 4f, y = 40f) {
    private var text = "--"
    fun update(x: Double, z: Double, nether: Boolean) {
        text = if (nether) "Overworld: ${"%.1f".format(x * 8.0)} / ${"%.1f".format(z * 8.0)}"
        else "Nether: ${"%.1f".format(x / 8.0)} / ${"%.1f".format(z / 8.0)}"
    }
    override fun getLines() = listOf(text)
}

object XpModule : Module("XP", Category.WORLD, enabled = false, x = 4f, y = 52f) {
    private var lvl = 0; private var prog = 0f; private var total = 0
    fun update(l: Int, p: Float, t: Int) { lvl = l; prog = p; total = t }
    override fun getLines() = listOf("XP: Lv$lvl (${(prog*100).toInt()}%) Total: $total")
}

object WorldDayModule : Module("World Day", Category.WORLD, enabled = false, x = 4f, y = 64f) {
    private var day = 0L; private var timeStr = "00:00"
    fun update(timeOfDay: Long) {
        day = timeOfDay / 24000L
        val t = timeOfDay % 24000L
        val h = ((t + 6000L) % 24000L * 24L / 24000L).toInt()
        val m = ((t + 6000L) % 24000L * 1440L / 24000L % 60L).toInt()
        timeStr = "%02d:%02d".format(h, m)
    }
    override fun getLines() = listOf("Day: $day ($timeStr)")
}

object WeatherModule : Module("Weather", Category.WORLD, enabled = false, x = 4f, y = 88f) {
    private var w = "Clear"
    fun update(raining: Boolean, thundering: Boolean) {
        w = when { thundering -> "Thunder"; raining -> "Rain"; else -> "Clear" }
    }
    override fun getLines() = listOf("Weather: $w")
}

object GameTimeModule : Module("Game Time", Category.WORLD, enabled = false, x = 4f, y = 100f) {
    private var time = 0L
    fun update(t: Long) { time = t }
    override fun getLines() = listOf("Game Time: $time")
}

object MoonPhaseModule : Module("Moon Phase", Category.WORLD, enabled = false, x = 4f, y = 112f) {
    private var phase = 0
    fun update(p: Int) { phase = p }
    override fun getLines() = listOf("Moon Phase: $phase")
}

object DifficultyModule : Module("Difficulty", Category.WORLD, enabled = false, x = 4f, y = 124f) {
    private var diff = "normal"
    fun update(d: String) { diff = d }
    override fun getLines() = listOf("Difficulty: $diff")
}

object SpawnDistanceModule : Module("Spawn Distance", Category.WORLD, enabled = false, x = 4f, y = 136f) {
    private var dist = 0
    fun update(d: Int) { dist = d }
    override fun getLines() = listOf("Spawn Dist: ${dist}m")
}

object WorldBorderModule : Module("World Border", Category.WORLD, enabled = false, x = 4f, y = 148f) {
    private var dist = 0.0
    fun update(d: Double) { dist = d }
    override fun getLines() = listOf("Border Dist: ${"%.1f".format(dist)}m")
}

object ServerBrandModule : Module("Server Brand", Category.WORLD, enabled = false, x = 4f, y = 160f) {
    private var brand = "vanilla"
    fun update(b: String) { brand = b }
    override fun getLines() = listOf("Brand: $brand")
}

package com.vibe.client.module.impl

import com.vibe.client.module.Category
import com.vibe.client.module.Module

object SlipperinessModule : Module("Slipperiness", Category.WORLD, enabled = false, x = 4f, y = 4f) {
    private var slip = 0.6f
    fun update(v: Float) { slip = v }
    override fun getLines() = listOf("Slip: ${"%.2f".format(slip)}")
}

object SolidGroundModule : Module("On Solid Ground", Category.WORLD, enabled = false, x = 4f, y = 16f) {
    private var ground = true
    fun update(v: Boolean) { ground = v }
    override fun getLines() = listOf("Ground: ${if (ground) "Yes" else "No"}")
}

object ClimbingModule : Module("Climbing", Category.WORLD, enabled = false, x = 4f, y = 28f) {
    private var climbing = false
    fun update(v: Boolean) { climbing = v }
    override fun getLines() = if (climbing) listOf("Climbing: Yes") else emptyList()
}

object InWaterModule : Module("In Water", Category.WORLD, enabled = false, x = 4f, y = 40f) {
    private var water = false
    fun update(v: Boolean) { water = v }
    override fun getLines() = if (water) listOf("In Water: Yes") else emptyList()
}

object PortalCooldownModule : Module("Portal Cooldown", Category.WORLD, enabled = false, x = 4f, y = 52f) {
    private var cd = 0
    fun update(v: Int) { cd = v }
    override fun getLines() = if (cd > 0) listOf("Portal CD: ${cd}t") else emptyList()
}

object CanSleepModule : Module("Can Sleep", Category.WORLD, enabled = false, x = 4f, y = 64f) {
    private var sleep = false
    fun update(v: Boolean) { sleep = v }
    override fun getLines() = listOf("Can Sleep: ${if (sleep) "Yes" else "No"}")
}

object SprintStateModule : Module("Sprinting", Category.WORLD, enabled = false, x = 4f, y = 76f) {
    private var sprinting = false; private var foodOk = true
    fun update(s: Boolean, f: Boolean) { sprinting = s; foodOk = f }
    override fun getLines() = listOf("Sprint State: ${if (sprinting) "Active" else "Idle"}")
}

object SneakStateModule : Module("Sneaking", Category.WORLD, enabled = false, x = 4f, y = 88f) {
    private var sneaking = false
    fun update(v: Boolean) { sneaking = v }
    override fun getLines() = if (sneaking) listOf("Sneaking: Yes") else emptyList()
}

object RegenModule : Module("Regen", Category.WORLD, enabled = false, x = 4f, y = 100f) {
    private var regen = false
    fun update(v: Boolean) { regen = v }
    override fun getLines() = listOf("Regen: ${if (regen) "Yes" else "No"}")
}

object BiomeTempModule : Module("Biome Temp", Category.WORLD, enabled = false, x = 4f, y = 112f) {
    private var temp = 0.5f
    fun update(v: Float) { temp = v }
    override fun getLines() = listOf("Temp: ${"%.2f".format(temp)}")
}

object VelocityModule : Module("Velocity", Category.MOVEMENT, enabled = false, x = 4f, y = 124f) {
    private var vx = 0.0; private var vy = 0.0; private var vz = 0.0
    fun update(x: Double, y: Double, z: Double) { vx = x; vy = y; vz = z }
    override fun getLines() = listOf("Vel: X${"%.1f".format(vx)} Y${"%.1f".format(vy)} Z${"%.1f".format(vz)}")
}

object FallDistanceModule : Module("Fall Distance", Category.MISC, enabled = false, x = 4f, y = 136f) {
    private var dist = 0f
    fun update(v: Float) { dist = v }
    override fun getLines() = if (dist > 0f) listOf("Fall: ${"%.1f".format(dist)}m") else emptyList()
}
